package com.ai.assistance.operit.ui.features.packages.market

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarketDownloadFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `external names cannot escape cache or overwrite another download`() {
        val first = prepareMarketDownload(temporary.root, "../../outside.js") { it.writeText("one") }
        val second = prepareMarketDownload(temporary.root, "../../outside.js") { it.writeText("two") }
        assertEquals(temporary.root.canonicalFile, first.canonicalFile.parentFile)
        assertNotEquals(first, second)
        assertEquals("js", first.extension)
        assertEquals("one", first.readText())
        assertEquals("two", second.readText())
    }

    @Test fun `failed and cancelled downloads clean partial files and propagate original error`() {
        listOf(IOException("network failed"), CancellationException("cancelled")).forEach { failure ->
            try {
                prepareMarketDownload(temporary.root, "artifact.toolpkg") {
                    it.writeText("partial")
                    throw failure
                }
                fail("Failure was swallowed")
            } catch (actual: Exception) { assertSame(failure, actual) }
            assertTrue(temporary.root.list()!!.isEmpty())
        }
    }

    @Test fun `empty downloads and unsupported formats never leave an artifact`() {
        try { prepareMarketDownload(temporary.root, "artifact.ts") {}; fail("Empty file accepted") }
        catch (_: IllegalStateException) { }
        try { prepareMarketDownload(temporary.root, "artifact.exe") { fail("Unsupported download started") }; fail("Unsupported format accepted") }
        catch (_: IllegalArgumentException) { }
        assertTrue(temporary.root.list()!!.isEmpty())
    }
}
