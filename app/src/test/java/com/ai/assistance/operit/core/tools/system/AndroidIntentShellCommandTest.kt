package com.ai.assistance.operit.core.tools.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidIntentShellCommandTest {

    @Test
    fun buildQuotesEveryExternalArgument() {
        val command =
            AndroidIntentShellCommand.build(
                type = "broadcast",
                action = "com.example.ACTION; echo unsafe",
                uri = "https://example.test/a path?q=one&x=two",
                packageName = "com.example.app",
                component = "com.example.app/.Receiver",
                flags = 0x10000000,
                extras =
                    listOf(
                        AndroidIntentShellCommand.stringExtra("message", "it's safe; still one arg"),
                    ),
            )

        assertEquals(
                "am broadcast -a 'com.example.ACTION; echo unsafe' " +
                "-d 'https://example.test/a path?q=one&x=two' " +
                "-p 'com.example.app' -n 'com.example.app/com.example.app.Receiver' " +
                "-f 268435456 --es 'message' 'it'\"'\"'s safe; still one arg'",
            command,
        )
    }

    @Test
    fun componentShortNameIsNormalized() {
        assertEquals(
            "com.example.app/com.example.app.MainActivity",
            AndroidIntentShellCommand.normalizeComponent("com.example.app/.MainActivity"),
        )
    }

    @Test
    fun parseFlagsCombinesArrayValues() {
        assertEquals(3, AndroidIntentShellCommand.parseFlags("[1, 2]"))
        assertEquals(16, AndroidIntentShellCommand.parseFlags("16"))
        assertEquals(16, AndroidIntentShellCommand.parseFlags("0x10"))
    }

    @Test
    fun decimalFlagsAreRejected() {
        val error = runCatching { AndroidIntentShellCommand.parseFlags("1.5") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun parseExtrasPreservesSupportedTypes() {
        val extras =
            AndroidIntentShellCommand.parseExtras(
                "{\"boolean\":true,\"double\":1.5,\"integer\":3,\"long\":922337203685477580,\"text\":\"hello\"}",
            )

        assertEquals(listOf("boolean", "double", "integer", "long", "text"), extras.map { it.key })
        assertEquals(listOf("--ez", "--ed", "--ei", "--el", "--es"), extras.map { it.option })
        assertEquals(listOf("true", "1.5", "3", "922337203685477580", "hello"), extras.map { it.value })
    }

    @Test
    fun unsupportedAndAmbiguousValuesFailExplicitly() {
        val nullError =
            runCatching { AndroidIntentShellCommand.parseExtras("{\"value\":null}") }
                .exceptionOrNull()
        assertTrue(nullError is IllegalArgumentException)

        val commaError =
            runCatching { AndroidIntentShellCommand.parseExtras("{\"value\":[\"a,b\"]}") }
                .exceptionOrNull()
        assertTrue(commaError is IllegalArgumentException)
    }

    @Test
    fun serviceRequiresExplicitComponent() {
        val error =
            runCatching {
                AndroidIntentShellCommand.build(
                    type = "service",
                    action = "com.example.START",
                    uri = null,
                    packageName = null,
                    component = null,
                    flags = 0,
                    extras = emptyList(),
                )
            }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun amProtocolErrorsAreDetectedWithoutChangingGenericShellSemantics() {
        assertTrue(
            AndroidIntentShellCommand.outputIndicatesFailure(
                stdout = "Broadcasting: Intent { act=com.example.TEST }\nPermission Denial",
                stderr = "",
            ),
        )
        assertTrue(
            AndroidIntentShellCommand.outputIndicatesFailure(
                stdout = "",
                stderr = "Error: Activity not started",
            ),
        )
        assertTrue(
            !AndroidIntentShellCommand.outputIndicatesFailure(
                stdout = "Broadcast completed: result=0",
                stderr = "",
            ),
        )
    }
}
