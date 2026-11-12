# ARM64 建置步驟

[English](BUILD.md)

請使用全新解壓縮的對應來源。`rebuild-native.ps1` 以 Android CMake 重建 FreeRDP 3.33.1、WinPR、Android JNI、OpenSSL 4.0.3、cJSON 及 uriparser。打包前會移除九個原生函式庫的除錯符號。SQLCipher 4.17.0 由 Maven AAR 提供。

使用 Windows x64、Android SDK API 37／Build Tools 37.0.0、NDK 29.0.13113456、CMake 4.1.2、Ninja、JDK 21.0.12.1+1、Python 3，以及 MSYS2 的 Bash、Perl、GNU make 和 pkg-config。離線打包前需備妥 Gradle 9.6.1、AGP 9.2.1 及來源宣告的 Java 相依套件。

下載 [建置輸入清單](native-rebuild-inputs.json) 中的三份相依來源封存，保留原檔名並放在同一資料夾。腳本會核對 SHA-256，再填入 CMake 的相依來源快取。從儲存庫建置時，將 `-SourceRoot` 指向 `source/`。

```powershell
./rebuild-native.ps1 -SourceRoot <extracted-source> -AndroidSdk <sdk> `
  -JavaHome <jdk> -MsysRoot <msys> -MsysMake <make.exe> `
  -Python3 <python.exe> -ArchivesDirectory <verified-archives>
```

加入 `-NativeOnly` 時，腳本會在 `.source-offer-rebuild/native/arm64-v8a/` 產出原生函式庫後停止。`-Jobs` 設定平行工作數，預設為 2。腳本需要全新的建置目錄。

打包結果為未簽署 APK。`native-build-receipt.json` 記錄發行版原生建置的來源和輸出雜湊。重建時可比對 ABI、執行行為和授權輸入。工具鏈與封裝時間戳可能使重建檔案的雜湊不同。
