package com.ai.assistance.operit.api.chat.llmprovider

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAIResponsesToolSearchCompilerAndroidTest {
    @Test
    fun largeCatalog_keepsCoreFunctionsEagerAndDefersTheLongTail() {
        val tools = JSONArray()
        tools.put(function("read_file"))
        repeat(10) { index ->
            tools.put(function("special_tool_$index"))
        }

        val compiled =
            OpenAIResponsesToolSearchCompiler.compile(
                sourceTools = tools,
                enabled = true,
            )

        assertEquals(10, compiled.deferredFunctionCount)
        assertTrue(compiled.toolSearchAdded)
        assertFalse(compiled.tools.getJSONObject(0).has("defer_loading"))
        assertTrue(compiled.tools.getJSONObject(1).getBoolean("defer_loading"))
        assertEquals(
            "tool_search",
            compiled.tools.getJSONObject(compiled.tools.length() - 1).getString("type"),
        )
    }

    @Test
    fun smallCatalog_doesNotAddToolSearchRoundTrip() {
        val tools = JSONArray()
        repeat(4) { index ->
            tools.put(function("special_tool_$index"))
        }

        val compiled =
            OpenAIResponsesToolSearchCompiler.compile(
                sourceTools = tools,
                enabled = true,
            )

        assertEquals(0, compiled.deferredFunctionCount)
        assertFalse(compiled.toolSearchAdded)
        assertEquals(4, compiled.tools.length())
    }

    private fun function(name: String): JSONObject =
        JSONObject()
            .put("type", "function")
            .put("name", name)
            .put("description", name)
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject())
                    .put("required", JSONArray()),
            )
}
