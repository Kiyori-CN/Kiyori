package com.ai.assistance.operit.core.tools.system.shell

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Produces a bounded command description for diagnostics without copying command arguments into
 * logs. Shell commands can contain passwords, tokens, file contents, or user-provided payloads;
 * a short digest is sufficient to correlate one execution across log lines without exposing them.
 */
internal object ShellCommandDiagnostics {

    fun describe(command: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(command.toByteArray(StandardCharsets.UTF_8))
                .take(8)
                .joinToString(separator = "") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
        val hasShellOperators = command.any { character ->
            character == '|' || character == '&' || character == ';' ||
                character == '<' || character == '>'
        }
        return "chars=${command.length}, shellOperators=$hasShellOperators, digest=$digest"
    }
}
