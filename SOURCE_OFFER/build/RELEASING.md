# Android release verification and publication

1. Build the APK and matching source ZIP locally. Sign the APK with the existing release key, upgrade the tablet in place, and record the SHA-256 of the signed APK you tested.
2. Commit the matching source, `RELEASE_ARTIFACTS.json`, and `SHA256SUMS.txt`. Push a `vX.Y.Z` tag pointing to that commit. Create a draft release for the existing tag, add the release notes, and upload the four artifacts.
3. In GitHub Actions, run **Verify Android release** on `main`. Enter the tag, the full commit SHA, the Release database ID, and the tested APK SHA-256. Enable **Publish the draft after verification** to publish. Leave it disabled to audit an already published release with read-only permissions.

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

1. 在本機建置 APK 與配對的來源 ZIP。用既有發行金鑰簽署，在平板原地升級驗收，記下實際驗收 APK 的 SHA-256。
2. 提交配對來源、`RELEASE_ARTIFACTS.json` 與 `SHA256SUMS.txt`，將指向該提交的 `vX.Y.Z` 標籤推上 GitHub。用該標籤建立 Release 草稿，填入發行說明並上傳四份附件。
3. 在 GitHub Actions 對 `main` 執行 **Verify Android release**。填入標籤、完整來源提交 SHA、Release 資料庫 ID 及平板驗收的 APK SHA-256。勾選 **Publish the draft after verification** 會在全部核對通過後公開草稿。已公開的版本可用預設的唯讀模式重查。

Release ID 可用上方的 `gh release view` 指令取得。流程會核對正式簽章、APK 版本與 SHA-256、附件來源 ZIP，以及 GitHub 自動產生的 ZIP 和 tar.gz。它保留草稿設定的預發行狀態。金鑰與實體平板留在本機。

需要離線核對時，在儲存庫根目錄使用上方的 `verify-release.py` 指令。 [本機建置說明](BUILD.zh-Hant.md) · [GitHub 手動執行流程說明](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)
