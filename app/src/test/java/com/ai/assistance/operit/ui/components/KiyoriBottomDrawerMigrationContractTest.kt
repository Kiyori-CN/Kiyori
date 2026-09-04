package com.ai.assistance.operit.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriBottomDrawerMigrationContractTest {
    @Test
    fun productionUiHasOneModalBottomDrawerImplementation() {
        val productionRoot =
            repositoryFile("app/src/main/java").toPath()
        val productionSources =
            productionRoot
                .toFile()
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .toList()

        productionSources.forEach { sourceFile ->
            assertFalse(
                "${sourceFile.path} still uses Material ModalBottomSheet",
                sourceFile.readText().contains("ModalBottomSheet("),
            )
        }

        // The file context menu became a centered Dialog in 5580c23f9. Its own source contract
        // protects that presentation; only actual bottom drawers belong in this consumer list.
        val requiredConsumers =
            listOf(
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ChatArea.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/ModelNameTagEditor.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/UpstreamModelPickerSheet.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/UserPreferencesSettingsScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/ModelApiSettingsSection.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/sqlviewer/SqlViewerScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/browser/WebSessionHistorySheet.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/browser/WebSessionWebElementOverlays.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriSettingsUi.kt",
            )
        requiredConsumers.forEach { relativePath ->
            assertTrue(
                "$relativePath must use the shared modal drawer host",
                repositoryFile(relativePath).readText().contains("KiyoriModalBottomDrawer("),
            )
        }
    }

    private fun repositoryFile(relativePath: String): File {
        var current: File? =
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true || candidate?.isDirectory == true) {
                return candidate
            }
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
