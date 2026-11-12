# Really Decent Portal 0.1.7 Beta

適用於 Android 10（API 29）以上的 ARM64 裝置。這是 GitHub 公開測試版。

## 本版更新

- 原生連線核心更新至 FreeRDP 3.33.1，內附 OpenSSL 更新至 4.0.3。
- 移除 Android 與 JNI 日誌中的剪貼簿原文。
- 保留 0.1.6 在 Book Cover 上調校的縮放靈敏度。雙指捏合或張開會逐級調整遠端應用程式的內容倍率。
- 連線失敗或中斷時，畫面顯示原因。按「複製錯誤訊息」可取得完整代碼和診斷資料。

## 下載與回饋

同版附件包含簽署的 `Really-Decent-Portal-0.1.7-arm64-v8a.apk`、`Really-Decent-Portal-0.1.7-source.zip`、`SHA256SUMS.txt` 及 `RELEASE_ARTIFACTS.json`。來源 ZIP 隨附建置配方和適用的授權原文。

公開測試重點包括不同 Android 裝置與遠端應用程式的輸入相容性，以及長時間連線與重連。回報問題時，可提供 App 版本、Android 版本、裝置型號和可重現步驟。

## English

Public beta for ARM64 devices running Android 10 or later.

- Updates the native connection core to FreeRDP 3.33.1 and the bundled OpenSSL to 4.0.3.
- Removes raw clipboard text from Android and JNI logs.
- Keeps the Book Cover pinch sensitivity tuned in 0.1.6. Pinching adjusts the remote app's content zoom in steps.
- Displays a readable reason when a connection fails or ends. **Copy error details** includes the full codes and diagnostic information.

The release assets include the signed APK, corresponding source archive, SHA-256 checksums, and a release manifest. Compatibility across devices and remote applications, and stability over long sessions, are still being evaluated.
