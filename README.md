# Really Decent Portal

適合 Android 平板的 RDP 用戶端，支援實體鍵盤、滑鼠、觸控板、手指與 S Pen。專案衍生自 FreeRDP／aFreeRDP，由社群維護。

Really Decent Portal is a community-maintained Android RDP client based on FreeRDP and aFreeRDP, with support for tablet keyboards, mice, trackpads, touch, and S Pen.

## 下載與連線

**0.1.7 公開測試版**支援 Android 10（API 29）以上的 ARM64 裝置。請從同一版 Release 取得簽署 APK、對應來源 ZIP 和校驗資料。 [GitHub Releases](https://github.com/muen1019/Really-Decent-Portal/releases) · [發行說明](RELEASE_NOTES.md)

1. 下載 `Really-Decent-Portal-0.1.7-arm64-v8a.apk`，依 Android 提示安裝。檔案的 SHA-256 可與同版的 `SHA256SUMS.txt` 核對。
2. 在 App 新增遠端電腦，填寫位址、連接埠及登入資訊。
3. 若出現憑證警告，請先確認遠端電腦的身分與憑證指紋，再決定是否接受。

正式套件名稱為 `io.github.muen1019.reallydecentportal`。原有正式版可透過同一簽章原地升級。App 提供英文與繁體中文介面。

## 輸入操作

- 手指可切換直接觸控與螢幕觸控板。實體滑鼠和 Book Cover 觸控板有各自的輸入處理。
- 鍵盤支援修飾鍵、長按重複和快捷鍵。若要轉送 Android 原本會攔截的快捷鍵，可在 Android 設定中啟用 App 的快捷鍵擷取服務。
- 實體滑鼠支援滾輪、側鍵和拖曳。觸控板支援雙軸捲動、輕點兩下後按住拖曳，以及逐級縮放遠端內容。
- S Pen 採用標準 RDP 筆輸入。懸浮游標和落筆效果由遠端 Windows 與應用程式決定。
- 新連線預設使用整片螢幕的解析度、200% 遠端桌面縮放，以及隱藏 Android 系統列。

上述輸入操作使用 RDP，遠端 Windows 可直接接收。觸控板捏合以 Ctrl＋滾輪調整內容倍率，頁面可能重新排版。在支援的瀏覽器中，滑鼠側鍵可用於上一頁和下一頁。不同裝置及遠端應用程式的手勢相容性，以及長時間連線穩定性，仍在公開測試範圍內。

斷線時，對話框會顯示原因。需要回報問題時，可按「複製錯誤訊息」取得診斷資料。請在分享前確認內容，並將帳密、主機資訊和剪貼簿內容留在自己的裝置上。

## 來源與授權

Android 來源位於 [`source/`](source/)。此版的原生核心以 [FreeRDP 3.33.1](https://github.com/FreeRDP/FreeRDP/releases/tag/3.33.1) 為基礎，包含專案的 Android 輸入與連線修改。發行附件 `Really-Decent-Portal-0.1.7-source.zip` 包含對應來源、相依套件版本和 [建置說明](SOURCE_OFFER/build/BUILD.md)。在此 repository 的目錄配置中，請將建置配方的 `-SourceRoot` 指向 `source/`。

授權依各檔案與元件適用。 [授權範圍](LICENSE.md) · [第三方告知](THIRD_PARTY_NOTICES.md) · [授權原文](LICENSES/) · [元件與來源對照](CURRENT_RUNTIME_LICENSE_MAP.json)
