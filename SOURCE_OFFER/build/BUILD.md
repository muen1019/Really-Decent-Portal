# Clean ARM64 rebuild recipe (not executed by source preparation)

Use a fresh extracted formal source copy. `rebuild-native.ps1` configures the
included Android CMake source directly, builds OpenSSL/cJSON/uriparser and all
FreeRDP/WinPR/JNI source, then packages the freshly produced nine native members.
It does not reuse the private tested native producer. SQLCipher 4.17.0 remains
the declared Maven dependency, whose separate source/license notice is retained.

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

Use at least12GiB free disk plus the planned build-growth reserve, and6GiB
available physical/commit memory; retain source/final outputs and only clean
owned generated build directories after verification. The script never deletes
an existing tree and rejects reused clean-build directories. Private formal
stage/owner checks are not prerequisites for this public source rebuild.

This is a source-level clean-build recipe derived from actual producer inputs,
not a newly executed clean-build result or a promise of bit-for-bit identical
APKs. The actual candidate used qualified unchanged cores plus a rebuilt JNI
wrapper. Compare behavior, ABI, source mapping and licenses independently;
timestamps/toolchain packaging and normal stripping can affect binary hashes.
No signing keys, credentials, device access, service or publication is included.
