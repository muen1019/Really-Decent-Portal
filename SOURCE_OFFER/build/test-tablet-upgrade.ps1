# SPDX-License-Identifier: Apache-2.0

<#
.SYNOPSIS
Tests an in-place Android upgrade, the launcher icon and a saved RDP connection.
.DESCRIPTION
Use PowerShell 7.4+ on Windows with adb on PATH and an unlocked Samsung tablet.
Pin the app on the home screen or taskbar. The saved profile must be visible
in the app's bookmark list and have working sign-in details.
Use the APK checksum from RELEASE_ARTIFACTS.json and the README icon from the
same release. The script writes a cropped launcher icon and tablet-upgrade.json
to a new evidence directory. It leaves a successful RDP session open.
.PARAMETER ResumeFrom
An earlier tablet-upgrade.json that recorded a verified installation.
Rechecks the same installation and reruns the UI checks without reinstalling.
.EXAMPLE
$meta = Get-Content ./RELEASE_ARTIFACTS.json -Raw | ConvertFrom-Json
./test_tablet_upgrade.ps1 -Serial 'ADB_SERIAL' -Apk $meta.apk.file `
    -ExpectedSha256 $meta.apk.sha256 -Version $meta.version -VersionCode $meta.version_code `
    -SavedProfile 'My PC' -ExpectedIcon ./app-icon.png -EvidenceDirectory ./tablet-result
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][string]$ExpectedSha256,
    [Parameter(Mandatory)][string]$Version,
    [Parameter(Mandatory)][int]$VersionCode,
    [Parameter(Mandatory)][string]$SavedProfile,
    [Parameter(Mandatory)][string]$ExpectedIcon,
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [string]$ResumeFrom,
    [string]$ProbeHost,
    [string]$JavaHome,
    [string]$AndroidJar,
    [string]$D8Jar
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$app = 'io.github.muen1019.reallydecentportal'
$label = 'Really Decent Portal'
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$iconPath = (Resolve-Path -LiteralPath $ExpectedIcon).Path
$apkSha = (Get-FileHash -LiteralPath $apkPath).Hash.ToLowerInvariant()
if ($apkSha -ne $ExpectedSha256) { throw 'The selected APK does not match the expected SHA-256.' }
if ($ProbeHost -and (-not $JavaHome -or -not $AndroidJar -or -not $D8Jar)) {
    throw 'The touchpad input test requires JavaHome, AndroidJar and D8Jar.'
}

function Invoke-Device {
    & adb -s $Serial @args
}

function Get-Match([string]$Text, [string]$Pattern) {
    $match = [regex]::Match($Text, $Pattern)
    if (-not $match.Success) { throw "Missing Android diagnostic field: $Pattern" }
    $match.Groups[1].Value
}

function Get-Installation {
    $dump = (Invoke-Device shell dumpsys package $app) -join "`n"
    @{
        Version = Get-Match $dump '(?m)^\s*versionName=(\S+)'
        Code = [int](Get-Match $dump '(?m)^\s*versionCode=(\d+)')
        AppId = Get-Match $dump '(?m)^\s*(?:appId|userId)=(\d+)'
        FirstInstalled = Get-Match $dump '(?m)^\s*firstInstallTime=([^\r\n]+)'
    }
}

function Get-Ui {
    # Keep the hierarchy in memory. It can contain private profile names.
    $dump = (Invoke-Device exec-out uiautomator dump /dev/tty) -join "`n"
    $start = $dump.IndexOf('<?xml')
    $end = $dump.LastIndexOf('</hierarchy>')
    if ($start -lt 0 -or $end -lt $start) { throw 'Android did not return a UI hierarchy.' }
    [xml]$dump.Substring($start, $end + '</hierarchy>'.Length - $start)
}

