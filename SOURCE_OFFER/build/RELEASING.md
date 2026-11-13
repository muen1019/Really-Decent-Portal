# Android release verification and publication

1. Build the APK and matching source ZIP locally. Sign the APK with the existing release key.
2. Commit the matching source, `RELEASE_ARTIFACTS.json`, and `SHA256SUMS.txt`. Push a `vX.Y.Z` tag pointing to that commit. Create a draft release for the tag and upload the four artifacts with the release notes.
3. Run `run-release-acceptance.ps1` from Windows with `-Publish`. It upgrades the app in place, checks the launcher icon and saved connection, then sends screen touchpad gestures to a temporary receiver in the remote Windows session. GitHub Actions verifies and publishes the draft using the hash of the tested APK. Omit `-Publish` to run the checks locally.

Use PowerShell 7.4+, ADB, JDK 17 or newer, and Android SDK build tools. For publishing, also sign in to the GitHub CLI. Unlock the Samsung tablet and pin the app to its home screen or taskbar. The test Windows PC needs working SSH under the same user as the saved RDP connection, with permission to register an interactive scheduled task for that user. The probe runs in a temporary window, reports event counts and closes when the test ends. Keep the tablet and remote desktop idle during the run.

```powershell
./SOURCE_OFFER/build/run-release-acceptance.ps1 -Serial $serial -Artifacts $artifacts `
  -SavedProfile $profile -ProbeHost $sshAlias -JavaHome $jdk -AndroidJar $androidJar `
  -D8Jar $d8Jar -EvidenceDirectory $results -Publish
```

For a retry after installation, pass the earlier `tablet-upgrade.json` via `-ResumeFrom`. The script checks the same installation and reruns the UI and input tests without installing again. The GitHub workflow remains available for read-only audits of existing releases.

Run the controller regression on a PC before packaging. From `source/client/Android/Studio` on Windows with the Android SDK and JDK 21 configured:

```powershell
$init = Join-Path ([IO.Path]::GetTempPath()) ('rdp-robolectric-' + [guid]::NewGuid() + '.gradle')
try {
    "allprojects { tasks.withType(Test).configureEach { jvmArgs '--add-exports=java.base/jdk.internal.access=ALL-UNNAMED' } }" |
        Set-Content -LiteralPath $init
    .\gradlew.bat -I $init :freeRDPCore:testDebugUnitTest --tests '*.ScreenInputControllerTest'
} finally {
    Remove-Item -LiteralPath $init -ErrorAction SilentlyContinue
}
```

An x86_64 Android emulator can exercise the same source against the Windows receiver with an x86_64 test APK. The current release script checks the signed ARM64 APK and uses One UI controls to check the launcher.

To find the draft release ID, run:

```sh
gh release view v0.1.8 --repo muen1019/Really-Decent-Portal --json databaseId --jq .databaseId
```

The workflow checks the release certificate, APK version, SHA-256, source ZIP against the tagged source, and the ZIP and tar.gz that GitHub generates. It keeps the selected prerelease status. The signing key and the tablet remain local.

For an offline artifact check from the repository root, run:

```text
python SOURCE_OFFER/build/verify-release.py --tag v0.1.8 --commit <full-commit-sha> --tested-apk-sha256 <sha256> --build-tools <android-sdk-build-tools-dir> --assets <directory-with-four-attachments>
```

See [local build instructions](BUILD.md) and [GitHub's workflow dispatch documentation](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

# Android 發行驗證與公開

1. 在本機建置 APK 與配對的來源 ZIP，使用既有發行金鑰簽署 APK。
2. 提交配對來源、`RELEASE_ARTIFACTS.json` 與 `SHA256SUMS.txt`，推送對應的 `vX.Y.Z` 標籤。建立 Release 草稿，填入發行說明並上傳四份附件。
3. 在 Windows 執行上方的 `run-release-acceptance.ps1` 範例。加上 `-Publish` 時，只有原地升級、圖示、既有連線和遠端收到點擊、拖曳放開及雙軸捲動都通過，命令才會以實測 APK 的 SHA-256 觸發 GitHub Actions 公開草稿。

本機需有 PowerShell 7.4 以上、ADB、JDK 17 以上和 Android SDK 工具。發布時也需登入 GitHub CLI。先解鎖 Samsung 平板，並將 App 釘選在桌面或工作列。Windows 測試主機的 SSH 帳號要與測試用 RDP 帳號相同，且能為該帳號建立互動式排程工作。執行期間請讓平板與遠端桌面保持閒置。已升級後可用 `-ResumeFrom` 指向前次的 `tablet-upgrade.json` 重跑。

電腦可先跑手勢辨識回歸。x86_64 Android 模擬器搭配同源測試 APK，也能用 Windows 接收器驗證 RDP 傳輸。上述發行命令使用 Samsung One UI 驗證簽署的 ARM64 APK 和實際桌面圖示。

Release ID 可用上方的 `gh release view` 指令取得。流程會核對正式簽章、APK 版本與 SHA-256、附件來源 ZIP，以及 GitHub 自動產生的 ZIP 和 tar.gz。它保留草稿設定的預發行狀態。金鑰與實體平板留在本機。

需要離線核對時，在儲存庫根目錄使用上方的 `verify-release.py` 指令。 [本機建置說明](BUILD.zh-Hant.md) · [GitHub 手動執行流程說明](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)
