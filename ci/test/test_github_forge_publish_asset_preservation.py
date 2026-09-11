from __future__ import annotations

import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_PATH = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "ai"
    / "assistance"
    / "operit"
    / "ui"
    / "features"
    / "packages"
    / "market"
    / "GitHubForgePublishService.kt"
)


class GitHubForgePublishAssetPreservationTest(unittest.TestCase):
    def test_market_registration_failure_preserves_uploaded_release_asset(self) -> None:
        source = SOURCE_PATH.read_text(encoding="utf-8")
        registration_start = source.index("            val entry =")
        registration_end = source.index(
            "            onProgress(PublishProgressStage.COMPLETED)",
            registration_start,
        )
        registration_block = source[registration_start:registration_end]

        self.assertIn("PublishAttemptResult.RegistrationFailed", registration_block)
        self.assertNotIn("rollbackFailedMarketRegistration", source)
        self.assertNotIn("githubApiService.deleteRelease(", registration_block)
        self.assertNotIn("githubApiService.deleteReleaseAsset(", registration_block)

    def test_same_version_asset_is_verified_and_never_deleted_for_retry(self) -> None:
        source = SOURCE_PATH.read_text(encoding="utf-8")
        replacement_start = source.index(
            "    internal suspend fun ensureReleaseAsset("
        )
        replacement_end = source.index(
            "    private suspend fun registerMarketEntry(",
            replacement_start,
        )
        replacement_block = source[replacement_start:replacement_end]

        # 同版本内容冲突必须保留既有发布物；JVM 行为测试另核对实际 API 调用。
        self.assertIn("githubApiService.downloadReleaseAsset(", replacement_block)
        self.assertIn("existingBytes.contentEquals(content)", replacement_block)
        self.assertIn("return Result.success(existingAsset)", replacement_block)
        self.assertNotIn("githubApiService.deleteReleaseAsset(", replacement_block)
        self.assertNotIn("githubApiService.deleteRelease(", replacement_block)


if __name__ == "__main__":
    unittest.main()
