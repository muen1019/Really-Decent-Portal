# Really Decent Portal 0.1.7 對應原始碼

[English](SOURCE_LOCATION.md)

從 [0.1.7 發行頁](https://github.com/muen1019/Really-Decent-Portal/releases/tag/v0.1.7) 一併取得 APK 與 `Really-Decent-Portal-0.1.7-source.zip`。兩份檔案的 SHA-256 列於同版的 `RELEASE_ARTIFACTS.json`。若 APK 由他人單獨提供，可向提供者索取同版來源。

壓縮檔頂層的 `Really-Decent-Portal-0.1.7/` 內含完整修改來源。ARM64 建置會依固定版本重建 FreeRDP、WinPR、Android JNI、OpenSSL、cJSON 及 uriparser，SQLCipher 則由宣告的 Android AAR 提供。 [建置說明](build/BUILD.zh-Hant.md) · [工具與相依來源](build/native-rebuild-inputs.json) · [第三方告知](../THIRD_PARTY_NOTICES.md)
