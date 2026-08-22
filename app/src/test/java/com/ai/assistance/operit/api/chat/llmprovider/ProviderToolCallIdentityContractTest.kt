package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ProviderToolCallIdentityContractTest {
    @Test
    fun `canonical arguments sort object keys and preserve array order`() {
        assertEquals(
            """{"a":[2,1],"b":{"c":3,"d":4}}""",
            ProviderToolCallIdentityContract.canonicalJsonText(
                """{"b":{"d":4,"c":3},"a":[2,1]}"""
            ),
        )
    }

    @Test
    fun `same identity accepts semantically identical arguments`() {
        val key = ProviderToolCallIdentityContract.key("deepseek", "call_123")!!
        ProviderToolCallIdentityContract.requireSame(
            key = key,
            existing =
                ProviderToolCallIdentityContract.signature(
                    toolName = "read_file",
                    argumentsJson = """{"path":"a","line":1}""",
                ),
            incoming =
                ProviderToolCallIdentityContract.signature(
                    toolName = "read_file",
                    argumentsJson = """{"line":1,"path":"a"}""",
                ),
        )
    }

    @Test
    fun `same identity rejects a different payload`() {
        val key = ProviderToolCallIdentityContract.key("deepseek", "call_123")!!
        try {
            ProviderToolCallIdentityContract.requireSame(
                key = key,
                existing =
                    ProviderToolCallIdentityContract.signature(
                        toolName = "read_file",
                        argumentsJson = """{"path":"a"}""",
                    ),
                incoming =
                    ProviderToolCallIdentityContract.signature(
                        toolName = "read_file",
                        argumentsJson = """{"path":"b"}""",
                    ),
            )
            fail("Expected ProviderToolCallIdentityConflictException")
        } catch (_: ProviderToolCallIdentityConflictException) {
        }
    }
}
