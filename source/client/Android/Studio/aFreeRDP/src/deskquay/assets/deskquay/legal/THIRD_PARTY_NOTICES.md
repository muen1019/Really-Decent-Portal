<!-- SPDX-License-Identifier: MPL-2.0 -->
<!-- Applies only to this project's original notice arrangement and explanations.
Quoted third-party material, copyright, license texts and notices retain their
original terms. Distribution modification: 2026-11-12. -->

# 第三方授權與來源告知

本文件說明 Really Decent Portal 0.1.8 ARM64 的第三方元件、授權與對應來源。FreeRDP 基底為 3.33.1，固定 commit 為 `65bcca1913a28cbdd7eb913599f692bd2b707991`。

本產品衍生自 FreeRDP／aFreeRDP，包含下列分別授權的程式碼與資料。它不是 FreeRDP 官方發行，也不代表各上游作者、Microsoft、Google、Mozilla、Zetetic 或其他權利人背書。保留的版權與授權文字不因 App 改名而消失。

各授權全文收錄於 [授權原文](LICENSES/)。`SOURCE_ONLY/` 收錄僅適用於隨附來源或建置工具的條款。各檔案、程式庫與資料依其原有授權提供。

## 對應修改來源的取得方式

與 APK 同版的完整修改來源封存檔名為 `Really-Decent-Portal-0.1.8-source.zip`。請從 APK 的提供處一併取得，或向提供 APK 的人索取。App「關於」中的「對應原始碼」頁也提供取得說明。

對 MPL-2.0 Covered Software，包含修改的 Android Java、資源及 JNI 檔案，散布者須依 MPL 3.1／3.2 提供對應來源及告知取得方式。該來源的 MPL 權利不受 App 二進位的其他使用條款限制。[MPL-2.0 全文](LICENSES/MPL-2.0.txt)

## FreeRDP、WinPR 與 aFreeRDP

FreeRDP／WinPR 的主要來源使用 Apache License 2.0。aFreeRDP 的多個 Android GUI、XML 與 JNI 檔案另標示 Mozilla Public License 2.0；適用範圍依個別檔案與保留通知。版權屬 FreeRDP contributors、Thincast Technologies GmbH 及各來源檔案所列作者；修改檔案仍須保留適用的原通知。

