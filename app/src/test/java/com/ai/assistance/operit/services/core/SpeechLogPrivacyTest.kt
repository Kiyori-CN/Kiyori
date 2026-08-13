package com.ai.assistance.operit.services.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeechLogPrivacyTest {
    @Test
    fun `speech runtime owners never format spoken text previews`() {
        val owners =
            listOf(
                "app/src/main/java/com/ai/assistance/operit/services/core/" +
                    "MessageProcessingDelegate.kt",
                "app/src/main/java/com/ai/assistance/operit/api/voice/" +
                    "AccessibilityVoiceProvider.kt",
                "app/src/main/java/com/ai/assistance/operit/api/voice/HttpVoiceProvider.kt",
                "app/src/main/java/com/ai/assistance/operit/api/voice/" +
                    "SiliconFlowVoiceProvider.kt",
                "app/src/main/java/com/ai/assistance/operit/api/voice/VitsVoiceProvider.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/" +
                    "ChatViewModel.kt",
            )

        owners.forEach { relativePath ->
            val source = repositoryFile(relativePath).readText()
            assertFalse("$relativePath retains speechPreview()", source.contains("speechPreview("))
            assertFalse(
                "$relativePath retains a speech preview limit",
                source.contains("SPEECH_PREVIEW_MAX") ||
                    source.contains("AUTO_READ_PREVIEW_MAX"),
            )
            assertFalse(
                "$relativePath retains a preview log field",
                source.contains(" preview=\\\""),
            )
        }
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
