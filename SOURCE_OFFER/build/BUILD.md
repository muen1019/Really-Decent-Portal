# Clean ARM64 rebuild recipe

Use a fresh extracted formal source copy. `rebuild-native.ps1` configures the
included Android CMake source directly and builds FreeRDP 3.33.1, WinPR,
the Android JNI wrapper, OpenSSL 4.0.3, cJSON, and uriparser. It strips the
nine newly built ARM64 libraries before packaging. SQLCipher 4.17.0 comes
from the declared Maven dependency and retains its separate source and license notice.

Prerequisites: Windows x64; SDK/build-tools 37.0.0/API37; NDK 29.0.13113456
(the tested source.properties reports 29.0.13113456-beta1); CMake 4.1.2/Ninja;
JDK21.0.12.1+1; Python3; existing MSYS2 bash, Perl, GNU make and pkg-config.
Gradle9.6.1/AGP9.2.1 and the exact Java dependencies in the source must be
provisioned before the offline packaging step. No installer or download is run
by this preparation. `native-rebuild-inputs.json` records original tool hashes
and the three dependency archive filenames/official URLs/SHA256 values.

Provision those three archives yourself in one directory and retain their
original filenames. The script verifies hashes and seeds the documented CMake
ExternalProject cache. No source/dependency fallback or older binary substitution
is accepted. The included public metadata/Windows OpenSSL recipes preserve the
actual public runtime defaults: WinPR `/`, `/etc`, `/share/locale`; OpenSSL `/`,
`lib`, `/usr/local/ssl`. Prefix mapping uses reader-supplied directories.

Example (paths below are reader-supplied parameters, never private build paths):
`./rebuild-native.ps1 -SourceRoot <extracted-source> -AndroidSdk <sdk> -JavaHome <jdk> -MsysRoot <msys> -MsysMake <make.exe> -Python3 <python.exe> -ArchivesDirectory <verified-archives>`

Add `-NativeOnly` to stop after writing the stripped libraries under
`.source-offer-rebuild/native/arm64-v8a/`. `-Jobs` controls parallelism, with
a default of two. Allow room for an isolated source copy, dependencies and
generated build files. The script requires a fresh build directory.

The release's `native-build-receipt.json` records the exact source and
output hashes from the ARM64 native build. Compare ABI, behavior and license
inputs independently when rebuilding on another host. Compiler and packaging
timestamps can change binary hashes. The packaging step produces an unsigned APK.
