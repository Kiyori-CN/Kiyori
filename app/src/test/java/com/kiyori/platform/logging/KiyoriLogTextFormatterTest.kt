package com.kiyori.platform.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriLogTextFormatterTest {

    @Test
    fun unchangedTextRemainsUnchanged() {
        assertEquals(
            "short diagnostic",
            KiyoriLogTextFormatter.truncateText(
                text = "short diagnostic",
                maxChars = 512,
            ),
        )
    }

    @Test
    fun longTextIsBoundedAndMarked() {
        val formatted =
            KiyoriLogTextFormatter.truncateText(
                text = "x".repeat(2_000),
                maxChars = 512,
            )

        assertEquals(512, formatted.length)
        assertTrue(formatted.endsWith("\n... [truncated]"))
    }

    @Test
    fun throwableFormattingKeepsCauseAndBound() {
        val cause = IllegalArgumentException("inner failure")
        val error =
            IllegalStateException(
                "outer failure",
                cause,
            )
        error.stackTrace = emptyArray()
        cause.stackTrace = emptyArray()

        val formatted =
            KiyoriLogTextFormatter.format(
                throwable = error,
                maxChars = 512,
            )

        assertTrue(formatted.length <= 512)
        assertTrue(formatted.contains("java.lang.IllegalStateException: outer failure"))
        assertTrue(formatted.contains("Caused by: java.lang.IllegalArgumentException"))
    }
}
