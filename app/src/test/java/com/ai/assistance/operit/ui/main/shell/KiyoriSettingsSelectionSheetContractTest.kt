package com.ai.assistance.operit.ui.main.shell

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriSettingsSelectionSheetContractTest {
    @Test
    fun selectionOptionsUseASeparateScrollableRegion() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriSettingsUi.kt"
            ).readText()

        assertTrue(source.contains("Column(modifier = Modifier.fillMaxWidth().fillMaxHeight())"))
        assertTrue(source.contains("LazyColumn("))
        assertTrue(source.contains("Modifier.fillMaxWidth().weight(1f)"))
        assertTrue(source.contains("itemsIndexed(visibleOptions)"))
    }

    @Test
    fun selectionSheetOwnsItsSettingsThemeBoundary() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriSettingsUi.kt"
            ).readText()

        assertTrue(source.contains("KiyoriSettingsTheme {"))
        assertTrue(source.contains("KiyoriSettingsSelectionSheetContent("))
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
