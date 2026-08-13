package com.ai.assistance.operit.ui.features.settings.sections

import com.ai.assistance.operit.data.model.ApiProviderType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelApiSettingsLogPrivacyTest {
    @Test
    fun `settings save log contains only non-sensitive configuration state`() {
        val log =
            ModelApiProviderPresentationPolicy.formatSettingsSaveLog(
                provider = ApiProviderType.OPENAI_RESPONSES_GENERIC,
                modelCount = 3,
                credentialConfigured = true,
            )

        assertEquals(
            "保存API设置: provider_type=OPENAI_RESPONSES_GENERIC, model_count=3, " +
                "credential_configured=true, endpoint_kind=compatible",
            log,
        )
        assertFalse(log.contains("apiKey", ignoreCase = true))
        assertFalse(log.contains("endpoint=", ignoreCase = true))
        assertFalse(log.contains("model=", ignoreCase = true))
    }

    @Test
    fun `settings save owner does not format credentials endpoint or model text`() {
        val section =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/" +
                    "ModelApiSettingsSection.kt"
            ).readText()
        val flushBlock =
            section
                .substringAfter("suspend fun flushSettings(")
                .substringBefore("RegisterModelConfigSaveAction(")

        assertFalse(flushBlock.contains("apiKey.take"))
        assertFalse(flushBlock.contains("state.apiEndpoint"))
        assertFalse(flushBlock.contains("state.modelName}"))
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
