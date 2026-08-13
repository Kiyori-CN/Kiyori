package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPkgRegistrationObservationPolicyTest {
    @Test
    fun `openai web search registration proves version schema digest source thread and time`() {
        val digest = "ab".repeat(32)
        val log =
            ToolPkgRegistrationObservationPolicy.format(
                toolPkgId = OpenAIHostedWebSearchContract.TOOLPKG_ID,
                version = OpenAIHostedWebSearchContract.TOOLPKG_VERSION,
                artifactSha256 = digest,
                sourceKind = "asset",
                registrationThread = "DefaultDispatcher-worker-3",
                elapsedMs = 417L,
                responseSchemaRevision =
                    OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
            )

        assertTrue(log.contains("toolpkg_id=com.kiyori.openai_web_search"))
        assertTrue(log.contains("version=1.0.6"))
        assertTrue(log.contains("artifact_sha256=$digest"))
        assertTrue(log.contains("source_kind=asset"))
        assertTrue(log.contains("registration_thread=DefaultDispatcher-worker-3"))
        assertTrue(log.contains("elapsed_ms=417"))
        assertTrue(log.contains("response_schema_revision=7"))
    }

    @Test
    fun `registration log never accepts or emits a private source path`() {
        val privatePath = "C:\\Users\\private\\plugins\\secret.toolpkg"
        val log =
            ToolPkgRegistrationObservationPolicy.format(
                toolPkgId = "example.toolpkg",
                version = "2.0.0",
                artifactSha256 = "cd".repeat(32),
                sourceKind = "external",
                registrationThread = "worker path=$privatePath",
                elapsedMs = 1L,
                responseSchemaRevision = null,
            )

        assertFalse(log.contains(privatePath))
        assertFalse(log.contains("\\"))
        assertFalse(log.contains("response_schema_revision"))
    }
}
