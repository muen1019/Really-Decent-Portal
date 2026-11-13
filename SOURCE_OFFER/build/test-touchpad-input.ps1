# SPDX-License-Identifier: Apache-2.0
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][int]$AppId,
    [Parameter(Mandatory)][string]$ProbeHost,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$AndroidJar,
    [Parameter(Mandatory)][string]$D8Jar
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$package = 'io.github.muen1019.reallydecentportal'
if ($ProbeHost -notmatch '^[A-Za-z0-9][A-Za-z0-9._@-]*$') { throw 'Use an SSH host or alias.' }

function Adb {
    & adb.exe -s $Serial @args
}

function Invoke-Remote([string]$Script, [string]$Payload = '') {
    $entry = '$ProgressPreference="SilentlyContinue"; ' + $Script
    $encodedEntry = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($entry))
    $start = [Diagnostics.ProcessStartInfo]::new('ssh.exe')
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in '-T','-o','BatchMode=yes','-o','ConnectTimeout=5',$ProbeHost,'powershell.exe','-NoProfile','-NonInteractive','-EncodedCommand',$encodedEntry) {
        $start.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::Start($start)
    try {
        $output = $process.StandardOutput.ReadToEndAsync()
        $errors = $process.StandardError.ReadToEndAsync()
        if ($Payload) { $process.StandardInput.WriteLine($Payload) }
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(12000)) {
            $process.Kill()
            throw 'The remote Windows probe command timed out.'
        }
        if ($process.ExitCode -ne 0) { throw "Remote Windows probe command failed: $($errors.Result)" }
        $output.Result.Trim()
    } finally { $process.Dispose() }
}

function Get-SessionUi {
    $focus = (Adb shell dumpsys window) -join "`n"
    if ($focus -notmatch ('(?m)^\s*mCurrentFocus=.*u0 ' + [regex]::Escape($package) + '/[^\s}]*SessionActivity')) {
        throw 'The RDP session is not in the foreground.'
    }
    $dump = (Adb exec-out uiautomator dump /dev/tty) -join "`n"
    [xml]([regex]::Match($dump, '(?s)<\?xml.*?</hierarchy>').Value)
}

function Get-Control($Ui, [string]$Id) {
    $Ui.SelectSingleNode("//node[@resource-id='${package}:id/$Id']")
}

