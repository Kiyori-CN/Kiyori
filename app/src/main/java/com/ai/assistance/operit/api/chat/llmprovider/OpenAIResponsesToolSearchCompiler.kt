package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject

internal data class OpenAIResponsesToolSearchCompilation(
    val tools: JSONArray,
    val deferredFunctionCount: Int,
    val toolSearchAdded: Boolean,
)

internal object OpenAIResponsesToolSearchCompiler {
    private const val MIN_DEFERRED_FUNCTIONS = 8
    private val eagerFunctionNames =
        setOf(
            "read_file",
            "read_file_part",
            "list_files",
            "grep_code",
            "file_exists",
            "calculate",
            "use_package",
            "package_proxy",
        )

    fun compile(
        sourceTools: JSONArray,
        enabled: Boolean,
    ): OpenAIResponsesToolSearchCompilation {
        val copied =
            JSONArray().apply {
                for (index in 0 until sourceTools.length()) {
                    val tool = sourceTools.optJSONObject(index) ?: continue
                    put(JSONObject(tool.toString()))
                }
            }
        if (!enabled) {
            return OpenAIResponsesToolSearchCompilation(
                tools = copied,
                deferredFunctionCount = 0,
                toolSearchAdded = false,
            )
        }

        val deferCandidates = mutableListOf<JSONObject>()
        var alreadyHasToolSearch = false
        for (index in 0 until copied.length()) {
            val tool = copied.optJSONObject(index) ?: continue
            when (tool.optString("type", "")) {
                "tool_search" -> alreadyHasToolSearch = true
                "function" -> {
                    val name = tool.optString("name", "").trim()
                    if (name.isNotEmpty() && name !in eagerFunctionNames) {
                        deferCandidates += tool
                    }
                }
            }
        }

        if (deferCandidates.size < MIN_DEFERRED_FUNCTIONS) {
            return OpenAIResponsesToolSearchCompilation(
                tools = copied,
                deferredFunctionCount = 0,
                toolSearchAdded = false,
            )
        }

        deferCandidates.forEach { function ->
            function.put("defer_loading", true)
        }
        if (!alreadyHasToolSearch) {
            copied.put(JSONObject().put("type", "tool_search"))
        }
        return OpenAIResponsesToolSearchCompilation(
            tools = copied,
            deferredFunctionCount = deferCandidates.size,
            toolSearchAdded = !alreadyHasToolSearch,
        )
    }
}
