package com.ai.assistance.operit.ui.features.chat.webview.workspace

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WorkspacePreviewPathTest {
    @Test fun virtualPathsNormalizeBeforeTestingWorkspaceBoundary() {
        assertNull(resolveVirtualWorkspaceRelativePath("/project/../secret", "/project"))
        assertEquals("/file", resolveVirtualWorkspaceRelativePath("/project/sub/../file", "/project"))
        assertEquals("/project/file", resolveVirtualWorkspaceRelativePath("/project/file", "/"))
    }

    @Test fun localFilesystemRootIsAValidPreviewRoot() {
        val root = Files.createTempDirectory("preview-root").toFile()
        try {
            assertEquals("/index.html", resolveLocalWorkspaceRelativePath(root.resolve("index.html").path, root.path))
            assertNotNull(resolveLocalWorkspaceRelativePath(root.path, root.toPath().root.toString()))
            assertNull(resolveLocalWorkspaceRelativePath(root.resolve("../sibling").path, root.path))
        } finally { root.deleteRecursively() }
    }
}
