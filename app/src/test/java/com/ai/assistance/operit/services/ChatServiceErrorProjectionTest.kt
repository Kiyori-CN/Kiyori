package com.ai.assistance.operit.services

import com.ai.assistance.operit.ui.features.chat.viewmodel.UiStateDelegate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatServiceErrorProjectionTest {
    @Test
    fun `shared ui state exposes and clears message processing errors`() {
        val uiStateDelegate = UiStateDelegate()
        val error = "发送消息失败: upstream 502"

        uiStateDelegate.showErrorMessage(error)

        assertEquals(error, uiStateDelegate.errorMessage.value)

        uiStateDelegate.clearError()

        assertNull(uiStateDelegate.errorMessage.value)
    }

    @Test
    fun `message processing failure is projected through the main error dialog chain`() {
        val core =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt"
            ).readText()
        val viewModel =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/ChatViewModel.kt"
            ).readText()
        val screen =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/screens/AIChatScreen.kt"
            ).readText()

        val messageErrorCallback =
            core
                .substringAfter("showErrorMessage = { error ->")
                .substringBefore("updateChatTitle =")

        assertTrue(messageErrorCallback.contains("uiStateDelegate.showErrorMessage(error)"))
        assertTrue(viewModel.contains("uiStateDelegate = mainChatCore.getUiStateDelegate()"))
        assertTrue(viewModel.contains("val errorMessage: StateFlow<String?>"))
        assertTrue(screen.contains("actualViewModel.errorMessage.collectAsState()"))
        assertTrue(screen.contains("ErrorDialog(errorMessage = message"))
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
