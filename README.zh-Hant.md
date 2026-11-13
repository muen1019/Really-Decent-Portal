<h1 align="center"><img src="assets/app-icon.png" width="48" height="48" alt="" /> Really Decent Portal</h1>

<p align="center"><strong>適用於 Android 平板的遠端桌面</strong></p>

<p align="center">
  <a href="https://github.com/muen1019/Really-Decent-Portal/releases/tag/v0.1.7"><img src="https://img.shields.io/badge/Beta-v0.1.7-167D75?style=flat-square" alt="0.1.7 測試版" /></a>
  <img src="https://img.shields.io/badge/Android-10%2B-287E3A?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android 10 以上" />
  <a href="https://github.com/FreeRDP/FreeRDP/releases/tag/3.33.1"><img src="https://img.shields.io/badge/FreeRDP-3.33.1-276589?style=flat-square" alt="FreeRDP 3.33.1" /></a>
</p>

<p align="center">
  <a href="https://github.com/muen1019/Really-Decent-Portal/releases/tag/v0.1.7">下載</a> ·
  <a href="#安裝與連線">快速開始</a> ·
  <a href="RELEASE_NOTES.zh-Hant.md">發行說明</a> ·
  <a href="README.md">English</a>
</p>

Really Decent Portal 源自 FreeRDP 與 aFreeRDP，支援實體觸控板、鍵盤、滑鼠及 S Pen。

## 輸入特色

- **實體觸控板。** 支援左鍵與右鍵點按、雙指平順縱向與橫向捲動，以及輕點兩下後按住拖曳。裝置回報雙軸位移時，也可斜向捲動。兩種螢幕觸控模式都可使用這些操作。
- **S Pen。** 透過 RDP 筆輸入轉送懸浮、筆尖接觸、筆壓與側鍵。已在 Samsung 平板的兩種螢幕觸控模式測試懸浮跟隨、落筆和側鍵。筆的實際行為取決於遠端 Windows 工作階段及應用程式。
- **觸控、鍵盤與滑鼠。** 螢幕可切換直接觸控與螢幕觸控板。實體鍵盤支援修飾鍵、長按重複和快捷鍵。可選擇啟用 Android 快捷鍵擷取服務，轉送支援的系統快捷鍵。實體滑鼠支援捲動、拖曳及側鍵，在支援的瀏覽器中可用側鍵切換上一頁與下一頁。

新連線預設使用全螢幕解析度、200% 遠端桌面縮放，以及隱藏 Android 系統列。斷線時會顯示原因，按「複製錯誤訊息」可取得完整代碼及診斷資料。

## 安裝與連線

**0.1.7 測試版**適用於 Android 10（API 29）以上的 ARM64 裝置。App 提供繁體中文與英文介面。 [下載 0.1.7 測試版](https://github.com/muen1019/Really-Decent-Portal/releases/tag/v0.1.7)

1. 下載並安裝 `Really-Decent-Portal-0.1.7-arm64-v8a.apk`。檔案的 SHA-256 可用同版 `SHA256SUMS.txt` 核對。
2. 新增遠端電腦，填寫位址、連接埠及登入資訊。

正式套件名稱為 `io.github.muen1019.reallydecentportal`。以相同發行金鑰簽署的舊版可原地升級。測試版著重驗證不同裝置與遠端應用程式的輸入相容性，以及長時間連線穩定性。

## 來源與授權

修改後的原始碼位於 [`source/`](source/)。原生核心以 [FreeRDP 3.33.1](https://github.com/FreeRDP/FreeRDP/releases/tag/3.33.1) 為基礎。同版附件 `Really-Decent-Portal-0.1.7-source.zip` 提供對應來源、相依套件版本和建置說明。從此儲存庫建置時，將 `-SourceRoot` 指向 `source/`。 [建置說明](SOURCE_OFFER/build/BUILD.zh-Hant.md) · [對應原始碼](SOURCE_OFFER/SOURCE_LOCATION.zh-Hant.md)

授權依各檔案及元件適用。 [授權範圍](LICENSE.zh-Hant.md) · [第三方告知](THIRD_PARTY_NOTICES.md) · [授權原文](LICENSES/) · [執行階段元件與來源對照](CURRENT_RUNTIME_LICENSE_MAP.json)
