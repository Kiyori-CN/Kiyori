package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAdBlockStartupContractTest {
    @Test
    fun `store construction never parses subscription payloads on the caller thread`() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserAdBlockStore.kt",
            ).readText()

        assertFalse(source.contains("private val initialSnapshot = readInitialSnapshot()"))
        assertTrue(source.contains("private var matcher = BrowserAdBlockMatcher.EMPTY"))
        assertTrue(source.contains("init {"))
        assertTrue(source.contains("ioScope.launch {"))
        assertTrue(source.contains("initializeRuntime()"))
        assertTrue(source.contains("phase = BrowserAdBlockRuntimePhase.LOADING_COMPILED_RULES"))
        assertTrue(source.contains("phase = BrowserAdBlockRuntimePhase.COMPILING_RULES"))
        assertTrue(source.contains("BrowserAdBlockCompiledCacheCodec.read("))
        assertTrue(source.contains("cacheResult.fold("))
        assertTrue(source.contains("onSuccess = { partition ->"))
        assertTrue(source.contains("compiledSubscriptionCount += 1"))
        assertTrue(source.contains("matcher = compileMatcher(revisionedState, compiledEngine)"))
        assertTrue(source.contains("private var customRuntimeEngine"))
        assertTrue(source.contains("private var subscriptionRuntimeEngines"))
        assertTrue(source.contains("private var subscriptionRuntimeEngine"))
        assertTrue(source.contains("put(subscription.id, partition.engine)"))
        assertTrue(source.contains("if (_runtimeStatus.value.phase == BrowserAdBlockRuntimePhase.READY)"))
        assertTrue(source.contains("combineRuntimeEngines("))
        assertTrue(source.contains("compileBrowserAdBlockSubscription("))
        assertTrue(source.contains(".bufferedReader(StandardCharsets.UTF_8)"))
        assertFalse(source.contains("parseBrowserAdBlockSubscription("))
        assertFalse(source.contains("bytes.toString(StandardCharsets.UTF_8)"))
        assertFalse(source.contains("payload.toString(StandardCharsets.UTF_8)"))
        assertFalse(source.contains("OutOfMemoryError"))
    }

    @Test
    fun `subscription compiler avoids split domain lists and retained set partitions`() {
        val policySource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserAdBlockPolicy.kt",
            ).readText()
        val cacheSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserAdBlockCompiledCache.kt",
            ).readText()

        assertTrue(policySource.contains("class BrowserAdBlockDomainInterner"))
        assertTrue(policySource.contains("forEachBrowserAdBlockDelimitedSegment"))
        assertTrue(policySource.contains("val domainIncludes: List<String>"))
        assertTrue(policySource.contains("val domainExcludes: List<String>"))
        assertTrue(policySource.contains("val denyAllowDomains: List<String>"))
        assertFalse(policySource.contains("host.split('.')"))
        assertFalse(policySource.contains("domainExpression.split(',')"))
        assertFalse(policySource.contains("value.split('|')"))
        assertTrue(cacheSource.contains("readStringList("))
        assertFalse(cacheSource.contains("readStringSet("))
    }

    @Test
    fun `schema three and refresh paths preserve content identity and unchanged revisions`() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserAdBlockStore.kt",
            ).readText()

        assertTrue(source.contains("private const val BROWSER_AD_BLOCK_SCHEMA_VERSION = 3"))
        assertTrue(source.contains("\"payloadSha256\""))
        assertTrue(source.contains("\"payloadByteCount\""))
        assertTrue(source.contains("\"payloadStorageVersion\""))
        assertTrue(source.contains("subscription.matchesCommittedPayload("))
        assertTrue(source.contains("commitUnchangedSubscriptionRefresh("))
        assertTrue(source.contains("ruleRevision = currentState.ruleRevision + 1L"))
        assertTrue(source.contains("compiledCacheDirectory"))
        assertTrue(source.contains("applicationContext.noBackupFilesDir"))
    }

    @Test
    fun `settings and WebView surfaces consume asynchronous runtime state`() {
        val settingsSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriAdBlockSettingsPage.kt",
            ).readText()
        val webViewSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserWebViewSupport.kt",
            ).readText()
        val networkSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserToolSupport.kt",
            ).readText()

        assertTrue(settingsSource.contains("store.runtimeStatus.collectAsState()"))
        assertTrue(settingsSource.contains("enabled = runtimeStatus.ready"))
        assertTrue(webViewSource.contains("buildBrowserAdBlockElementInjectionPayload"))
        assertTrue(webViewSource.contains("ioScope.launch"))
        assertTrue(webViewSource.contains("data-kiyori-adblock-style"))
        assertTrue(networkSource.contains("scheduleNetworkStateRefresh(session)"))
        assertTrue(networkSource.contains("postDelayed"))
    }

    private fun repositoryFile(relativePath: String): File {
        var current: File? =
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true) {
                return candidate
            }
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
