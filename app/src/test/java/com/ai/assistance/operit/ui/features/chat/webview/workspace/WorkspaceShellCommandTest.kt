package com.ai.assistance.operit.ui.features.chat.webview.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceShellCommandTest {
    @Test fun configuredWorkingDirectoryIsQuotedWithoutShellExpansion() {
        assertEquals("cd -- '/project/sub dir' && {\npwd\n}", workspaceShellCommand("/project", "pwd", "sub dir"))
        assertEquals("cd -- '/other' && {\npwd\n}", workspaceShellCommand("/project", "pwd", "/other"))
        assertTrue(runCatching { workspaceShellCommand("/project", "echo x; pwd", shell = false) }.isFailure)
    }

    @Test fun directoryMetacharactersRemainOneLiteralArgument() {
        assertEquals("cd -- '/tmp/a'\"'\"'b ${'$'}(touch marker) `id`' && {\nprintf done\n}",
            workspaceShellCommand("/tmp/a'b ${'$'}(touch marker) `id`", "printf done"))
    }

    @Test fun multilineCodeAndTrailingCommentStayInsideDirectoryGuard() {
        assertEquals("cd -- '/tmp/project' && {\nexport FLAG=1\nprintf done # trailing comment\n}",
            workspaceShellCommand("/tmp/project", "export FLAG=1\nprintf done # trailing comment"))
    }

    @Test fun invalidTargetsAndNulAreRejectedBeforeSubmission() {
        assertTrue(runCatching { workspaceShellCommand("relative", "pwd") }.isFailure)
        assertTrue(runCatching { workspaceShellCommand("/tmp/\u0000", "pwd") }.isFailure)
        assertTrue(runCatching { workspaceShellCommand("/tmp", "pwd\u0000") }.isFailure)
    }
}
