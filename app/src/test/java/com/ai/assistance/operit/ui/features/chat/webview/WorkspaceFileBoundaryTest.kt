package com.ai.assistance.operit.ui.features.chat.webview

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFileBoundaryTest {
    private val root = File(System.getProperty("java.io.tmpdir"), "kiyori-root")

    @Test fun acceptsRootAndNestedFiles() {
        assertTrue(isFileWithinWorkspace(root, root))
        assertTrue(isFileWithinWorkspace(root, File(root, "assets/index.html")))
    }

    @Test fun rejectsSiblingWithMatchingNamePrefixAndParentTraversal() {
        assertFalse(isFileWithinWorkspace(root, File(root.parentFile, "kiyori-root-private/secret.txt")))
        assertFalse(isFileWithinWorkspace(root, File(root, "../kiyori-root-private/secret.txt")))
        assertFalse(isFileWithinWorkspace(root, File(root, "../secret.txt")))
    }
}
