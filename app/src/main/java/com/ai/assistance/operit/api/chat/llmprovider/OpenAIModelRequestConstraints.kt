package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject

/** 已核实的模型限制在提交前处理；不切换模型、协议或用户思考意图。 */
internal object OpenAIModelRequestConstraints {
    fun apply(request: JSONObject, profile: ModelCapabilityProfile) {
        if (profile.modelFamily != "gpt-6-astra") return
        val responses = profile.reasoningWireFormat == ReasoningWireFormat.RESPONSES
        require(responses || request.optJSONArray("tools").let { it == null || it.length() == 0 }) {
            "gpt-6-astra 的原生工具调用需要 OpenAI Responses 协议；请在模型配置中选择 Responses。"
        }
        // 旧配置的采样参数在 Astra 没有定义；按模型合同移除，不能发送后再重试。
        listOf("temperature", "top_p", "top_logprobs", "logprobs").forEach(request::remove)
        if (!responses && request.has("max_tokens")) {
            if (!request.has("max_completion_tokens")) {
                request.put("max_completion_tokens", request.get("max_tokens"))
            }
            request.remove("max_tokens")
        }
        request.optJSONArray("include")?.let { source ->
            val filtered = JSONArray()
            for (index in 0 until source.length()) {
                if (source.optString(index) != "message.output_text.logprobs") {
                    filtered.put(source.get(index))
                }
            }
            request.put("include", filtered)
        }
        if (profile.providerContractAuthority == ProviderContractAuthority.OPENAI_OFFICIAL) {
            request.remove("prompt_cache_retention")
            val options = request.optJSONObject("prompt_cache_options") ?: JSONObject()
            options.put("ttl", "30m")
            request.put("prompt_cache_options", options)
        }
    }
}
