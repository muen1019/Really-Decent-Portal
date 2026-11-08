# SPDX-License-Identifier: Apache-2.0
# Portable clean-source recipe; not executed by the source-offer preparation.
param(
 [Parameter(Mandatory=$true)][string]$SourceRoot,
 [Parameter(Mandatory=$true)][string]$AndroidSdk,
 [Parameter(Mandatory=$true)][string]$JavaHome,
 [Parameter(Mandatory=$true)][string]$MsysRoot,
 [Parameter(Mandatory=$true)][string]$MsysMake,
 [Parameter(Mandatory=$true)][string]$Python3,
 [Parameter(Mandatory=$true)][string]$ArchivesDirectory
)
$ErrorActionPreference='Stop'
$source=(Resolve-Path -LiteralPath $SourceRoot).Path
$sdk=(Resolve-Path -LiteralPath $AndroidSdk).Path
if($source -match '\s' -or $sdk -match '\s'){throw 'Use an ASCII source/SDK location without whitespace for compiler prefix-map flags.'}
$ndk=Join-Path $sdk 'ndk/29.0.13113456'
$cmake=Join-Path $sdk 'cmake/4.1.2/bin/cmake.exe'
$ninja=Join-Path $sdk 'cmake/4.1.2/bin/ninja.exe'
$project=Join-Path $source 'client/Android/Studio'
$core=Join-Path $project 'freeRDPCore'
$cpp=Join-Path $core 'src/main/cpp'
$build=Join-Path $core '.cxx/RelWithDebInfo/source23clean/arm64-v8a'
$work=Join-Path $source '.source-offer-rebuild'
if ((Test-Path -LiteralPath $build) -or (Test-Path -LiteralPath $work)) {throw 'Use a fresh extracted source copy. Existing build/output is never deleted.'}
$inputs=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'native-rebuild-inputs.json') -Raw | ConvertFrom-Json
foreach($item in $inputs.external_source_archives){
 $inputArchive=Join-Path $ArchivesDirectory $item.filename
 if(!(Test-Path -LiteralPath $inputArchive) -or (Get-FileHash -LiteralPath $inputArchive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha256){throw 'Provision and verify the documented source archives before this offline recipe.'}
}
foreach($item in @($cmake,$ninja,(Join-Path $ndk 'build/cmake/android.toolchain.cmake'),$MsysMake,$Python3,(Join-Path $MsysRoot 'usr/bin/bash.exe'))){if(!(Test-Path -LiteralPath $item)){throw 'A documented existing build tool is missing.'}}
function Invoke-Checked([string]$Exe,[string[]]$Arguments){ & $Exe @Arguments; if($LASTEXITCODE -ne 0){throw 'Build command failed; outputs retained for diagnosis.'} }
New-Item -ItemType Directory -Path $work | Out-Null
$env:JAVA_HOME=(Resolve-Path -LiteralPath $JavaHome).Path
$env:ANDROID_HOME=$sdk
$env:ANDROID_SDK_ROOT=$sdk
$env:PYTHON=$Python3
$env:PATH=(Split-Path $Python3 -Parent)+';'+(Join-Path $JavaHome 'bin')+';'+(Join-Path $MsysRoot 'mingw64/bin')+';'+$env:PATH
$flags=@()
foreach($pair in @(@($sdk,'/build/deskquay/android-sdk'),@($ndk,'/toolchain/ndk'),@($source,'/src/deskquay'))){
 $prefix=$pair[0].Replace('\','/');$forms=@($prefix)
 if($prefix -match '^([A-Za-z]):/(.*)$'){$forms+=('/'+$Matches[1].ToUpperInvariant()+'/'+$Matches[2]);$forms+=('/'+$Matches[1].ToLowerInvariant()+'/'+$Matches[2])}
 foreach($form in $forms){$flags+='-ffile-prefix-map='+$form+'='+$pair[1];$flags+='-fdebug-prefix-map='+$form+'='+$pair[1]}
}
$common=@('-S',$cpp,'-B',$build,'-G','Ninja',('-DCMAKE_MAKE_PROGRAM='+$ninja),
 ('-DCMAKE_TOOLCHAIN_FILE='+(Join-Path $ndk 'build/cmake/android.toolchain.cmake')),
 '-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-29','-DANDROID_STL=c++_shared',
 '-DCMAKE_BUILD_TYPE=RelWithDebInfo','-DCMAKE_INSTALL_LIBDIR=lib','-DCMAKE_EXPORT_COMPILE_COMMANDS=ON',
 ('-DPython3_EXECUTABLE='+$Python3),('-DFREERDP_MSYS_ROOT='+$MsysRoot),('-DFREERDP_MSYS_MAKE='+$MsysMake),
 ('-DPKG_CONFIG_EXECUTABLE='+(Join-Path $MsysRoot 'mingw64/bin/pkg-config.exe')),
 ('-DCMAKE_C_FLAGS:STRING='+($flags -join ' ')),('-DCMAKE_CXX_FLAGS:STRING='+($flags -join ' ')),
 '-DWITH_FFMPEG=OFF','-DWITH_OPENH264=OFF','-DWITH_OPUS=OFF','-DWITH_WEBP=OFF','-DWITH_JPEG=OFF','-DWITH_PNG=OFF',
 '-DWITH_CJSON=ON','-DWITH_OPENSSL=ON')
Invoke-Checked $cmake $common
# ExternalProject verifies these exact existing bytes without downloading them.
foreach($item in $inputs.external_source_archives){
 $directory=Join-Path $build ($item.component+'-prefix/src')
 New-Item -ItemType Directory -Force -Path $directory | Out-Null
 Copy-Item -LiteralPath (Join-Path $ArchivesDirectory $item.filename) -Destination (Join-Path $directory $item.filename)
}
Invoke-Checked $cmake @('--build',$build,'--parallel','2')
$nativeRoot=Join-Path $work 'native'
$abiRoot=Join-Path $nativeRoot 'arm64-v8a'
New-Item -ItemType Directory -Path $abiRoot | Out-Null
$deps=Join-Path $core 'src/main/jniLibs/arm64-v8a/lib'
foreach($name in $inputs.native_member_names){
 $from=if($name -eq 'libfreerdp-android.so'){Join-Path $build $name}elseif($name -eq 'legacy.so'){Join-Path $deps 'ossl-modules/legacy.so'}else{Join-Path $deps $name}
 if(!(Test-Path -LiteralPath $from)){throw 'A clean native output is missing; do not substitute an older binary.'}
 Copy-Item -LiteralPath $from -Destination (Join-Path $abiRoot $name)
}
# This init packages only the newly built nine native members; SQLCipher stays
# in its declared 4.17.0 Maven AAR. It contains no key/signing configuration.
Invoke-Checked (Join-Path $project 'gradlew.bat') @('-p',$project,'--offline','--no-daemon','--max-workers=1',
 '--console=plain','-PdeskquayAbi=arm64-v8a',('-PrdpRebuiltNativeRoot='+$nativeRoot),
 '-I',(Join-Path $PSScriptRoot 'clean-package.gradle'),':aFreeRDP:assembleDeskquayRelease')
Write-Output 'Unsigned candidate produced. No signing/install/publication; do not claim byte-identical reproduction.'
