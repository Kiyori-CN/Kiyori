package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BrowserExtensionBootstrapTest {
    @Test fun productionBootstrapCompletesAnAgentLifecycleInJavaScript() {
        val code = """
            const marker = document.createElement('div'); document.body.appendChild(marker);
            kiyori.onCleanup(() => marker.remove());
            kiyori.onAction(() => { marker.hidden = !marker.hidden; });
        """.trimIndent()
        val bundle = extensionFixture(code = code).let { base -> base.copy(
            manifest = base.manifest.copy(content_scripts = listOf(base.manifest.content_scripts.single().copy(css = listOf("main.css"))),
                action = BrowserExtensionAction("Toggle")),
            files = base.files + ("main.css" to "div { color: blue; }"),
        ) }
        val script = sequenceOf(File("tools/example_packages/browser_extension_runtime.test.mjs"),
            File("../tools/example_packages/browser_extension_runtime.test.mjs")).first { it.isFile }
        val process = ProcessBuilder("node", script.canonicalPath).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(BrowserExtensionBootstrap.source(bundle, "__testBridge")) }
        val ended = process.waitFor(20, TimeUnit.SECONDS)
        if (!ended) process.destroyForcibly()
        assertTrue("JavaScript lifecycle simulation timed out", ended)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.exitValue())
        assertTrue(output, output.contains("PASS:"))
    }
}
