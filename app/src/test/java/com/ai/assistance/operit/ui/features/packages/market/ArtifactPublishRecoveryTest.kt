package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.api.GitHubReleaseAsset
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class ArtifactPublishRecoveryTest {
    private val asset = GitHubReleaseAsset(2, "artifact.js", "https://example.test/artifact.js", 3, 0, "text/javascript")
    private val release = GitHubRelease(1, "v1", "v1", "", "https://example.test/v1", created_at = "", assets = listOf(asset))

    @Test fun `existing identical release asset is reused without deleting or uploading`() = runTest {
        val github = mock<GitHubApiService>()
        whenever(github.downloadReleaseAsset(asset.browser_download_url)).thenReturn(Result.success(byteArrayOf(1, 2, 3)))
        val descriptor = mock<PublishArtifactDescriptor> { on { assetName } doReturn asset.name }
        val service = GitHubForgePublishService(mock(), github, mock(), mock())
        assertEquals(asset, service.ensureReleaseAsset("owner", "repo", release, descriptor, byteArrayOf(1, 2, 3)).getOrThrow())
        verify(github).downloadReleaseAsset(asset.browser_download_url)
        verifyNoMoreInteractions(github)
    }

    @Test fun `different existing release asset is preserved and the new upload is refused`() = runTest {
        val github = mock<GitHubApiService>()
        whenever(github.downloadReleaseAsset(asset.browser_download_url)).thenReturn(Result.success(byteArrayOf(1, 2, 3)))
        val descriptor = mock<PublishArtifactDescriptor> { on { assetName } doReturn asset.name }
        val service = GitHubForgePublishService(mock(), github, mock(), mock())
        assertTrue(service.ensureReleaseAsset("owner", "repo", release, descriptor, byteArrayOf(4)).isFailure)
        verify(github).downloadReleaseAsset(asset.browser_download_url)
        verifyNoMoreInteractions(github)
    }

    @Test fun `registration recovery only sends the original market payload and rejects another account`() = runTest {
        val github = mock<GitHubApiService>()
        val market = mock<MarketStatsApiService>()
        val auth = mock<GitHubAuthPreferences>()
        whenever(auth.isLoggedIn()).thenReturn(true)
        whenever(auth.getCurrentUserInfo()).thenReturn(GitHubUser(1, "publisher", avatarUrl = ""))
        val entry = mock<MarketV2Entry>()
        whenever(market.publish(any())).thenReturn(Result.success(entry))
        val payload = MarketRegistrationPayload(
            type = PublishArtifactType.SCRIPT, projectId = "project", projectDisplayName = "Name",
            projectDescription = "Detail", runtimePackageId = "script", publisherLogin = "publisher",
            releaseOwner = "publisher", releaseRepository = "repo", releaseTag = "v1", assetName = asset.name,
            downloadUrl = asset.browser_download_url, sha256 = "a".repeat(64), version = "1.0.0",
            displayName = "Name", description = "Description", detail = "Detail", categoryId = "tools",
            sourceFileName = asset.name, minSupportedAppVersion = "1.12.1+3", maxSupportedAppVersion = "1.99.99"
        )
        val failed = PublishAttemptResult.RegistrationFailed("rejected", payload, null, null, retryAllowed = true)
        val service = GitHubForgePublishService(mock(), github, market, auth)
        assertEquals(entry, service.retryMarketRegistration(failed).getOrThrow())
        verify(market).publish(argThat { title == "Name" && asset?.url == payload.downloadUrl && asset.sha256 == payload.sha256 })
        verifyNoInteractions(github)
        whenever(auth.getCurrentUserInfo()).thenReturn(GitHubUser(2, "other", avatarUrl = ""))
        assertTrue(service.retryMarketRegistration(failed).isFailure)
        assertTrue(service.retryMarketRegistration(failed.copy(retryAllowed = false)).isFailure)
        verifyNoMoreInteractions(market)
    }
}
