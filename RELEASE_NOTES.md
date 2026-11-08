# Really Decent Portal 0.1.0

package `io.github.muen1019.reallydecentportal`。正式 APK、簽章及
來源 SHA-256 必須以 `RELEASE_ARTIFACTS.json` 的實際讀回資料為準。

## 輸入修補

- 整合 FreeRDP 3.33.0 與既有鍵盤、觸控、滑鼠、S Pen 及生命週期修改。
- 處理觸控板手勢結束後緊接新手勢的邊界，避免新事件被當成已退休資料。
- 調整密集雙軸滾輪佇列／時序與修飾鍵邊界。
- 保留手指模式切換、實體滑鼠側鍵、長按重複與 Windows 原生筆懸浮路徑。

同一輸入實作的 Android 模擬器到 Windows RDP 對照已通過所測範圍；
Book Cover 實體短測由使用者回報正常，未宣稱已逐筆獨立核對原始事件。
本次正式身份／資產包裝只需必要產物比對，不重跑全部歷史功能測試。

## 發行資料

名稱為 Really Decent Portal；App 版本與上游 FreeRDP 版本分開。
正式 package 與 `.lab23` 測試版分開，不覆蓋既有 RD Client／Windows App。
上游固定 commit、實際對應來源與乾淨重建配方隨來源附件保留。

目前仍保留長時間連線穩定性未充分驗收、實體短測未逐筆核對原始事件，以及
瀏覽器導覽手勢與水平滾輪不同等限制。未宣稱所有已知安全或相容性問題均已排除。

## 授權與公開

FreeRDP／WinPR 的主要 Apache-2.0、Android 部分 MPL-2.0 及其他元件原文
依各檔保留；實際 runtime 依賴及靜態支援碼通知已配對，新增的兩份 NDK
通知與原有 25 份原文一起保留。來源 ZIP 與 APK 成對交付。
同版 APK 與完整修改來源以 Release 附件成對提供，核對資料見 `RELEASE_ARTIFACTS.json`。
