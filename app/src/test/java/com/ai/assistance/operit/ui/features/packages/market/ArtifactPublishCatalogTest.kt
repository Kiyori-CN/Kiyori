package com.ai.assistance.operit.ui.features.packages.market

import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel.ArtifactMarketViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactPublishCatalogTest {
    @Test fun `pending metadata submission keeps its slot when a stale dialog dismisses and releases on owner clear`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val forge = mock<GitHubForgePublishService>()
            val auth = mock<GitHubAuthPreferences>()
            whenever(auth.isLoggedInFlow).thenReturn(MutableStateFlow(true))
            val packages = mock<PackageManager>()
            whenever(packages.getPublishablePackageSources()).thenReturn(emptyList())
            val market = mock<com.ai.assistance.operit.data.api.MarketStatsApiService>()
            val vm = ArtifactMarketViewModel(mock(), ArtifactMarketScope.ALL, market, auth, forge, packages)
            store.put("vm", vm)
            vm.updatePublishedArtifact(mock(), "Name", "Description", "Detail", "tools", true, null, null)
            assertEquals(PublishProgressStage.VALIDATING, vm.publishProgressStage.value)
            vm.dismissForgeInitializationPrompt()
            assertEquals(PublishProgressStage.VALIDATING, vm.publishProgressStage.value)
            store.clear()
            runCurrent()
            assertEquals(PublishProgressStage.IDLE, vm.publishProgressStage.value)
            verifyNoInteractions(market, forge)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `changing repository clears its assets and cancels an unstarted request`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val forge = mock<GitHubForgePublishService>()
            val auth = mock<GitHubAuthPreferences>()
            whenever(auth.isLoggedInFlow).thenReturn(MutableStateFlow(true))
            val packages = mock<PackageManager>()
            whenever(packages.getPublishablePackageSources()).thenReturn(emptyList())
            val catalog = GitHubReleaseCatalog(GitHubReleaseRepository("owner", "old"), emptyList())
            whenever(forge.loadGitHubReleaseCatalog("old")).thenReturn(Result.success(catalog))
            val vm = ArtifactMarketViewModel(mock(), ArtifactMarketScope.ALL, mock(), auth, forge, packages)
            store.put("vm", vm)
            vm.loadGitHubReleaseCatalog("old")
            runCurrent()
            assertEquals(catalog, vm.githubReleaseCatalog.value)
            vm.loadGitHubReleaseCatalog("queued")
            assertTrue(vm.isLoadingGitHubReleaseCatalog.value)
            vm.clearGitHubReleaseCatalog()
            runCurrent()
            assertNull(vm.githubReleaseCatalog.value)
            assertFalse(vm.isLoadingGitHubReleaseCatalog.value)
            verify(forge, never()).loadGitHubReleaseCatalog("queued")
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `catalog failure clears loading and explicit retry publishes current repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val forge = mock<GitHubForgePublishService>()
            val auth = mock<GitHubAuthPreferences>()
            whenever(auth.isLoggedInFlow).thenReturn(MutableStateFlow(true))
            val packages = mock<PackageManager>()
            whenever(packages.getPublishablePackageSources()).thenReturn(emptyList())
            val catalog = GitHubReleaseCatalog(GitHubReleaseRepository("owner", "new"), emptyList())
            whenever(forge.loadGitHubReleaseCatalog("new")).thenReturn(
                Result.failure(IllegalStateException("offline")), Result.success(catalog)
            )
            val vm = ArtifactMarketViewModel(mock(), ArtifactMarketScope.ALL, mock(), auth, forge, packages)
            store.put("vm", vm)
            vm.loadGitHubReleaseCatalog("new")
            runCurrent()
            assertFalse(vm.isLoadingGitHubReleaseCatalog.value)
            assertEquals("offline", vm.githubReleaseCatalogError.value)
            vm.loadGitHubReleaseCatalog("new")
            runCurrent()
            assertEquals(catalog, vm.githubReleaseCatalog.value)
            assertNull(vm.githubReleaseCatalogError.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
