#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# DeskQuay distribution: portable in-tree host build helper, 2026-10-29.
"""Run the upstream Android OpenSSL build in an existing, isolated MSYS2 shell.

This adapter does not install software, alter global environment variables, change
algorithms or verification switches, or contact a server. The explicit public
default lookup directories require provider/certificate regression. ExternalProject verifies sources.
The caller supplies an existing MSYS2 root and a verified MSYS (not MinGW) make.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import importlib.util


def existing(path: str, *, directory: bool = False) -> Path:
    value = Path(path).resolve(strict=True)
    if directory != value.is_dir():
        raise ValueError(f"Expected {'directory' if directory else 'file'}: {value}")
    return value


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", required=True, choices=("configure", "build", "install"))
    parser.add_argument("--source", required=True)
    parser.add_argument("--root", required=True)
    parser.add_argument("--build-root", required=True)
    parser.add_argument("--abi", required=True)
    parser.add_argument("--binary", required=True)
    parser.add_argument("--ndk", required=True)
    parser.add_argument("--msys-root", required=True)
    parser.add_argument("--make", required=True)
    parser.add_argument("--arch", required=True, choices=(
        "android-arm64", "android-arm", "android-x86_64", "android-x86", "android-riscv64"))
    parser.add_argument("--api", required=True, type=int)
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--libdir", default="lib")
    parser.add_argument("--jobs", type=int, default=2)
    args = parser.parse_args()
    if os.name != "nt":
        parser.error("This host adapter is only for Windows; use upstream commands elsewhere.")
    if not 1 <= args.jobs <= 64 or not 21 <= args.api <= 99:
        parser.error("Expected 1..64 jobs and Android API 21..99.")
    relative_libdir = Path(args.libdir)
    if relative_libdir.drive or relative_libdir.root or ".." in relative_libdir.parts:
        parser.error("--libdir must be a relative install directory without '..'.")

    source = existing(args.source, directory=True)
    ndk = existing(args.ndk, directory=True)
    msys = existing(args.msys_root, directory=True)
    make = existing(args.make)
    bash = existing(str(msys / "usr/bin/bash.exe"))
    cygpath = existing(str(msys / "usr/bin/cygpath.exe"))
    existing(str(msys / "usr/bin/perl.exe"))
    ndk_bin = existing(str(ndk / "toolchains/llvm/prebuilt/windows-x86_64/bin"), directory=True)
    existing(str(ndk_bin / "clang.exe"))
    existing(str(source / "Configure"))
    metadata_path = Path(__file__).with_name("public_native_metadata.py")
    spec = importlib.util.spec_from_file_location("deskquay_public_native_metadata", metadata_path)
    metadata = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(metadata)
    if metadata.ordinary(args.root) != Path(__file__).resolve().parents[3] or args.libdir != "lib":
        raise ValueError("Exact own-source public native metadata layout required")
    ctx = metadata.context(args.root, args.build_root, args.binary, args.prefix, args.abi)
    binary, prefix = ctx["binary"], ctx["prefix"]

    env = os.environ.copy()
    # Clang is a native Windows process; MSYS must translate its POSIX paths.
    env.pop("MSYS_NO_PATHCONV", None)
    # Target literals/maps are not host file paths. Keep ordinary source/include
    # conversion; never use a global '*' exclusion for the native NDK compiler.
    env["MSYS2_ARG_CONV_EXCL"] = "-DOPENSSLDIR=;-DMODULESDIR=;-ffile-prefix-map=;-fdebug-prefix-map=;-fmacro-prefix-map="
    env["MSYS2_ENV_CONV_EXCL"] = "CFLAGS;CXXFLAGS;CPPFLAGS"
    # The runtime DLL and POSIX mount table come from this existing MSYS2 root.
    env["PATH"] = str(msys / "usr/bin") + os.pathsep + env.get("PATH", "")
    env.update(LC_ALL="C", LANG="C", MSYS2_PATH_TYPE="minimal", CHERE_INVOKING="1")

    def posix(path: Path) -> str:
        return subprocess.check_output(
            [str(cygpath), "-a", "-u", str(path)], env=env, text=True).strip()

    # Use only this invocation's tools. No login/profile scripts or global PATH edits.
    shell_path = ":".join((posix(ndk_bin), posix(make.parent), "/usr/bin", "/bin"))
    ndk_posix = posix(ndk)
    exports = {
        "PATH": shell_path,
        "ANDROID_NDK": ndk_posix,
        "ANDROID_NDK_ROOT": ndk_posix,
        "ANDROID_NDK_HOME": ndk_posix,
        "CC": "clang",
        "LC_ALL": "C",
        "LANG": "C",
    }
    def run(command):
        # No install command is built here: the shared recipe supplies exact owned DESTDIR.
        script = "; ".join([
            "set -eu",
            *(f"export {key}={shlex.quote(value)}" for key, value in exports.items()),
            "unset CROSS_COMPILE CROSS_SYSROOT",
            f"cd {shlex.quote(posix(binary))}",
            "exec " + shlex.join(command),
        ])
        result = subprocess.run([str(bash), "--noprofile", "--norc", "-c", script], env=env)
        if result.returncode:
            raise ValueError("Public OpenSSL stage failed")
    metadata.openssl_stage(ctx, source, args.stage, args.arch, args.api, args.jobs,
                           run, convert=posix, make=posix(make), windows_response=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"OpenSSL Windows host adapter: {error}", file=sys.stderr)
        sys.exit(2)
