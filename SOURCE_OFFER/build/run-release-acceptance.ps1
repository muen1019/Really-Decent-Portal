# SPDX-License-Identifier: Apache-2.0
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$Artifacts,
    [Parameter(Mandatory)][string]$SavedProfile,
    [Parameter(Mandatory)][string]$ProbeHost,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$AndroidJar,
    [Parameter(Mandatory)][string]$D8Jar,
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [string]$ResumeFrom,
    [switch]$Publish
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$folder = (Resolve-Path -LiteralPath $Artifacts).Path
$manifestPath = Join-Path $folder 'RELEASE_ARTIFACTS.json'
$release = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($release.version -notmatch '^\d+\.\d+\.\d+$' -or
    $release.package -ne 'io.github.muen1019.reallydecentportal' -or
    $release.apk.file -ne "Really-Decent-Portal-$($release.version)-arm64-v8a.apk") {
    throw 'Unexpected release artifact manifest.'
}

$tag = 'v' + $release.version
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$taggedIcon = $null
try {
    $expectedIcon = Join-Path $repo 'assets/app-icon.png'
    if ($Publish) {
        $commit = (git -C $repo rev-parse "refs/tags/$tag^{commit}").Trim()
        $remote = 'muen1019/Really-Decent-Portal'
        $draft = gh release view $tag -R $remote --json databaseId,isDraft | ConvertFrom-Json
        if (-not $draft.isDraft) { throw 'Publication requires an existing draft for the tested tag.' }
        # Compare against the icon at the release tag, even if main has advanced.
        $taggedIcon = Join-Path ([IO.Path]::GetTempPath()) ('rdp-tag-icon-' + [guid]::NewGuid() + '.png')
        git -C $repo show "${commit}:assets/app-icon.png" > $taggedIcon
        $expectedIcon = $taggedIcon
    }
    & (Join-Path $PSScriptRoot 'test-tablet-upgrade.ps1') -Serial $Serial `
        -Apk (Join-Path $folder $release.apk.file) -ExpectedSha256 $release.apk.sha256 `
        -Version $release.version -VersionCode $release.version_code -SavedProfile $SavedProfile `
        -ExpectedIcon $expectedIcon -EvidenceDirectory $EvidenceDirectory -ResumeFrom $ResumeFrom `
        -ProbeHost $ProbeHost -JavaHome $JavaHome -AndroidJar $AndroidJar -D8Jar $D8Jar
    $proof = Get-Content -LiteralPath (Join-Path $EvidenceDirectory 'tablet-upgrade.json') -Raw | ConvertFrom-Json
    if ($proof.state -ne 'PASS' -or $proof.apk_sha256 -ne $release.apk.sha256 -or
        $proof.touchpad.click -ne $true -or $proof.touchpad.drag -ne $true -or
        $proof.touchpad.'scroll-vertical' -ne $true -or $proof.touchpad.'scroll-horizontal' -ne $true) {
        throw 'Release acceptance did not pass for this APK.'
    }
    if ($Publish) {
        # Only the checks and APK identity are needed by GitHub Actions.
        $acceptance = @{
            version = $proof.version
            apk_sha256 = $proof.apk_sha256
            upgrade = $proof.first_install_time_preserved -and $proof.app_id_preserved -and $proof.installed_apk_matches
            icon = $proof.launcher_icon_matches
            connection = $proof.saved_profile_preserved -and $proof.session_connected -and $proof.remote_frame_visible
            click = $proof.touchpad.click
            drag = $proof.touchpad.drag
            scroll_vertical = $proof.touchpad.'scroll-vertical'
            scroll_horizontal = $proof.touchpad.'scroll-horizontal'
        } | ConvertTo-Json -Compress
        gh workflow run release.yml -R $remote --ref main -f "tag=$tag" `
            -f "release_id=$($draft.databaseId)" -f "source_commit=$commit" `
            -f "tested_apk_sha256=$($proof.apk_sha256)" -f "acceptance=$acceptance" -F publish=true
    }
} finally {
    if ($taggedIcon -and (Test-Path -LiteralPath $taggedIcon)) { Remove-Item -LiteralPath $taggedIcon }
}
