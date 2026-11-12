<!-- SPDX-License-Identifier: MPL-2.0 -->
<!-- The license applies to this project's original arrangement and explanations.
Third-party code, data, copyright notices, and license texts keep their original terms. -->

# Third-party notices and corresponding source

[繁體中文](THIRD_PARTY_NOTICES.md)

This notice covers the Really Decent Portal 0.1.7 ARM64 APK. Its FreeRDP base is 3.33.1, commit `65bcca1913a28cbdd7eb913599f692bd2b707991`. Really Decent Portal is an independent distribution derived from FreeRDP and aFreeRDP.

The [license directory](LICENSES/) contains the original texts. [Source-only notices](LICENSES/SOURCE_ONLY/) cover material supplied as source or used by the build tools. The terms on each source file, library, or data file continue to apply.

## Corresponding source

Download `Really-Decent-Portal-0.1.7-source.zip` alongside the matching APK from the [0.1.7 release](https://github.com/muen1019/Really-Decent-Portal/releases/tag/v0.1.7). If someone supplies the APK separately, ask that distributor for the corresponding source. The app's About page also provides the source offer.

Sections 3.1 and 3.2 of the MPL-2.0 govern distribution of the corresponding source for covered Android Java, resources, and JNI files. Recipients retain their MPL rights to that source. [MPL-2.0](LICENSES/MPL-2.0.txt)

## FreeRDP, WinPR, and aFreeRDP

Most FreeRDP and WinPR source uses Apache-2.0. Android Java, resources, and JNI files marked MPL-2.0 follow the MPL. The original copyright and notices in each file apply, including notices by the FreeRDP contributors, Thincast Technologies GmbH, and other named authors. [Apache-2.0](LICENSES/Apache-2.0.txt) · [MPL-2.0](LICENSES/MPL-2.0.txt) · [Pinned upstream source](https://github.com/FreeRDP/FreeRDP/tree/65bcca1913a28cbdd7eb913599f692bd2b707991)

| Compiled component | Original notices |
| --- | --- |
| AOSP CPU features, Copyright 2010 The Android Open Source Project | [BSD two-clause-style text](LICENSES/AOSP-cpufeatures-BSD-2-style.txt) and [upstream NOTICE](LICENSES/AOSP-cpufeatures-NOTICE.txt) |
| OpenSL input and output, Copyright 2012 Victor Lazzarini | [BSD three-clause-style text](LICENSES/Victor-Lazzarini-OpenSL-BSD-style.txt), covering the audin and rdpsnd copies |
| MD4 and MD5, Alexander Peslyak (Solar Designer) | [MD4 public-domain and fallback notice](LICENSES/Openwall-MD4-public-domain-fallback.txt) and [MD5 public-domain and fallback notice](LICENSES/Openwall-MD5-public-domain-fallback.txt) |
| WinPR WindowsZones, generated from Unicode CLDR | [CLDR 2025 Unicode-3.0 text](LICENSES/Unicode-CLDR-c33a1f0-LICENSE.txt) and [data copyright notice for 1991–2013](LICENSES/Unicode-CLDR-windowsZones-NOTICE.txt) |
| WinPR IANA time-zone aliases, generated from Unicode CLDR | [CLDR 2026 Unicode-3.0 text](LICENSES/Unicode-CLDR-7b1090a-LICENSE.txt) and [data copyright notice for 1991–2017](LICENSES/Unicode-CLDR-timezone-aliases-NOTICE.txt) |

### Unicode CLDR provenance

The 500 ordered `(IANA type, Windows name, territory)` entries in the compiled WindowsZones table match the CLDR XML at commit `c33a1f0a23e3b676b406345fe0b42c130defc51d`, including its `otherVersion` and `typeVersion` values. The original generator fetched a moving branch, so the paired snapshot is the verified source equivalent. [FreeRDP generation change](https://github.com/FreeRDP/FreeRDP/commit/9cd3ffa84342e11b148920a48b47a1013e45ca7d) · [CLDR WindowsZones data](https://github.com/unicode-org/cldr/blob/c33a1f0a23e3b676b406345fe0b42c130defc51d/common/supplemental/windowsZones.xml)

All 19 WinPR IANA alias entries match the output of FreeRDP's extraction rules on CLDR commit `7b1090ada749eb29d85d64bfb6c1952671d4b1f5`. This is the last change to the XML before the upstream generation commit. [CLDR alias data](https://github.com/unicode-org/cldr/blob/7b1090ada749eb29d85d64bfb6c1952671d4b1f5/common/bcp47/timezone.xml) · [FreeRDP generation change](https://github.com/FreeRDP/FreeRDP/commit/1fcade45924a86e346570a286288a73c38749684)

## OpenSSL 4.0.3

`libcrypto.so`, `libssl.so`, and `legacy.so` come from the same verified OpenSSL 4.0.3 source archive. Its main license is Apache-2.0. The compiled ARM `poly1305-armv9-sve2` source offers BSD-style or GPL terms. This distribution retains its complete notice under the BSD-style option. [OpenSSL 4.0.3 license](LICENSES/OpenSSL-4.0.3-Apache-2.0.txt) · [CRYPTOGAMS ARMv9 notice](LICENSES/OpenSSL-CRYPTOGAMS-armv9-notice.txt) · [Pinned source](https://github.com/openssl/openssl/tree/openssl-4.0.3)

The OpenSSL source archive also retains the terms for other architectures and build tools, including the GPL-1.0-or-later and Artistic options for Text::Template. [Text::Template license](LICENSES/SOURCE_ONLY/OpenSSL-Text-Template-1.56-LICENSE.txt)

## cJSON 1.7.19

`libcjson.so` uses the MIT license. Copyright 2009–2017 Dave Gamble and cJSON contributors. [Original license](LICENSES/cJSON-1.7.19-MIT.txt) · [Pinned source](https://github.com/DaveGamble/cJSON/tree/v1.7.19)

## uriparser 1.0.2

The shipped library's `src` and `include` code uses BSD-3-Clause. Copyright 2007 Weijia Song and Sebastian Pipping. The source archive also contains LGPL-2.1-or-later tests and Apache-2.0 fuzzing code. The Android build disables those targets and the command-line tools. [Library license](LICENSES/uriparser-1.0.2-BSD.txt) · [Test suite notice](LICENSES/SOURCE_ONLY/uriparser-test-COPYING.txt) · [Pinned source](https://github.com/uriparser/uriparser/tree/uriparser-1.0.2)

FreeRDP's `uwac/protocols/server-decoration.xml` retains its LGPL-2.1-or-later notice in the complete source. It is outside the compiled Android library. [LGPL-2.1](LICENSES/SOURCE_ONLY/LGPL-2.1.txt)

## SQLCipher for Android 4.17.0

The Maven Community AAR `net.zetetic:sqlcipher-android:4.17.0` supplies the Java bindings and `libsqlcipher.so`. Its original sources and notices are paired here.

- SQLCipher core, Copyright 2025 ZETETIC LLC. [BSD-style license](LICENSES/SQLCipher-core-v4.17.0-BSD.txt)
- Android Java and JNI bindings include AOSP code. [Apache-2.0](LICENSES/Apache-2.0.txt)
- SQLite core uses public-domain terms. SQLCipher's encryption extensions have their own terms. [Original SQLite notice](LICENSES/SQLite-core-public-domain-notice.txt)
- The upstream tag defaults to LibTomCrypt gitlink `476a9579ae94f32b9ea9e2747bfb04b302370259`, under public-domain or WTFPL v2 terms. [Pinned license](LICENSES/LibTomCrypt-476a957-LICENSE.txt)

The SQLCipher core gitlink is `e2a6040f2ae5cfff2b3e08eb3320007d93cdf3fc` and its SQLite version is 3.53.1. The reviewed evidence pairs the vendor AAR and upstream source declarations. OpenSSL 4.0.3 is a separate dependency of the app's RDP implementation. [Vendor source](https://github.com/sqlcipher/sqlcipher-android/tree/v4.17.0) · [Crypto provider configuration](https://github.com/sqlcipher/sqlcipher-android/blob/v4.17.0/sqlcipher/src/main/jni/sqlcipher/Android.mk)

## AndroidX, Kotlin, and other JVM dependencies

The [runtime license map](CURRENT_RUNTIME_LICENSE_MAP.json) records 63 external components, including their AAR, JAR, and POM evidence and the hashes of embedded notices. Most declared terms are Apache-2.0. Guava ListenableFuture inherits the Apache declaration from `guava-parent:26.0-android`. The supplied Kotlin 2.2.10 stdlib includes [ThreeTen BSD](LICENSES/Kotlin-ThreeTen-BSD.txt), [Boost-licensed JVM math](LICENSES/Kotlin-Math-Boost-1.0.txt), [GWT](LICENSES/Kotlin-GWT-Apache-2.0.txt), and [Guava](LICENSES/Kotlin-Guava-Apache-2.0.txt) material alongside [Kotlin's Apache-2.0 text](LICENSES/Kotlin-2.2.10-Apache-2.0.txt). [Kotlin upstream license breakdown](https://github.com/JetBrains/kotlin/blob/v2.2.10/license/README.md)

| Component | Declared terms and known exceptions | Embedded notice entries | Notice location |
| --- | --- | ---: | --- |
| `androidx.activity:activity-ktx:1.8.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.activity:activity:1.8.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.annotation:annotation-experimental:1.5.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.annotation:annotation-jvm:1.10.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.appcompat:appcompat-resources:1.7.1` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.appcompat:appcompat:1.7.1` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.arch.core:core-common:2.2.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.arch.core:core-runtime:2.2.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.collection:collection-jvm:1.5.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.collection:collection-ktx:1.5.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.concurrent:concurrent-futures:1.1.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.core:core-ktx:1.19.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.core:core-viewtree:1.0.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.core:core:1.19.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.cursoradapter:cursoradapter:1.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.customview:customview-poolingcontainer:1.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.customview:customview:1.1.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.databinding:viewbinding:9.2.1` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.drawerlayout:drawerlayout:1.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.emoji2:emoji2-views-helper:1.3.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.emoji2:emoji2:1.3.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.fragment:fragment-ktx:1.3.6` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.fragment:fragment:1.5.4` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.interpolator:interpolator:1.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-common-jvm:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-livedata-core-ktx:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-livedata-core:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-livedata:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-process:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-runtime-android:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.lifecycle:lifecycle-viewmodel:2.11.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.loader:loader:1.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.preference:preference:1.2.1` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.profileinstaller:profileinstaller:1.4.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.recyclerview:recyclerview:1.4.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.resourceinspection:resourceinspection-annotation:1.0.1` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.room:room-common-jvm:2.8.4` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.room:room-runtime-android:2.8.4` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.savedstate:savedstate-android:1.4.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.savedstate:savedstate-ktx:1.4.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.slidingpanelayout:slidingpanelayout:1.2.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.sqlite:sqlite-android:2.7.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.sqlite:sqlite-framework-android:2.7.0` | Apache-2.0 | 1 | Apache-2.0 / embedded mapping |
| `androidx.startup:startup-runtime:1.1.1` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.tracing:tracing:1.2.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.transition:transition:1.4.1` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.vectordrawable:vectordrawable-animated:1.1.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.vectordrawable:vectordrawable:1.1.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.viewpager:viewpager:1.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `androidx.window:window:1.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `com.google.guava:listenablefuture:1.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `net.zetetic:sqlcipher-android:4.17.0` | BSD-style core; Apache-2.0 bindings; SQLite / LibTomCrypt terms above | 0 | SQLCipher section |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.10` | Apache-2.0 with embedded BSD and Boost material | 0 | Kotlin section |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `org.jetbrains:annotations:23.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `org.jspecify:jspecify:1.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |

## Original license texts and hashes

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| [Android-NDK29-NOTICE.txt](LICENSES/Android-NDK29-NOTICE.txt) | 516438 | `faf0dc52cf6ce9ab09a04b0c074a378dd2565ee1ed9bc1179d83793bcdba012e` |
| [AOSP-cpufeatures-BSD-2-style.txt](LICENSES/AOSP-cpufeatures-BSD-2-style.txt) | 1398 | `4102cd5ae35cbdeb0638c350966185e028ea9da863859f70e231c3cb273118d1` |
| [AOSP-cpufeatures-NOTICE.txt](LICENSES/AOSP-cpufeatures-NOTICE.txt) | 577 | `c870d52f42a8f93023b99a85fe68d02637d6b04e5f35bbcd7f98759adb4e4d49` |
| [Apache-2.0.txt](LICENSES/Apache-2.0.txt) | 11358 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| [cJSON-1.7.19-MIT.txt](LICENSES/cJSON-1.7.19-MIT.txt) | 1084 | `a36dda207c36db5818729c54e7ad4e8b0c6fba847491ba64f372c1a2037b6d5c` |
| [809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt](LICENSES/EMBEDDED/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) | 10175 | `809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0` |
| [Kotlin-2.2.10-Apache-2.0.txt](LICENSES/Kotlin-2.2.10-Apache-2.0.txt) | 11358 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| [Kotlin-Guava-Apache-2.0.txt](LICENSES/Kotlin-Guava-Apache-2.0.txt) | 11358 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| [Kotlin-GWT-Apache-2.0.txt](LICENSES/Kotlin-GWT-Apache-2.0.txt) | 11358 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| [Kotlin-Math-Boost-1.0.txt](LICENSES/Kotlin-Math-Boost-1.0.txt) | 1337 | `8d8291caf1cee26d23acf3eb67c9f9a2d58f1c681b16a4fbe8cbfb9e3c0b5a9b` |
| [Kotlin-ThreeTen-BSD.txt](LICENSES/Kotlin-ThreeTen-BSD.txt) | 1520 | `d1bc53b493a3ab387b42717ed5c4b1976a5048996f81154278100bff86d39331` |
| [LibTomCrypt-476a957-LICENSE.txt](LICENSES/LibTomCrypt-476a957-LICENSE.txt) | 719 | `8f196cb13afd271f5e267fd29543fc454596382ad580e7592709492843996ac8` |
| [LLVM-NDK29-runtime-NOTICE.txt](LICENSES/LLVM-NDK29-runtime-NOTICE.txt) | 130424 | `f96f763beb66a7ba7a667647fc64c0226ace875e590c831fdd9579ec1c1d91e1` |
| [MPL-2.0.txt](LICENSES/MPL-2.0.txt) | 16726 | `3f3d9e0024b1921b067d6f7f88deb4a60cbe7a78e76c64e3f1d7fc3b779b9d04` |
| [OpenSSL-4.0.3-Apache-2.0.txt](LICENSES/OpenSSL-4.0.3-Apache-2.0.txt) | 10175 | `7d5450cb2d142651b8afa315b5f238efc805dad827d91ba367d8516bc9d49e7a` |
| [OpenSSL-CRYPTOGAMS-armv9-notice.txt](LICENSES/OpenSSL-CRYPTOGAMS-armv9-notice.txt) | 2569 | `ac2e966f556cd7b8642df186e25998189df76872680858df2d9999e012d3cd08` |
| [Openwall-MD4-public-domain-fallback.txt](LICENSES/Openwall-MD4-public-domain-fallback.txt) | 1626 | `fee6b8995c471425e4e3780c8f100545adb7f1a2f08e222901ee177254093a24` |
| [Openwall-MD5-public-domain-fallback.txt](LICENSES/Openwall-MD5-public-domain-fallback.txt) | 1626 | `c7cf010490872c2fc0de69a68dcd729a4659aec4eef96a92be9d6b0098baa434` |
| [LGPL-2.1.txt](LICENSES/SOURCE_ONLY/LGPL-2.1.txt) | 26435 | `278c725b20339da63f93314ba6204e72cdab93501aaff6f909471fd1ffe26053` |
| [OpenSSL-Text-Template-1.56-LICENSE.txt](LICENSES/SOURCE_ONLY/OpenSSL-Text-Template-1.56-LICENSE.txt) | 18412 | `9837f05336ef3cbacb6a96e1672a0426d81ad01191f214b8d48e22ca62338181` |
| [uriparser-test-COPYING.txt](LICENSES/SOURCE_ONLY/uriparser-test-COPYING.txt) | 26435 | `278c725b20339da63f93314ba6204e72cdab93501aaff6f909471fd1ffe26053` |
| [SQLCipher-core-v4.17.0-BSD.txt](LICENSES/SQLCipher-core-v4.17.0-BSD.txt) | 1463 | `2a2826f6acf46fa650730cf42cbb22a642be33a7ef119c9c4f4bf6daf3bef48e` |
| [SQLite-core-public-domain-notice.txt](LICENSES/SQLite-core-public-domain-notice.txt) | 610 | `71c9d370765c4e00aaf89d43bdb57cef6d2404aa3121a1f29931778c0e599b46` |
| [Unicode-CLDR-c33a1f0-LICENSE.txt](LICENSES/Unicode-CLDR-c33a1f0-LICENSE.txt) | 2033 | `b4c0ae8ef04f7059f96ce5bbe0467f9fe6f6d81bbe13517701dfeb961fb4d0b6` |
| [Unicode-CLDR-windowsZones-NOTICE.txt](LICENSES/Unicode-CLDR-windowsZones-NOTICE.txt) | 209 | `9ebad17a0b5a891c2c004c44f6de2479e2a374ce6275828519be9fbc460efc8c` |
| [Unicode-CLDR-timezone-aliases-NOTICE.txt](LICENSES/Unicode-CLDR-timezone-aliases-NOTICE.txt) | 209 | `65e902a6879f7816de81af2428933be59bf97404c483c5d11a2b760f831888f4` |
| [Unicode-CLDR-7b1090a-LICENSE.txt](LICENSES/Unicode-CLDR-7b1090a-LICENSE.txt) | 3193 | `65eb33419e22a297550a811b5b535f5e532f9a7e0114b516c0ea18afd53c3804` |
| [uriparser-1.0.2-BSD.txt](LICENSES/uriparser-1.0.2-BSD.txt) | 1734 | `287f09e6546a9610f949f89e8fb937cacfeabd7bfaa8c8a0c18312193bf04ad3` |
| [Victor-Lazzarini-OpenSL-BSD-style.txt](LICENSES/Victor-Lazzarini-OpenSL-BSD-style.txt) | 1557 | `24439ab6f79f3c0265e5da6877ca4442d41e47aef9548e56590764afb4259412` |

## ARM64 runtime inputs

The [runtime license map](CURRENT_RUNTIME_LICENSE_MAP.json) pairs the 0.1.7 native compile and link inputs with their shipped libraries and notices. It covers the rebuilt FreeRDP, WinPR, Android JNI wrapper, OpenSSL, cJSON, and uriparser, plus the separately supplied SQLCipher AAR.

The Android NDK 29 compiler support archives are paired with [LLVM's runtime notice](LICENSES/LLVM-NDK29-runtime-NOTICE.txt), including the LLVM Exceptions. The actual CRT objects are paired with the [Android NDK notice](LICENSES/Android-NDK29-NOTICE.txt). These original notices cover the broader toolchain as well. System shared libraries are supplied by Android. Files without a clear per-file header are recorded as `PER-FILE-ORIGINAL-UNCLASSIFIED` and distributed with their original contents and root notices.
