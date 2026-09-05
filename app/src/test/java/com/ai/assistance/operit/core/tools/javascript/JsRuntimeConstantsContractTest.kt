package com.ai.assistance.operit.core.tools.javascript

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class JsRuntimeConstantsContractTest {
    @Test
    fun `Kiyori paths and imported script aliases share correctly escaped values`() {
        val download = "/sdcard/Download/Kiyori/\"quoted\"/\\path"
        val cleanup = "$download/clean_on_exit\nline"
        val modules = buildInitRuntimeModules(download, cleanup)
        val script = modules.single { it.fileName == "quickjs/init/runtime-constants.js" }.source
        val exports = Regex("expose\\('([A-Z_]+)', (.+)\\);")
            .findAll(script)
            .associate { match -> match.groupValues[1] to JSONArray("[${match.groupValues[2]}]").getString(0) }

        assertEquals(
            mapOf(
                "KIYORI_DOWNLOAD_DIR" to download,
                "KIYORI_CLEAN_ON_EXIT_DIR" to cleanup,
                "OPERIT_DOWNLOAD_DIR" to download,
                "OPERIT_CLEAN_ON_EXIT_DIR" to cleanup,
            ),
            exports,
        )
        assertEquals("quickjs/init/runtime-expose.js", modules.first().fileName)
    }
}
