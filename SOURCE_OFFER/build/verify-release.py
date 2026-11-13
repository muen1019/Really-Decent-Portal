#!/usr/bin/env python3
"""Verify an RDP Android release pair and optionally publish its existing GitHub draft."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import tempfile
from urllib.request import Request, urlopen
from uuid import uuid4
import zipfile


PACKAGE = "io.github.muen1019.reallydecentportal"
SIGNER = "51cf6d51e0f5e97c9c482a5afab7e314bf7bfbf67347bc9857f284c732621216"
REPOSITORY = "muen1019/Really-Decent-Portal"
MAX_ARCHIVE = 128 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def run(*args, data=None):
    return subprocess.run(args, input=data, check=True, capture_output=True, timeout=90).stdout


def git(*args, data=None):
    return run("git", *args, data=data)


def git_files(commit, path):
    """Hash Git blobs directly so checkout filters and export-ignore do not affect comparison."""
    tree = git("ls-tree", "-r", "-z", commit, "--", path)
    entries = []
    for row in tree.rstrip(b"\0").split(b"\0"):
        metadata, name = row.split(b"\t", 1)
        mode, kind, oid = metadata.split()
        require(kind == b"blob" and mode in (b"100644", b"100755"), f"Invalid source mode: {name!r}")
        entries.append((name.decode("utf-8"), mode.decode("ascii"), oid))
    blobs = git("cat-file", "--batch", data=b"".join(oid + b"\n" for _, _, oid in entries))
    results = {}
    with io.BytesIO(blobs) as contents:
        for name, mode, oid in entries:
            actual_oid, kind, size = contents.readline().split()
            length = int(size)
            data = contents.read(length)
            require(actual_oid == oid and kind == b"blob" and len(data) == length
                    and contents.read(1) == b"\n", f"Incomplete Git blob: {name}")
            results[name] = (length, sha(data), mode)
        require(not contents.read(1), "Unexpected extra Git blob")
    return results


def verify_source(source_zip, manifest, commit):
    prefix = f"Really-Decent-Portal-{manifest['version']}/"
    checked = git_files(commit, "source/")
    with zipfile.ZipFile(source_zip) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate source ZIP entry")
        source_paths = {name.removeprefix(prefix) for name in names if name.startswith(prefix)}
        wanted = {name.removeprefix("source/") for name in checked}
        require(source_paths == wanted and len(wanted) == manifest["source_files"],
                "Source ZIP file set differs from the Git tag")
        manifest_path = "SOURCE_OFFER/formal-source-manifest.json"
        source_manifest = json.loads(archive.read(manifest_path))
        require(source_manifest["format"] == "really-decent-portal-source-manifest-v1"
                and source_manifest["package"] == PACKAGE
                and source_manifest["version"] == manifest["version"]
                and source_manifest["version_code"] == manifest["version_code"]
                and source_manifest["entry_count"] == len(wanted),
                "Source ZIP manifest identity differs from the release")
        entries = {row["path"]: (row["bytes"], row["sha256"], row["mode"])
                   for row in source_manifest["entries"]}
        require(len(source_manifest["entries"]) == len(entries) == len(wanted),
                "Source manifest has missing or duplicate entries")
        for name, facts in checked.items():
            relative = name.removeprefix("source/")
            info = archive.getinfo(prefix + relative)
            require(entries.get(relative) == facts and info.file_size == facts[0]
                    and sha(archive.read(info)) == facts[1]
                    and f"{info.external_attr >> 16:o}" == facts[2],
                    f"Source ZIP differs at {relative}")
        # Corresponding source ZIPs historically place root-level licenses under SOURCE_OFFER.
        # Compare every supplemental file to the tagged repository in either supported layout.
        supplements = git_files(commit, "SOURCE_OFFER/")
        root = git_files(commit, "LICENSES/")
        for name in ("CURRENT_RUNTIME_LICENSE_MAP.json", "LICENSE.md", "LICENSE.zh-Hant.md",
                     "THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.en.md"):
            root[name] = (0, sha(git("show", f"{commit}:{name}")), "100644")
        require({manifest_path, "SOURCE_OFFER/build/BUILD.md", "SOURCE_OFFER/build/rebuild-native.ps1",
                 "SOURCE_OFFER/LICENSE.md", "SOURCE_OFFER/THIRD_PARTY_NOTICES.md"} <= set(names),
                "Required source documentation or license is missing")
        for name in names:
            if name.startswith(prefix):
                continue
            require(name.startswith("SOURCE_OFFER/"), f"Unexpected source ZIP member: {name}")
            facts = supplements.get(name) or root.get(name.removeprefix("SOURCE_OFFER/"))
            digest = sha(archive.read(name))
            if name == "SOURCE_OFFER/SOURCE_LOCATION.zh-Hant.md":
                # The standalone ZIP places this notice next to SOURCE_LOCATION.
                standalone = git("show", f"{commit}:{name}").replace(
                    b"](../THIRD_PARTY_NOTICES.md)", b"](THIRD_PARTY_NOTICES.md)")
                require(digest in {facts[1], sha(standalone)}, f"Source ZIP supplement differs from the tag: {name}")
            else:
                require(facts is not None and digest == facts[1], f"Source ZIP supplement differs from the tag: {name}")


def verify_apk(apk, manifest, sdk):
    aapt2 = sdk / ("aapt2.exe" if os.name == "nt" else "aapt2")
    badging = run(aapt2, "dump", "badging", apk).decode("utf-8", errors="replace")
    package = re.search(r"(?m)^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    require(package is not None and package.groups() == (
        PACKAGE, str(manifest["version_code"]), manifest["version"]), "APK package/version mismatch")
    for label, value in (("minSdkVersion", manifest["min_sdk"]), ("targetSdkVersion", manifest["target_sdk"]),
                         ("native-code", "arm64-v8a")):
        require(re.search(rf"(?m)^{label}:\s*'{value}'\s*$", badging) is not None, f"Wrong APK {label}")

    for scheme, low, high in (("v3", manifest["min_sdk"], None), ("v2", 24, 27)):
        command = [sdk / "apksigner", "verify", "--verbose", "--print-certs", "--min-sdk-version", str(low)]
        if os.name == "nt":
            command[:1] = ["java", "-jar", sdk / "lib" / "apksigner.jar"]
        if high:
            command.extend(("--max-sdk-version", str(high)))
        output = run(*command, apk).decode("utf-8", errors="replace")
        certs = re.findall(r"certificate SHA-256 digest:\s*([a-f0-9]{64})", output, re.I)
        require([cert.lower() for cert in certs] == [SIGNER], "Unexpected APK signing certificate")
        require(re.search(rf"(?m)^Verified using {scheme} scheme[^\r\n]*:\s*true\s*$", output) is not None,
                f"APK {scheme} signature failed verification")


def verify_assets(directory, commit, version, tested_sha, sdk):
    raw_manifest = (directory / "RELEASE_ARTIFACTS.json").read_bytes()
    raw_sums = (directory / "SHA256SUMS.txt").read_bytes()
    require(raw_manifest == git("show", f"{commit}:RELEASE_ARTIFACTS.json")
            and raw_sums == git("show", f"{commit}:SHA256SUMS.txt"), "Release metadata differs from the tag")
    manifest = json.loads(raw_manifest)
    require(manifest["format"] == "really-decent-portal-release-artifacts-v1"
            and manifest["state"] == "VERIFIED_ARTIFACT_PAIR"
            and manifest["version"] == version and manifest["package"] == PACKAGE
            and manifest["abi"] == "arm64-v8a" and manifest["signing_certificate_sha256"] == SIGNER,
            "Release manifest identity mismatch")
    files = artifact_names(version)
    sums = []
    for key, name in zip(("apk", "source_archive"), files[:2]):
        row = manifest[key]
        file = directory / name
        require(file.stat().st_size <= MAX_ARCHIVE, f"Release attachment too large: {name}")
        data = file.read_bytes()
        digest = sha(data)
        require(row == {"file": name, "bytes": len(data), "sha256": digest}, f"Attachment mismatch: {name}")
        if key == "apk":
            require(digest == tested_sha, "APK SHA-256 differs from the tablet-tested APK")
        sums.append(f"{digest}  {name}")
    require(raw_sums.decode("ascii").splitlines() == sums, "Checksum attachment differs from the payload")
    verify_apk(directory / files[0], manifest, sdk)
    verify_source(directory / files[1], manifest, commit)


def artifact_names(version):
    return (f"Really-Decent-Portal-{version}-arm64-v8a.apk", f"Really-Decent-Portal-{version}-source.zip",
            "RELEASE_ARTIFACTS.json", "SHA256SUMS.txt")


def download(url, max_bytes=MAX_ARCHIVE):
    # Read public assets without forwarding an authentication header to a redirect host.
    query = "?rdp_verify=" + uuid4().hex
    request = Request(url + query, headers={"User-Agent": "rdp-release-check", "Cache-Control": "no-cache"})
    with urlopen(request, timeout=45) as response:
        result = response.read(max_bytes + 1)
    require(len(result) <= max_bytes, "Download exceeds its declared bound")
    return result


def archive_entries(data, archive_format):
    def content(name, content_bytes):
        _, separator, path = name.partition("/")
        require(separator and path, f"Unexpected archive path: {name}")
        return path, sha(content_bytes)

    if archive_format == "zip":
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            pairs = [content(member.filename, archive.read(member)) for member in archive.infolist()
                     if not member.is_dir()]
    else:
        mode = "r:gz" if archive_format == "tar.gz" else "r:"
        with tarfile.open(fileobj=io.BytesIO(data), mode=mode) as archive:
            pairs = []
            for member in archive:
                if member.isdir():
                    continue
                require(member.isfile(), f"Unexpected non-file in source archive: {member.name}")
                with archive.extractfile(member) as entry:
                    pairs.append(content(member.name, entry.read()))
    require(len(pairs) == len(dict(pairs)), "Duplicate archive path")
    return dict(pairs)


def verify_generated(tag, commit):
    local = archive_entries(git("archive", "--format=tar", "--prefix=source-archive/", commit), "tar")
    for archive_format in ("zip", "tar.gz"):
        url = f"https://github.com/{REPOSITORY}/archive/refs/tags/{tag}.{archive_format}"
        remote = archive_entries(download(url), archive_format)
        require(remote == local, f"GitHub's generated {archive_format} differs from the tagged files")
    print(f"GitHub generated ZIP and tar.gz match {len(local)} tagged files.")


def gh_api(endpoint, *args):
    return run(os.environ.get("RDP_GH", "gh"), "api", "--hostname", "github.com",
               "-H", "X-GitHub-Api-Version: 2022-11-28", endpoint, *args)


def release_assets(release, tag):
    require(release["tag_name"] == tag, "Release ID does not match the requested tag")
    expected = artifact_names(tag[1:])
    assets = {asset["name"]: (asset["id"], asset["size"], asset["digest"]) for asset in release["assets"]}
    require(len(assets) == len(release["assets"]) == len(expected) and set(assets) == set(expected),
            "Release must contain exactly the four matching attachments")
    for _, size, digest in assets.values():
        require(0 < size <= MAX_ARCHIVE and re.fullmatch(r"sha256:[a-f0-9]{64}", digest or ""),
                "Release attachment has invalid size or digest")
    return assets


def verify_remote_tag(tag, commit):
    # The tag may be either lightweight or annotated. Fetching it into the runner
    # verifies the source once, then the API verifies the remote ref before publication.
    endpoint = f"repos/{REPOSITORY}/git"
    remote = json.loads(gh_api(f"{endpoint}/ref/tags/{tag}"))["object"]
    if remote["type"] == "tag":
        remote = json.loads(gh_api(f"{endpoint}/tags/{remote['sha']}"))["object"]
    require(remote["type"] == "commit" and remote["sha"] == commit, "GitHub tag points to a different commit")


def verify_remote(args):
    require(args.release_id > 0, "Release ID must be positive")
    endpoint = f"repos/{REPOSITORY}/releases/{args.release_id}"
    remote = json.loads(gh_api(endpoint))
    require(not args.publish or remote["draft"], "Only an existing draft can be published")
    assets = release_assets(remote, args.tag)
    verify_remote_tag(args.tag, args.commit)
    with tempfile.TemporaryDirectory(prefix="rdp-release-") as scratch:
        directory = Path(scratch)
        for name, (asset_id, size, digest) in assets.items():
            if remote["draft"]:
                data = gh_api(f"repos/{REPOSITORY}/releases/assets/{asset_id}",
                              "-H", "Accept: application/octet-stream")
            else:
                data = download(f"https://github.com/{REPOSITORY}/releases/download/{args.tag}/{name}", size)
            require((len(data), "sha256:" + sha(data)) == (size, digest), f"Wrong download digest: {name}")
            (directory / name).write_bytes(data)
        verify_assets(directory, args.commit, args.tag[1:], args.tested_apk_sha256, args.build_tools)
        verify_generated(args.tag, args.commit)
        if args.publish:
            latest = json.loads(gh_api(endpoint))
            require(latest["draft"] and release_assets(latest, args.tag) == assets,
                    "Draft or its attachments changed during verification")
            verify_remote_tag(args.tag, args.commit)
            public = json.loads(gh_api(endpoint, "-X", "PATCH", "-F", "draft=false"))
            require(not public["draft"] and release_assets(public, args.tag) == assets,
                    "Unexpected release state after publication")
            for name, (_, size, digest) in assets.items():
                data = download(f"https://github.com/{REPOSITORY}/releases/download/{args.tag}/{name}", size)
                require((len(data), "sha256:" + sha(data)) == (size, digest),
                        f"Public download mismatch after publication: {name}")
            print(f"Published and verified {args.tag}.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--commit", required=True, help="full tested source commit")
    parser.add_argument("--tested-apk-sha256", required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--assets", type=Path, help="local directory with the four release files")
    source.add_argument("--release-id", type=int, help="existing GitHub Release database ID")
    parser.add_argument("--check-generated", action="store_true", help="also compare public source archives in local mode")
    parser.add_argument("--publish", action="store_true", help="publish the verified GitHub draft")
    args = parser.parse_args()
    require(re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", args.tag), "Use a release tag such as v0.1.8")
    require(re.fullmatch(r"[a-f0-9]{40}", args.commit), "Expected a full lowercase Git commit")
    require(re.fullmatch(r"[a-f0-9]{64}", args.tested_apk_sha256), "Expected a lowercase APK SHA-256")
    require(not args.publish or args.release_id, "Publishing requires an existing GitHub draft release ID")
    require(git("rev-parse", f"refs/tags/{args.tag}^{{commit}}").decode().strip() == args.commit,
            "Local release tag points to a different commit")
    if args.assets:
        verify_assets(args.assets, args.commit, args.tag[1:], args.tested_apk_sha256, args.build_tools)
        if args.check_generated:
            verify_generated(args.tag, args.commit)
    else:
        verify_remote(args)
    print(f"Verified {args.tag} at {args.commit} with tablet APK {args.tested_apk_sha256}.")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        print(f"Release verification failed: {Path(error.cmd[0]).name} exited with {error.returncode}.",
              file=sys.stderr)
        sys.exit(1)
    except (OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired, zipfile.BadZipFile,
            tarfile.TarError) as error:
        print(f"Release verification failed: {error}", file=sys.stderr)
        sys.exit(1)
