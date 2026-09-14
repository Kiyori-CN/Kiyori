package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject

/** 缓存请求策略只改善可复用条件，真实命中始终取供应商 usage。 */
internal object OpenAIPromptCachePolicy {
    fun requestChatUsage(request: JSONObject, profile: ModelCapabilityProfile) {
        if (!request.optBoolean("stream") || profile.reasoningWireFormat != ReasoningWireFormat.CHAT_COMPLETIONS) return
        // 尊重显式关闭；保留兼容服务已有扩展。不把 Chat 参数注入 Responses。
        val options = request.optJSONObject("stream_options") ?: JSONObject()
        if (!options.has("include_usage")) options.put("include_usage", true)
        request.put("stream_options", options)
    }

    fun markStablePrefix(request: JSONObject, profile: ModelCapabilityProfile) {
        if (profile.providerContractAuthority != ProviderContractAuthority.OPENAI_OFFICIAL ||
            profile.reasoningWireFormat != ReasoningWireFormat.RESPONSES ||
            profile.modelFamily !in setOf("gpt-5.6", "gpt-6-astra")) return
        // 显式缓存模式/断点是调用方策略，不能悄悄扩大缓存写入范围或改变收费。
        if (request.optJSONObject("prompt_cache_options")?.has("mode") == true) return
        val input = request.optJSONArray("input") ?: return
        if (input.toString().contains("\"prompt_cache_breakpoint\"")) return
        var lastDeveloper: JSONObject? = null
        for (index in 0 until input.length()) {
            val message = input.optJSONObject(index) ?: break
            if (message.optString("role") != "developer") break
            lastDeveloper = message
        }
        val message = lastDeveloper ?: return
        val content = message.opt("content")
        val parts: JSONArray
        if (content is String) {
            if (content.isBlank()) return
            parts = JSONArray().put(JSONObject().put("type", "input_text").put("text", content))
        } else if (content is JSONArray) {
            parts = content
        } else return
        val last = parts.optJSONObject(parts.length() - 1) ?: return
        if (last.optString("type") != "input_text") return
        // 保留默认 implicit 的历史消息边界，同时保存稳定 developer 前缀。
        // 首轮动态用户内容变化时，仍可复用独立的初始指令前缀。
        last.put("prompt_cache_breakpoint", JSONObject().put("mode", "explicit"))
        message.put("content", parts)
    }
}
