package com.ai.assistance.operit.core.tools.system

import com.ai.assistance.operit.core.tools.system.shell.ShellCommandDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellCommandDiagnosticsTest {

    @Test
    fun descriptorDoesNotExposeCommandArguments() {
        val secretCommand = "settings put secure token 'user-secret-value'"

        val descriptor = ShellCommandDiagnostics.describe(secretCommand)

        assertFalse(descriptor.contains(secretCommand))
        assertFalse(descriptor.contains("user-secret-value"))
        assertTrue(descriptor.contains("chars=${secretCommand.length}"))
        assertTrue(descriptor.contains("digest="))
    }

    @Test
    fun descriptorIsStableAndMarksShellSyntax() {
        val command = "echo safe | grep safe"

        assertEquals(
            ShellCommandDiagnostics.describe(command),
            ShellCommandDiagnostics.describe(command),
        )
        assertTrue(ShellCommandDiagnostics.describe(command).contains("shellOperators=true"))
    }
}
