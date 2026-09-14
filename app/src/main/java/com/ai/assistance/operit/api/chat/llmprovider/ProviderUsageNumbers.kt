package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject

/** 协议计数必须为非负整数；-1 只在解析边界表示异常，绝不作为 token 入账。 */
internal object ProviderUsageNumbers {
    fun present(json: JSONObject?, key: String): Boolean = json?.has(key) == true && !json.isNull(key)

    fun read(json: JSONObject?, key: String, absent: Int = 0): Int {
        if (!present(json, key)) return absent
        val raw = json?.opt(key) ?: return -1
        val decimal = raw.toString().toBigDecimalOrNull() ?: return -1
        return try {
            decimal.intValueExact().takeIf { it >= 0 } ?: -1
        } catch (_: ArithmeticException) {
            -1
        }
    }

    fun sumCacheCreation(json: JSONObject?): Int {
        json ?: return 0
        var total = 0L
        // 只读取协议定义的桶；不能递归把将来的汇总字段和细分重复相加。
        for (key in listOf("ephemeral_5m_input_tokens", "ephemeral_1h_input_tokens")) {
            val value = read(json, key)
            if (value < 0) return -1
            total += value
        }
        return if (total > Int.MAX_VALUE) -1 else total.toInt()
    }
}
