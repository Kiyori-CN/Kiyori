package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject

/** 思考开关控制推理本身，includeThoughts 仅请求摘要，不能代替开关或档位。 */
internal object GeminiReasoningCompiler {
    fun apply(config: JSONObject, model: String, enabled: Boolean, quality: Int) {
        require(quality in 1..5) { "thinkingQualityLevel must be in 1..5" }
        val name = model.trim().lowercase().removePrefix("models/")
        val is25 = Regex("gemini-2\\.5-(?:pro|flash)(?:-|$).*").matches(name)
        val is3 = Regex("gemini-3(?:\\.(?:1|5|6|7|8))?-(?:pro|flash)(?:-|$).*").matches(name)
        val custom = config.optJSONObject("thinkingConfig")
        require(!config.has("thinkingConfig") || custom != null) { "Gemini thinkingConfig must be an object" }
        if (!is25 && !is3) {
            // 未识别模型保留显式参数，不向非推理模型猜测注入协议字段。
            return
        }
        require(enabled || (is25 && name.contains("flash"))) {
            "$model does not support disabling thinking; enable thinking or select a model that supports it"
        }
        val thinking = custom ?: JSONObject()
        require(!(thinking.has("thinkingLevel") && thinking.has("thinkingBudget"))) {
            "Gemini thinkingLevel and thinkingBudget cannot be combined"
        }
        if (!enabled) {
            require(!thinking.has("thinkingLevel") && (!thinking.has("thinkingBudget") || thinking.optInt("thinkingBudget", -2) == 0)) {
                "Gemini custom thinkingConfig conflicts with disabled thinking"
            }
            thinking.put("thinkingBudget", 0)
            thinking.put("includeThoughts", false)
        } else if (is25) {
            require(!thinking.has("thinkingLevel")) { "Gemini 2.5 requires thinkingBudget, not thinkingLevel" }
            val maximum = if (name.contains("pro")) 32768 else 24576
            val minimum = if (name.contains("flash-lite")) 512 else if (name.contains("pro")) 128 else 1
            if (!thinking.has("thinkingBudget")) {
                thinking.put("thinkingBudget", listOf(1024, 4096, 8192, 16384, maximum)[quality - 1])
            }
            val budget = thinking.getInt("thinkingBudget")
            require(budget == -1 || budget in minimum..maximum) { "Invalid Gemini thinkingBudget for $model" }
            thinking.put("includeThoughts", true)
        } else {
            // 只对已识别家族编译；3 Pro 原版没有 medium，Flash-Lite Image 仅 minimal/high。
            val levels = when {
                name.startsWith("gemini-3.1-flash-lite-image") -> listOf("minimal", "minimal", "high", "high", "high")
                name.startsWith("gemini-3-pro") -> listOf("low", "low", "high", "high", "high")
                else -> listOf("low", "medium", "high", "high", "high")
            }
            if (!thinking.has("thinkingLevel") && !thinking.has("thinkingBudget")) {
                thinking.put("thinkingLevel", levels[quality - 1])
            }
            if (thinking.has("thinkingLevel")) {
                val supportsMinimal = name.contains("flash-lite") || name.startsWith("gemini-3-flash") ||
                    name.startsWith("gemini-3.5-flash") || name.startsWith("gemini-3.6-flash")
                val allowed = levels.toSet() + if (supportsMinimal) setOf("minimal") else emptySet()
                require(thinking.getString("thinkingLevel").lowercase() in allowed) {
                    "Invalid Gemini thinkingLevel for $model"
                }
            }
            if (thinking.has("thinkingBudget")) {
                val budget = thinking.getInt("thinkingBudget")
                require(budget == -1 || budget > 0) { "Gemini 3 cannot disable thinking with thinkingBudget=0" }
            }
            thinking.put("includeThoughts", true)
        }
        config.put("thinkingConfig", thinking)
    }
}