- [Apache-2.0 全文](LICENSES/Apache-2.0.txt)
- [MPL-2.0 全文](LICENSES/MPL-2.0.txt)
- [來源](https://github.com/FreeRDP/FreeRDP/tree/65bcca1913a28cbdd7eb913599f692bd2b707991)

實際編譯的原始碼另含下列通知，不能被根目錄的 Apache 標示取代：

| 來源部分 | 保留通知 |
|---|---|
| AOSP CPU features，Copyright 2010 The Android Open Source Project | [原檔 BSD 兩條款式完整註記](LICENSES/AOSP-cpufeatures-BSD-2-style.txt)、[同目錄原 NOTICE](LICENSES/AOSP-cpufeatures-NOTICE.txt) |
| OpenSL input/output，Copyright 2012 Victor Lazzarini | [原 BSD 三條款式完整註記](LICENSES/Victor-Lazzarini-OpenSL-BSD-style.txt)，適用 audin／rdpsnd 兩個來源副本 |
| MD4／MD5，Alexander Peslyak（Solar Designer） | [MD4 原 public-domain／fallback 註記](LICENSES/Openwall-MD4-public-domain-fallback.txt)、[MD5 原註記](LICENSES/Openwall-MD5-public-domain-fallback.txt) |
| WindowsZones 由 Unicode CLDR 資料生成 | [精確對應的 CLDR 2025 LICENSE（Unicode-3.0）](LICENSES/Unicode-CLDR-c33a1f0-LICENSE.txt) 與 [原資料 Copyright 1991–2013 notice](LICENSES/Unicode-CLDR-windowsZones-NOTICE.txt)。500 筆有序 mapping 與 CLDR commit `c33a1f0a23e3b676b406345fe0b42c130defc51d` 完全相同；並非套用目前最新授權。 |
| WinPR IANA 時區別名由 Unicode CLDR 資料生成 | [CLDR 2026 LICENSE（資料適用 Unicode-3.0）](LICENSES/Unicode-CLDR-7b1090a-LICENSE.txt) 與 [原資料 Copyright 1991–2017 notice](LICENSES/Unicode-CLDR-timezone-aliases-NOTICE.txt)。 |

## Unicode CLDR：已收斂的來源與通知

FreeRDP 此資料檔最後一次生成修改是 commit `9cd3ffa84342e11b148920a48b47a1013e45ca7d`（2025-04-11）。其前最近的 CLDR windowsZones 資料修改是 commit `c33a1f0a23e3b676b406345fe0b42c130defc51d`（2025-04-10）。已直接對實際 native compile source 的 500 筆 `(IANA type, Windows name, territory)` 作完整有序比對，與該 XML 的 500 筆完全相同，`otherVersion=7e11800`／`typeVersion=2021a` 亦同；`2021a` 不是這份資料最後生成年份。

該精確 CLDR revision 的 LICENSE 是 Unicode-3.0，Copyright 2004–2025 Unicode, Inc.；資料 XML 自身另保留 Copyright 1991–2013 Unicode, Inc.。兩者完整原文都在本目錄。已固定資料與當時 root LICENSE，沒有借用最新版 Unicode 文字。

當年 generator 只記錄了不固定的 main URL，未留下原 HTTP request 的 HEAD，因此不宣稱還原了那次請求；這裡固定的是**與已編譯資料完全匹配的官方 source revision 及其授權**。無需更改時區資料／native 邏輯即可在 associated documentation 及 App 授權資產補齊通知。

FreeRDP 3.33.1 新增的 WinPR IANA 時區別名表含 19 筆對照。依 tzextract 的提取規則重算，[CLDR 2026 資料快照](https://github.com/unicode-org/cldr/blob/7b1090ada749eb29d85d64bfb6c1952671d4b1f5/common/bcp47/timezone.xml) 的 19 筆對照與實際靜態表順序完全相同。該快照是 [FreeRDP 上游產生別名表](https://github.com/FreeRDP/FreeRDP/commit/1fcade45924a86e346570a286288a73c38749684) 前最後一次修改該資料檔的版本。上游產生器未固定下載 commit，這裡保留可重現的資料快照及對應原通知。

## OpenSSL 4.0.3

目前 `libcrypto.so`、`libssl.so`、`legacy.so` 來自同一個已核 SHA 的 OpenSSL 4.0.3 archive。OpenSSL 4.0.3 的主授權為 Apache-2.0；請見 [實際 archive 中的全文](LICENSES/OpenSSL-4.0.3-Apache-2.0.txt) 及 [上游該版本](https://github.com/openssl/openssl/tree/openssl-4.0.3)。既有 App About 中的舊 OpenSSL／SSLeay 說明不能單獨當成本版本的完整授權清單。

實際 ARM 建置包含 `poly1305-armv9-sve2` object，其來源保留 CRYPTOGAMS 的 BSD 式條款與另一個 GPL 選項。本發行依適用的 BSD 式條款保留完整通知。[完整原授權區塊](LICENSES/OpenSSL-CRYPTOGAMS-armv9-notice.txt)

OpenSSL archive 的部分其他架構 Camellia 來源亦保留 GPL／LGPL／MPL／BSD 可選文字；正式若增加 ABI，必須重新核對相應編譯集合。來源 archive 內 Text::Template 的 GPL-1.0-or-later／Artistic 選項屬建置工具來源；其原文保存在 [SOURCE_ONLY](LICENSES/SOURCE_ONLY/OpenSSL-Text-Template-1.56-LICENSE.txt)，不能據此宣告 APK runtime 為 GPL。

## cJSON 1.7.19

Copyright 2009–2017 Dave Gamble and cJSON contributors。`libcjson.so` 使用 MIT 授權；散布時保留該版權及授權文字。[完整原文](LICENSES/cJSON-1.7.19-MIT.txt)、[固定版本來源](https://github.com/DaveGamble/cJSON/tree/v1.7.19)

## uriparser 1.0.2

Library 的 `src`／`include` 為 BSD-3-Clause，Copyright 2007 Weijia Song、Sebastian Pipping。[實際 src/COPYING 全文](LICENSES/uriparser-1.0.2-BSD.txt)、[固定版本來源](https://github.com/uriparser/uriparser/tree/uriparser-1.0.2)

同一 archive 的 test suite 為 LGPL-2.1-or-later，fuzzing code 為 Apache-2.0，不能把整個來源 archive 都標成 BSD。目前實際 uriparser CMakeCache 的 tests／fuzzers／tools 均為 OFF；APK 中只有 library。若分發完整來源，仍須保留那些來源自己的授權。[test suite 原通知](LICENSES/SOURCE_ONLY/uriparser-test-COPYING.txt)、[LGPL-2.1 全文](LICENSES/SOURCE_ONLY/LGPL-2.1.txt)

固定 FreeRDP source 的 `uwac/protocols/server-decoration.xml` 也帶 LGPL-2.1-or-later 通知。它不是目前 Android library 編譯單元；公開完整 FreeRDP source 時仍保留其原文與對應 LGPL 全文。這些區分不是宣稱專案不存在 GPL／LGPL。

## SQLCipher for Android 4.17.0

使用 Maven Community artifact `net.zetetic:sqlcipher-android:4.17.0`。該 AAR 包含 Java bindings 與 `libsqlcipher.so`；AAR 本身沒有附上 LICENSE／NOTICE 資源，因此本產品需要另提供使用者可取得的通知。

- SQLCipher core：Copyright 2025 ZETETIC LLC，BSD 式條款。[固定 source gitlink 的全文](LICENSES/SQLCipher-core-v4.17.0-BSD.txt)
- Android Java／JNI bindings 有 AOSP Apache-2.0 來源；保留其原通知並提供 [Apache-2.0 全文](LICENSES/Apache-2.0.txt)。
- SQLite 原始部分為 public domain；SQLCipher 的加密擴充授權仍另行適用。[對應 core source 的原註記](LICENSES/SQLite-core-public-domain-notice.txt)、[SQLite 官方說明](https://www.sqlite.org/copyright.html)
- 官方 `v4.17.0` build 預設為 LibTomCrypt；該 tag 的 LibTomCrypt gitlink 為 `476a9579ae94f32b9ea9e2747bfb04b302370259`，其原授權為 public domain 或 WTFPL v2，可見 [完整原文](LICENSES/LibTomCrypt-476a957-LICENSE.txt)。這不是後來 develop 分支的 Unlicense 文字。

SQLCipher source gitlink 為 `e2a6040f2ae5cfff2b3e08eb3320007d93cdf3fc`；其 `VERSION` 是 SQLite 3.53.1。官方 [v4.17.0 README](https://github.com/sqlcipher/sqlcipher-android/blob/v4.17.0/README.md) 與 [Android.mk](https://github.com/sqlcipher/sqlcipher-android/blob/v4.17.0/sqlcipher/src/main/jni/sqlcipher/Android.mk) 說明預設與可選 crypto provider。此盤點核對了 vendor AAR 雜湊及官方 tag／gitlinks，沒有宣稱已重現 vendor 二進位建置；不能把本產品另外打包的 OpenSSL 4.0.3 直接認定為 SQLCipher 內部 crypto 的版本。

## AndroidX、Kotlin 與其他 JVM 元件

本次從既有真實 Java resource merge inputs 取得 63 個元件，核對各版本的本機 POM 與 AAR／JAR。AndroidX、Kotlin stdlib／coroutines／serialization、JSpecify／JetBrains annotations 的主授權聲明為 Apache-2.0；Guava ListenableFuture 的 Apache 聲明承自 `guava-parent:26.0-android`。SQLCipher 如上另述。

這些 POM 的主授權不代表每份內嵌程式都只有一種授權。Kotlin stdlib 2.2.10 的官方授權配置另外列出：

| 來源部分 | Attribution／完整文字 |
|---|---|
| `kotlin/time`，Stephen Colebourne、Michael Nascimento Santos | [ThreeTen BSD 三條款](LICENSES/Kotlin-ThreeTen-BSD.txt) |
| JVM math，Eric Ford、Hubert Holin | [Boost Software License 1.0](LICENSES/Kotlin-Math-Boost-1.0.txt) |
| collections 來自 GWT，Copyright 2007–2008 Google Inc. | [GWT Apache 原文](LICENSES/Kotlin-GWT-Apache-2.0.txt) |
| Unsigned JVM utilities 來自 Guava，Copyright 2011 The Guava Authors | [Guava Apache 原文](LICENSES/Kotlin-Guava-Apache-2.0.txt) |
| Kotlin 本身，JetBrains 及 Kotlin contributors | [該版本 Apache 原文](LICENSES/Kotlin-2.2.10-Apache-2.0.txt) |

依據：[Kotlin 2.2.10 官方授權配置](https://github.com/JetBrains/kotlin/blob/v2.2.10/license/README.md)。Kotlin repository 的 Compiler NOTICE 明確針對 compiler distribution，未將它自動當成這份 stdlib APK 的額外要求。

原 AAR／JAR 中找到的授權資源按內容去重後，逐位元組保存於 `LICENSES/EMBEDDED/`。元件與原始檔項目的對照及 SHA-256 保留於 [執行階段元件配對](CURRENT_RUNTIME_LICENSE_MAP.json)。

| 元件 | 授權主聲明／已知例外 | 原 artifact legal entry 數 | 通知位置 |
|---|---|---:|---|
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
| `net.zetetic:sqlcipher-android:4.17.0` | BSD-style core; Apache-2.0 bindings; SQLite / LibTomCrypt 如上 | 0 | SQLCipher 小節 |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.10` | Apache-2.0 ＋ BSD / Boost 內嵌 | 0 | Kotlin 小節 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `org.jetbrains:annotations:23.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |
| `org.jspecify:jspecify:1.0.0` | Apache-2.0 | 0 | Apache-2.0 / embedded mapping |

## 隨附授權原文與雜湊

| 授權文件 | Bytes | SHA-256 |
|---|---:|---|
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

## ARM64 執行階段元件的查核基準

隨附的 [執行階段元件配對](CURRENT_RUNTIME_LICENSE_MAP.json) 記錄 0.1.8 發行版的原生編譯來源、打包元件和適用授權。Java 相依套件沿用既有盤點，包含 63 個外部元件、各 classes.jar／原 AAR、JAR、POM 與內嵌原文。

本版重建 FreeRDP 3.33.1（commit 65bcca1913a28cbdd7eb913599f692bd2b707991）、WinPR 與 Android JNI，並使用 OpenSSL 4.0.3、cJSON 1.7.19 和 uriparser 1.0.2 的固定來源封存。執行階段元件配對保留實際編譯與連結來源的適用通知及雜湊。SQLCipher 使用供應商 AAR，對應來源與授權見上節。

NDK29 compiler support／linker alias另保留 [LLVM原NOTICE](LICENSES/LLVM-NDK29-runtime-NOTICE.txt)，包含 Apache-2.0 with LLVM Exceptions及所含原通知；實際 LLD dependency 中的 CRT objects 另配 [Android NDK原NOTICE](LICENSES/Android-NDK29-NOTICE.txt)。這兩份原文可能涵蓋較廣toolchain內容，不表示所有toolchain元件均進App；Android系統提供的shared libraries沒有隨APK散布。未命中明確header的來源保留 PER-FILE-ORIGINAL-UNCLASSIFIED，並保留其完整來源與根授權，不擅自改列Apache。本表是具體輸入／原文配對，非法律保證、全類別可達性或安全驗收。
