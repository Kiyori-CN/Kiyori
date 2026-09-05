package com.ai.assistance.operit.core.tools.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MCPToolParameterTest {
    @Test
    fun `json arrays preserve nulls and string types`() {
        val converted = MCPToolParameter.smartConvert(
            "[null, \"123\", true, 4, [\"false\"], {\"id\": \"7\"}]",
            "array",
        )

        assertTrue(converted is List<*>)
        val values = converted as List<*>
        assertEquals(6, values.size)
        assertEquals(null, values[0])
        assertEquals("123", values[1])
        assertEquals(true, values[2])
        assertEquals(4, values[3])
        assertEquals(listOf("false"), values[4])
        assertEquals(mapOf("id" to "7"), values[5])
    }

    @Test
    fun `json objects preserve nested string values`() {
        val converted = MCPToolParameter.smartConvert(
            "{\"count\": \"10\", \"enabled\": \"false\", \"nested\": {\"value\": \"2.5\"}}",
            "object",
        )

        assertEquals(
            mapOf(
                "count" to "10",
                "enabled" to "false",
                "nested" to mapOf("value" to "2.5"),
            ),
            converted,
        )
    }

    @Test
    fun `invalid structured input remains unchanged`() {
        val value = "{not-json}"
        assertEquals(value, MCPToolParameter.smartConvert(value, "object"))
    }
}