function Get-Geometry($Node) {
    if (-not $Node) { throw 'Missing Android session control.' }
    $m = [regex]::Match($Node.GetAttribute('bounds'), '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $m.Success) { throw 'Invalid Android session bounds.' }
    1..4 | ForEach-Object { [int]$m.Groups[$_].Value }
}

function Tap-Control($Node) {
    $l, $t, $r, $b = Get-Geometry $Node
    Adb shell input tap ([int](($l+$r)/2)) ([int](($t+$b)/2)) | Out-Null
}

$ui = Get-SessionUi
$mode = Get-Control $ui 'floating_toolbar_touch_pointer'
if (-not $mode) {
    Tap-Control (Get-Control $ui 'floating_toolbar_handle')
    $ui = Get-SessionUi
    $mode = Get-Control $ui 'floating_toolbar_touch_pointer'
}
if (-not $mode) { throw 'Could not inspect the touchscreen mode.' }
$touchpad = 'Screen touchpad:|螢幕觸控板：'
$switched = $mode.GetAttribute('content-desc') -notmatch $touchpad
if ($switched) {
    Tap-Control $mode
    $ui = Get-SessionUi
    $mode = Get-Control $ui 'floating_toolbar_touch_pointer'
}
if (-not $mode -or $mode.GetAttribute('content-desc') -notmatch $touchpad) {
    throw 'Could not verify that screen touchpad mode is active.'
}
$left, $top, $right, $bottom = Get-Geometry (Get-Control $ui 'sessionView')
if ($right-$left -lt 500 -or $bottom-$top -lt 500) { throw 'Session viewport is too small for the test gestures.' }
$x = [int](($left+$right)/2)
$y = [int](($top+$bottom)/2)

$run = 'rdp-input-' + [guid]::NewGuid().ToString('N')
$stage = Join-Path ([IO.Path]::GetTempPath()) $run
$deviceJar = "/data/local/tmp/$run.jar"
$remoteDir = "(Join-Path ([IO.Path]::GetTempPath()) '$run')"
$body = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'RemoteTouchpadProbe.cs')))
$remoteStarted = $false
$deviceStaged = $false
try {
    New-Item -ItemType Directory -Path $stage | Out-Null
    $jar = Join-Path $stage 'gesture.jar'
    & (Join-Path $JavaHome 'bin/javac.exe') --release 17 -classpath $AndroidJar -d $stage (Join-Path $PSScriptRoot 'TouchpadGesture.java')
    & (Join-Path $JavaHome 'bin/java.exe') -cp $D8Jar com.android.tools.r8.D8 --lib $AndroidJar --min-api 29 --output $jar (Join-Path $stage 'TouchpadGesture.class')
    Adb push $jar $deviceJar | Out-Null
    $deviceStaged = $true
    Adb shell chmod 444 $deviceJar

    # SSH runs outside the desktop session. An interactive scheduled task
    # runs the receiver as the signed-in Windows user, without credentials.
    $remoteStarted = $true
    $launch = @'
$ErrorActionPreference = 'Stop'
$dir = RUN_DIRECTORY
[void][IO.Directory]::CreateDirectory($dir)
$probe = Join-Path $dir 'probe.exe'
$source = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String([Console]::ReadLine()))
Add-Type -TypeDefinition $source -ReferencedAssemblies System.Windows.Forms,System.Drawing -OutputAssembly $probe -OutputType WindowsApplication
$user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$action = New-ScheduledTaskAction -Execute $probe -Argument ('"'+$dir+'"')
$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive
Register-ScheduledTask -TaskName 'RUN_NAME' -Action $action -Principal $principal | Out-Null
Start-ScheduledTask -TaskName 'RUN_NAME'
'@
    Invoke-Remote ($launch.Replace('RUN_DIRECTORY', $remoteDir).Replace('RUN_NAME', $run)) $body | Out-Null

    function Read-Events {
        $read = @'
$path = Join-Path RUN_DIRECTORY 'events.json'
for ($i=0; $i -lt 8; $i++) {
    try { [IO.File]::ReadAllText($path); break }
    catch [IO.IOException] { Start-Sleep -Milliseconds 40 }
}
'@
        $json = Invoke-Remote ($read.Replace('RUN_DIRECTORY', $remoteDir))
        if ($json) { $json | ConvertFrom-Json }
    }

    $deadline = (Get-Date).AddSeconds(18)
    do {
        $events = Read-Events
        if ($events.run -eq $run) { break }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    if ($events.run -ne $run -or $events.left_down -ne 0) {
        throw 'The remote test window did not start with fresh counters.'
    }
    Add-Type -AssemblyName System.Drawing
    $framePath = Join-Path $stage 'probe-frame.png'
    $deadline = (Get-Date).AddSeconds(8)
    do {
        & adb.exe -s $Serial exec-out screencap -p > $framePath
        $frame = [Drawing.Bitmap]::FromFile($framePath)
        try {
            $matchingPixels = 0
            foreach ($offset in -140,-70,70,140) {
                $pixel = $frame.GetPixel($x+$offset, $y)
                if ([Math]::Abs($pixel.R-18)+[Math]::Abs($pixel.G-63)+[Math]::Abs($pixel.B-74) -le 12) { $matchingPixels++ }
            }
            $probeVisible = $matchingPixels -eq 4
        } finally { $frame.Dispose() }
        if ($probeVisible) { break }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    if (-not $probeVisible) {
        throw "The remote probe is not visible in the Android session (sample RGB $($pixel.R),$($pixel.G),$($pixel.B), remote window $($events.window_width)x$($events.window_height))."
    }

    Adb shell "CLASSPATH=$deviceJar app_process /system/bin TouchpadGesture move $AppId $x $y" | Out-Null
    $checks = [ordered]@{}
    foreach ($gesture in 'click','drag','scroll-vertical','scroll-horizontal') {
        Get-SessionUi | Out-Null
        Adb shell "CLASSPATH=$deviceJar app_process /system/bin TouchpadGesture $gesture $AppId $x $y" | Out-Null
        Start-Sleep -Milliseconds 650
        $after = Read-Events
        if ($after.run -ne $run) { throw 'The remote probe stopped reporting.' }
        $checks[$gesture] = switch ($gesture) {
            'click' { $after.click -eq $events.click+1 -and $after.left_down -eq $events.left_down+1 -and $after.left_up -eq $events.left_up+1 }
            'drag' { $after.drag_released -eq $events.drag_released+1 -and $after.left_down -eq $events.left_down+1 -and $after.left_up -eq $events.left_up+1 -and $after.click -eq $events.click }
            'scroll-vertical' { $after.vertical -gt $events.vertical -and $after.horizontal -eq $events.horizontal -and $after.left_down -eq $events.left_down }
            'scroll-horizontal' { $after.horizontal -gt $events.horizontal -and $after.vertical -eq $events.vertical -and $after.left_down -eq $events.left_down }
        }
        if (-not $checks[$gesture]) {
            throw "Remote Windows did not receive the expected $gesture events. Before: $($events | ConvertTo-Json -Compress). After: $($after | ConvertTo-Json -Compress)."
        }
        $events = $after
    }
    [pscustomobject]$checks
} finally {
    if ($remoteStarted) {
        $cleanup = @'
$dir = RUN_DIRECTORY
if (Test-Path -LiteralPath $dir) { [IO.File]::WriteAllText((Join-Path $dir 'stop'),'') }
Unregister-ScheduledTask -TaskName 'RUN_NAME' -Confirm:$false -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 400
foreach ($file in 'probe.exe','events.json','events.next','stop') {
    Remove-Item -LiteralPath (Join-Path $dir $file) -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $dir -ErrorAction SilentlyContinue
'@
        try {
            Invoke-Remote ($cleanup.Replace('RUN_DIRECTORY', $remoteDir).Replace('RUN_NAME', $run)) | Out-Null
        } catch {
            Write-Warning "Remote probe cleanup could not be verified: $_"
        }
    }
    if ($deviceStaged) { Adb shell rm -f $deviceJar | Out-Null }
    foreach ($file in 'gesture.jar','TouchpadGesture.class','probe-frame.png') {
        Remove-Item -LiteralPath (Join-Path $stage $file) -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage }
    if ($switched) {
        $mode = Get-Control (Get-SessionUi) 'floating_toolbar_touch_pointer'
        if ($mode -and $mode.GetAttribute('content-desc') -match $touchpad) { Tap-Control $mode }
    }
}