function Get-NodeBounds($Node) {
    $bounds = [regex]::Match($Node.GetAttribute('bounds'), '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $bounds.Success) { throw 'Invalid UI bounds.' }
    $left, $top, $right, $bottom = 1..4 | ForEach-Object { [int]$bounds.Groups[$_].Value }
    if ($right -le $left -or $bottom -le $top) { throw 'Empty UI bounds.' }
    [Drawing.Rectangle]::FromLTRB($left, $top, $right, $bottom)
}

function Invoke-Tap($Bounds) {
    $x = [int]($Bounds.Left + $Bounds.Width / 2)
    $y = [int]($Bounds.Top + $Bounds.Height / 2)
    Invoke-Device shell input tap $x $y | Out-Null
}

function Get-SavedProfile {
    $ui = Get-Ui
    $rows = @($ui.SelectNodes('//node') | Where-Object {
        $_.GetAttribute('resource-id') -eq "${app}:id/bookmark_text1" -and
        $_.GetAttribute('text') -ceq $SavedProfile
    })
    if ($rows.Count -ne 1) { throw 'Expected exactly one visible card for the requested saved profile.' }
    Get-NodeBounds $rows[0]
}

function Get-LauncherIcon {
    $ui = Get-Ui
    $icons = @($ui.SelectNodes('//node') | Where-Object {
        $_.GetAttribute('resource-id') -eq 'com.sec.android.app.launcher:id/icon' -and
        ($_.GetAttribute('text') -ceq $label -or $_.GetAttribute('content-desc') -ceq $label)
    })
    if ($icons.Count -eq 0) { throw 'Really Decent Portal was not visible in Samsung One UI Home.' }
    Get-NodeBounds $icons[0]
}

function Test-LauncherImage($Crop, $Reference) {
    # Locate the dark teal tile, then compare foreground and background regions
    # to the approved icon. Sample interior points to tolerate resampling.
    $left = $Crop.Width
    $right = -1
    $top = $Crop.Height
    $bottom = -1
    for ($y = 0; $y -lt $Crop.Height; $y++) {
        for ($x = 0; $x -lt $Crop.Width; $x++) {
            $c = $Crop.GetPixel($x, $y)
            if ($c.R -lt 125 -and $c.G -gt $c.R + 18 -and $c.B -gt $c.R + 25 -and $c.B -lt 160) {
                $left = [Math]::Min($left, $x)
                $right = [Math]::Max($right, $x)
                $top = [Math]::Min($top, $y)
                $bottom = [Math]::Max($bottom, $y)
            }
        }
    }
    $width = $right - $left + 1
    $height = $bottom - $top + 1
    if ($width -lt 24 -or $height -lt 24 -or [Math]::Abs($width - $height) -gt 0.12 * $width) {
        return $false
    }
    $colors = @(
        @{ R = 244; G = 248; B = 249 }
        @{ R = 82; G = 182; B = 173 }
        @{ R = 238; G = 184; B = 95 }
    )
    foreach ($color in $colors) {
        $samples = $matches = 0
        for ($y = 10; $y -lt $Reference.Height - 10; $y += 3) {
            for ($x = 10; $x -lt $Reference.Width - 10; $x += 3) {
                $ref = $Reference.GetPixel($x, $y)
                if ($ref.A -ne 255 -or $ref.R -ne $color.R -or $ref.G -ne $color.G -or $ref.B -ne $color.B) { continue }
                $targetX = $left + [int][Math]::Floor(($x + 0.5) * $width / $Reference.Width)
                $targetY = $top + [int][Math]::Floor(($y + 0.5) * $height / $Reference.Height)
                $actual = $Crop.GetPixel($targetX, $targetY)
                $samples++
                $difference = [Math]::Abs($actual.R - $ref.R) + [Math]::Abs($actual.G - $ref.G) + [Math]::Abs($actual.B - $ref.B)
                if ($difference -lt 95) { $matches++ }
            }
        }
        if ($samples -lt 5 -or $matches / $samples -lt 0.55) { return $false }
    }
    return $true
}

function Get-Screenshot([string]$Path) {
    # PowerShell 7.4+ preserves native binary stdout when redirected to a file.
    & adb -s $Serial exec-out screencap -p > $Path
    [Drawing.Bitmap]::FromFile($Path)
}

if ($PSVersionTable.PSVersion -lt [version]'7.4') { throw 'PowerShell 7.4 or newer is required.' }
Add-Type -AssemblyName System.Drawing
$model = (Invoke-Device shell getprop ro.product.model).Trim()
$before = Get-Installation
$previous = $null
if ($ResumeFrom) {
    $previous = Get-Content -Raw -LiteralPath $ResumeFrom | ConvertFrom-Json
    if ($previous.model -ne $model -or $previous.apk_sha256 -ne $apkSha -or
        $previous.version_code -ne $VersionCode -or $before.Code -ne $VersionCode -or
        $before.Version -ne $Version -or -not $previous.first_install_time_preserved -or
        -not $previous.app_id_preserved -or -not $previous.installed_apk_matches -or
        $previous.first_install_time -ne $before.FirstInstalled -or $previous.app_id -ne $before.AppId) {
        throw 'Earlier evidence does not match this upgraded installation.'
    }
    Invoke-Device shell am force-stop $app | Out-Null
} elseif ($before.Code -ge $VersionCode) {
    throw 'Expected an older installed version for the in-place upgrade.'
}
Invoke-Device shell input keyevent KEYCODE_HOME | Out-Null
Get-LauncherIcon | Out-Null
Invoke-Device shell am start -W -n "$app/com.freerdp.freerdpcore.presentation.HomeActivity" | Out-Null
Get-SavedProfile | Out-Null

New-Item -ItemType Directory -Path $EvidenceDirectory | Out-Null
$evidence = (Resolve-Path -LiteralPath $EvidenceDirectory).Path
$scratch = Join-Path ([IO.Path]::GetTempPath()) ("rdp-tablet-" + [guid]::NewGuid() + ".png")
$result = [ordered]@{ state = 'FAIL'; model = $model; package = $app; apk_sha256 = $apkSha; previous_version = $before.Version }
try {
    if ($previous) {
        $result.previous_version = $previous.previous_version
    } else {
        $install = Invoke-Device install -r $apkPath
        if ($install -notcontains 'Success') { throw 'Android did not report an in-place install.' }
    }
    $after = Get-Installation
    if ($after.Version -ne $Version -or $after.Code -ne $VersionCode) { throw 'Installed version mismatch.' }
    $result.version = $after.Version
    $result.version_code = $after.Code
    $result.first_install_time = $after.FirstInstalled
    $result.app_id = $after.AppId
    $result.first_install_time_preserved = $after.FirstInstalled -eq $before.FirstInstalled
    $result.app_id_preserved = $after.AppId -eq $before.AppId
    if (-not ($result.first_install_time_preserved -and $result.app_id_preserved)) { throw 'Installation identity changed.' }
    $deviceApk = Get-Match ((Invoke-Device shell pm path $app) -join "`n") '(?m)^package:(\S+/base\.apk)'
    $deviceHash = Get-Match ((Invoke-Device shell sha256sum $deviceApk) -join "`n") '^([a-f0-9]{64})'
    $result.installed_apk_matches = $deviceHash -eq $apkSha
    if (-not $result.installed_apk_matches) { throw 'The installed APK differs from the selected artifact.' }

    Invoke-Device shell input keyevent KEYCODE_HOME | Out-Null
    $bounds = Get-LauncherIcon
    $reference = [Drawing.Bitmap]::FromFile($iconPath)
    $screen = Get-Screenshot $scratch
    try {
        $crop = $screen.Clone($bounds, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $crop.Save((Join-Path $evidence 'launcher-icon.png'), [Drawing.Imaging.ImageFormat]::Png)
            $result.launcher_icon_matches = Test-LauncherImage $crop $reference
        } finally { $crop.Dispose() }
    } finally {
        $screen.Dispose()
        $reference.Dispose()
    }
    if (-not $result.launcher_icon_matches) { throw 'Launcher screenshot does not match the expected icon.' }

    Invoke-Tap $bounds
    $bookmark = Get-SavedProfile
    $result.saved_profile_preserved = $true
    $processId = (Invoke-Device shell pidof -s $app).Trim()
    $since = (Invoke-Device shell date '+%m-%d_%H:%M:%S.%3N').Trim().Replace('_',' ')
    Invoke-Tap $bookmark
    $deadline = (Get-Date).AddSeconds(45)
    do {
        $lines = (Invoke-Device logcat -d --pid=$processId -T $since) -join "`n"
        $result.session_connected = $lines -match 'FreeRDP\.SessionActivity\s*:\s*onSessionConnected'
        $result.remote_framebuffer_initialized = $lines -match 'Remote framebuffer format\s+PIXEL_FORMAT_'
        if ($result.session_connected -and $result.remote_framebuffer_initialized) { break }
        Start-Sleep -Milliseconds 750
    } while ((Get-Date) -lt $deadline)
    if (-not ($result.session_connected -and $result.remote_framebuffer_initialized)) { throw 'No fresh connection and remote framebuffer events.' }

    $ui = Get-Ui
    $view = @($ui.SelectNodes('//node') | Where-Object { $_.GetAttribute('resource-id') -eq "${app}:id/sessionView" })
    if ($view.Count -ne 1) { throw 'The connected session view was not visible.' }
    $frame = Get-NodeBounds $view[0]
    # PostConnect can arrive before the first graphics update reaches the view.
    $deadline = (Get-Date).AddSeconds(20)
    do {
        $screen = Get-Screenshot $scratch
        try {
            $visibleColors = [Collections.Generic.HashSet[int]]::new()
            $painted = 0
            foreach ($y in 1..19) {
                foreach ($x in 1..29) {
                    $pixel = $screen.GetPixel([int]($frame.Left + $x * $frame.Width / 30), [int]($frame.Top + $y * $frame.Height / 20))
                    if ([Math]::Max($pixel.R, [Math]::Max($pixel.G, $pixel.B)) -gt 24) { $painted++ }
                    [void]$visibleColors.Add($pixel.ToArgb())
                }
            }
            $result.remote_frame_visible = $painted -ge 100 -and $visibleColors.Count -ge 8
        } finally { $screen.Dispose() }
        if ($result.remote_frame_visible) { break }
        Start-Sleep -Milliseconds 750
    } while ((Get-Date) -lt $deadline)
    if (-not $result.remote_frame_visible) { throw 'The remote session view did not display a discernible frame.' }
    if ($ProbeHost) {
        $result.touchpad = & (Join-Path $PSScriptRoot 'test-touchpad-input.ps1') `
            -Serial $Serial -AppId $after.AppId -ProbeHost $ProbeHost -JavaHome $JavaHome `
            -AndroidJar $AndroidJar -D8Jar $D8Jar
    }
    $result.state = 'PASS'
} finally {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch }
    $result | ConvertTo-Json | Set-Content -Encoding utf8NoBOM -LiteralPath (Join-Path $evidence 'tablet-upgrade.json')
}
$result | ConvertTo-Json
