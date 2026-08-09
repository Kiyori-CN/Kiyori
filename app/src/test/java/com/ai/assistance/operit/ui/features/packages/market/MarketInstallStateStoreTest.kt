package com.ai.assistance.operit.ui.features.packages.market

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketInstallStateStoreTest {
    @Test
    fun `artifact catalog revision increments without changing install progress`() {
        val revisionBefore = MarketInstallStateStore.artifactCatalogRevision.value
        val installStatesBefore = MarketInstallStateStore.installStates.value

        MarketInstallStateStore.notifyArtifactCatalogChanged()

        assertEquals(revisionBefore + 1L, MarketInstallStateStore.artifactCatalogRevision.value)
        assertEquals(installStatesBefore, MarketInstallStateStore.installStates.value)
    }
}
