package com.ai.assistance.operit.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriModalBottomDrawerContractTest {
    @Test
    fun modalHostOwnsBackAndWaitsForTheSharedDrawerToBecomeHidden() {
        val source =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/components/KiyoriModalBottomDrawer.kt"
            ).readText()

        assertTrue(source.contains("KiyoriDraggableBottomDrawer("))
        assertTrue(source.contains("Dialog("))
        assertTrue(source.contains("usePlatformDefaultWidth = false"))
        assertTrue(source.contains("dismissOnBackPress = false"))
        assertTrue(source.contains("dismissOnClickOutside = false"))
        assertTrue(source.contains("BackHandler(enabled = isVisible"))
        assertTrue(source.contains("onDismissRequest = dismissDrawer"))
        assertTrue(source.contains("onHidden = onDismissRequest"))
        assertTrue(source.contains("resolveKiyoriBottomDrawerPartialFraction("))
        assertFalse(source.contains("ModalBottomSheet("))
        assertFalse(source.contains("rememberModalBottomSheetState"))
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
