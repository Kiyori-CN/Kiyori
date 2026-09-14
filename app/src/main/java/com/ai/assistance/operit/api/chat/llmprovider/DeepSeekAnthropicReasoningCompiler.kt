package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject

internal object DeepSeekAnthropicReasoningCompiler {
    fun apply(request: JSONObject, enabled: Boolean, quality: Int) {
        request.put("thinking", JSONObject().put("type", if (enabled) "enabled" else "disabled"))
        val output = request.optJSONObject("output_config") ?: JSONObject()
        if (enabled) output.put("effort", DeepSeekReasoningPolicy.effort(quality))
        else output.remove("effort")
        if (output.length() > 0) request.put("output_config", output)
        else request.remove("output_config")
    }
}
