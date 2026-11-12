# Really Decent Portal 0.1.7 測試版

[English](RELEASE_NOTES.md)

GitHub 測試版，適用於 Android 10（API 29）以上的 ARM64 裝置。

## 輸入特色

- 實體觸控板支援左鍵與右鍵點按、平順雙軸捲動和輕點兩下後按住拖曳。Android 14 以上的相容觸控板沿用 0.1.6 調校的捏合靈敏度，可逐級縮放遠端內容。
- S Pen 透過 RDP 筆輸入轉送懸浮、筆尖接觸、筆壓與側鍵。筆的行為取決於遠端工作階段與應用程式。

## 0.1.7 更新

- 原生連線核心更新至 FreeRDP 3.33.1，內附 OpenSSL 更新至 4.0.3。
- 移除 Android 與 JNI 日誌中的剪貼簿原文。
- 連線失敗或中斷時，顯示易讀的原因。按「複製錯誤訊息」可取得完整代碼和診斷資料。

## 下載

此版本提供配對的簽署檔 `Really-Decent-Portal-0.1.7-arm64-v8a.apk`、`Really-Decent-Portal-0.1.7-source.zip`、`SHA256SUMS.txt` 及 `RELEASE_ARTIFACTS.json`。來源 ZIP 包含建置說明和適用的授權原文。

已在搭配 Book Cover Keyboard 的 Samsung 平板實測捏合縮放、普通捲動與 Ctrl 快捷鍵。Wi‑Fi 短暫中斷後也能自動回到同一個遠端工作階段。不同裝置、遠端應用程式與長時間連線仍是測試重點。
