package com.ai.assistance.operit.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PackageProxyParamsTest {
    @Test
    fun parsesObjectAndPreservesNestedValues() {
        val params = PackageProxyParams.parse("{\"query\":\"a & b\",\"limit\":3,\"tags\":[\"one\"]}")

        assertEquals("a & b", params.getString("query"))
        assertEquals(3, params.getInt("limit"))
        assertEquals("[\"one\"]", params.getJSONArray("tags").toString())
    }

    @Test
    fun parsesQuoteEscapedObjectAndJsonFence() {
        assertEquals("value", PackageProxyParams.parse("\"{\\\"name\\\":\\\"value\\\"}\"").getString("name"))
        assertEquals("value", PackageProxyParams.parse("```json\n{\"name\":\"value\"}\n```").getString("name"))
    }

    @Test
    fun rejectsArraysScalarsAndTrailingTextWithOneStableError() {
        listOf("[1]", "1", "{\"name\":\"value\"} trailing", "not-json", "\"not-an-object\"").forEach { raw ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                PackageProxyParams.parse(raw)
            }
            assertEquals("params must be a valid JSON object", error.message)
        }
    }

    @Test
    fun reportsBlankParamsAsObjectRequirement() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PackageProxyParams.parse("  ")
        }

        assertEquals("params must be a valid JSON object", error.message)
    }
}
