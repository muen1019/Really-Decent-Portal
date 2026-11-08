# Really Decent Portal

Android RDP 用戶端，著重平板上的實體鍵盤、滑鼠、觸控板、手指與 S Pen 操作。
衍生自 FreeRDP／aFreeRDP，並非 FreeRDP、Microsoft 或 Samsung 官方發行。

版本 **0.1.0**，適用 Android 10（API 29）以上的 ARM64 裝置。正式簽章 APK 與對應來源的檔名、SHA-256 及簽章指紋見
`RELEASE_ARTIFACTS.json`，APK 與來源 ZIP 於同一版本的 Release 附件提供。

## 使用方式

- 手指可切換直接觸控與螢幕觸控板；實體滑鼠、Book Cover 觸控板獨立處理。
- 鍵盤處理修飾鍵持有／放開、快捷鍵及長按重複；系統快捷鍵需在 Android
  啟用這個 App 的快捷鍵擷取服務。服務只在自己的遠端工作階段處理輸入。
- 支援滑鼠滾輪、側鍵及拖曳；雙軸捲動仍使用標準 RDP 滾輪訊號。
- S Pen 使用標準 RDP 筆輸入；懸浮外觀遵從遠端 Windows／應用程式。
- 新連線預設使用整片螢幕解析度、200% 桌面縮放與隱藏系統列。
- 捏合維持類似 `Ctrl+=`／`Ctrl+-` 的逐級內容縮放，可能重新排版。

上述操作不要求在遠端 Windows 安裝本專案配套程式。實際功能仍取決於
Android 裝置、RDP 雙端與遠端應用程式的支援。帳密請直接在 App 輸入，
不要放入 issue、紀錄或公開檔案。

## 來源與建置

上游基礎為 [FreeRDP 3.33.0 固定 commit](https://github.com/FreeRDP/FreeRDP/tree/5a10d7b6b79a2fbc62e8eba77085cd356dc303a6)。
完整修改來源在 [`source/`](source/)，包含全部既有
修改及正式名稱／資產覆蓋。

同版 `Really-Decent-Portal-0.1.0-source.zip` 隨 APK 一併提供。
[`SOURCE_OFFER/`](SOURCE_OFFER/) 保留來源／完整差異清單及乾淨重建配方；
在此 repository 版面，將配方的 `-SourceRoot` 指向 `source/`。
配方依實際工具與依賴版本整理，尚未另做全新乾淨環境重建，不宣稱位元完全相同。
APK 作為 Release 附件，不加入 Git 原始碼樹。

## 驗證範圍與已知限制

- 同一輸入實作已在電腦上的 Android 模擬器連入 Windows，驗證所測雙軸捲動量及 Ctrl 持有／放開。
- Book Cover 最小實體短測中，重新落指捲動、垂直與斜向轉向、立即輕點
  兩下按住拖曳／放開，使用者回報正常；這部分以使用者觀察為依據，未逐筆獨立核對原始事件。
- 長時間連線穩定性尚未充分驗收；先前操作中突然斷線的回報仍保留。
- 水平捲動不等於瀏覽器上一頁／下一頁。沒有承諾所有裝置、應用程式或
  每種手勢的原生連續縮放，也不以合成事件冒認硬體通過。

正式產物與簽章／來源的必要讀回由 `RELEASE_ARTIFACTS.json` 及發行附件配對。
詳見 [發行說明](RELEASE_NOTES.md)。

## 授權

本專案是依檔案與元件適用的混合授權；**沒有把全部程式改成單一授權**。
請閱讀 [授權範圍](LICENSE.md)、[第三方告知](THIRD_PARTY_NOTICES.md) 與
[`LICENSES/`](LICENSES/) 的 27 份原文。
第三方告知中的草稿／公開狀態保留先前稽核快照，當前版本與發行狀態請以 `RELEASE_ARTIFACTS.json` 及 GitHub Release 為準。

來源、原作者、修改告知與適用 NOTICE 均保留。詳細元件／來源對照見
[`CURRENT_RUNTIME_LICENSE_MAP.json`](CURRENT_RUNTIME_LICENSE_MAP.json)。
GitHub 版面保留上游 `.github` 於 `source/.github`；此專案未新增 root 工作流程，
因此不會僅因首次公開就啟動上游完整 CI。
