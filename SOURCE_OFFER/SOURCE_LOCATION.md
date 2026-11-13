# Really Decent Portal 0.1.9 source

This archive is supplied alongside the matching APK.
Keep `Really-Decent-Portal-0.1.9-source.zip` available at the same location
where recipients get the matching APK. The external release manifest identifies
both files by SHA-256.

The top-level `Really-Decent-Portal-0.1.9/` directory contains the complete
modified FreeRDP source. See `SOURCE_OFFER/build/BUILD.md` for the build steps
and the accompanying third-party notices for component licenses.

The project release page is https://github.com/muen1019/Really-Decent-Portal/releases.
If a recipient gets the APK separately, provide this ZIP or another accessible
copy of the matching source.

The ARM64 build recipe rebuilds FreeRDP, WinPR, the Android JNI wrapper,
OpenSSL, cJSON and uriparser from the corresponding sources and pinned
dependency archives. The APK obtains SQLCipher from the declared Android AAR.
See `SOURCE_OFFER/build/BUILD.md` and `SOURCE_OFFER/build/native-rebuild-inputs.json`
for the toolchain and dependency inputs.
