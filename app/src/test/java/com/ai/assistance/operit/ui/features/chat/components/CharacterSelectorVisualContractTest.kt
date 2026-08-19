package com.ai.assistance.operit.ui.features.chat.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSelectorVisualContractTest {
    @Test
    fun `character selector uses neutral and blue cards without pink styling`() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/" +
                    "CharacterSelectorPanel.kt",
            ).readText()

        assertFalse(source.contains("KiyoriSemanticTone.PINK"))
        assertFalse(source.contains("KiyoriSemanticTone.PURPLE"))
        assertTrue(source.contains("KiyoriSemanticTone.BLUE.resolveColors()"))
        assertTrue(source.contains("MaterialTheme.colorScheme.surface"))
        assertTrue(source.contains("MaterialTheme.colorScheme.outlineVariant"))
    }

    @Test
    fun `character sort menu keeps rounded corners without elevation shadow`() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/" +
                    "CharacterSelectorPanel.kt",
            ).readText()
        val sortMenu =
            source
                .substringAfter("expanded = sortMenuExpanded")
                .substringBefore("DropdownMenuItem(")

        assertTrue(sortMenu.contains("shape = RoundedCornerShape(12.dp)"))
        assertTrue(sortMenu.contains("containerColor = MaterialTheme.colorScheme.surface"))
        assertTrue(sortMenu.contains("tonalElevation = 0.dp"))
        assertTrue(sortMenu.contains("shadowElevation = 0.dp"))
        assertTrue(sortMenu.contains("width = 0.5.dp"))
        assertTrue(sortMenu.contains("color = MaterialTheme.colorScheme.outlineVariant"))
        assertFalse(sortMenu.contains(".shadow("))
    }

    @Test
    fun `collapsed character button uses blue default avatar and preserves custom image`() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/" +
                    "ChatHeader.kt",
            ).readText()
        val characterSwitcher =
            source
                .substringAfter("// Character Switcher")
                .substringBefore("Text(")

        assertFalse(source.contains("KiyoriSemanticTone.PINK"))
        assertTrue(source.contains("KiyoriSemanticTone.BLUE.resolveColors()"))
        assertTrue(characterSwitcher.contains("activeCharacterAvatarUri == null"))
        assertTrue(characterSwitcher.contains("characterColors.container"))
        assertTrue(characterSwitcher.contains("Color.Transparent"))
        assertTrue(characterSwitcher.contains("rememberAsyncImagePainter"))
    }

    @Test
    fun `AI drawer quick row is extensions toolbox workflow and hides permission`() {
        val registry =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/main/screens/" +
                    "ScreenRouteRegistry.kt",
            ).readText()
        val drawer =
            repositoryFile(
                "app/src/main/java/com/kiyori/app/shell/KiyoriAiDrawer.kt",
            ).readText()

        val packageEntry =
            registry.substringAfter("entryId = \"main.packages\"").substringBefore("),")
        val permissionEntry =
            registry.substringAfter("entryId = \"main.shizuku_commands\"").substringBefore("),")
        val workflowEntry =
            registry.substringAfter("entryId = \"main.workflow\"").substringBefore("),")
        val toolboxEntry =
            registry.substringAfter("entryId = \"main.toolbox\"").substringBefore("),")

        assertTrue(packageEntry.contains("surface = NavigationSurface.MAIN_SIDEBAR_TOOLS"))
        assertFalse(permissionEntry.contains("surface = NavigationSurface.MAIN_SIDEBAR_TOOLS"))
        assertTrue(toolboxEntry.contains("surface = NavigationSurface.MAIN_SIDEBAR_TOOLS"))
        assertTrue(toolboxEntry.contains("order = 20"))
        assertTrue(workflowEntry.contains("surface = NavigationSurface.MAIN_SIDEBAR_TOOLS"))
        assertTrue(drawer.contains("\"main.toolbox\" -> toolboxEntryCount.toString()"))
        assertTrue(drawer.contains("entry.surface == NavigationSurface.TOOLBOX"))
        assertFalse(drawer.contains("\"main.shizuku_commands\""))
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
