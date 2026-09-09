package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2Version
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarketInstallMarkerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `updating a marker publishes the complete new version without staging leftovers`() {
        val root = temporary.newFolder("skill")
        writeMarketInstallMarker(root, MarketV2Entry(id = "entry", latestVersion = MarketV2Version(id = "old")))
        writeMarketInstallMarker(root, MarketV2Entry(id = "entry", latestVersion = MarketV2Version(id = "新版本")))
        assertEquals(MarketInstallMarker("entry", "新版本"), readMarketInstallMarker(root))
        assertEquals(1, root.walkTopDown().filter { it.isFile }.count())
    }

    @Test fun `invalid incoming identity keeps the existing installation marker`() {
        val root = temporary.newFolder("skill")
        writeMarketInstallMarker(root, MarketV2Entry(id = "entry", latestVersion = MarketV2Version(id = "old")))
        assertThrows(IllegalStateException::class.java) {
            writeMarketInstallMarker(root, MarketV2Entry(id = "entry"))
        }
        assertEquals(MarketInstallMarker("entry", "old"), readMarketInstallMarker(root))
    }
}
