package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.ToolParameter
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Canonical parser for the object-valued params field of package_proxy. */
object PackageProxyParams {
    fun parse(raw: String): JSONObject {
        var candidate = stripJsonFence(raw)
        repeat(2) {
            val value = parseJsonValue(candidate)
            when (value) {
                is JSONObject -> return value
                is String -> {
                    candidate = stripJsonFence(value)
                }
                is JSONArray,
                null,
                JSONObject.NULL -> throw IllegalArgumentException("params must be a valid JSON object")
                else -> throw IllegalArgumentException("params must be a valid JSON object")
            }
        }
        throw IllegalArgumentException("params must be a valid JSON object")
    }

    fun toToolParameters(params: JSONObject): MutableList<ToolParameter> {
        val forwardedParameters = mutableListOf<ToolParameter>()
        val keys = params.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = params.opt(key)
            val valueString =
                if (value == null || value === JSONObject.NULL) {
                    "null"
                } else if (value is String) {
                    value
                } else {
                    value.toString()
                }
            forwardedParameters.add(ToolParameter(name = key, value = valueString))
        }
        return forwardedParameters
    }

    private fun parseJsonValue(candidate: String): Any? {
        if (candidate.isBlank()) {
            throw IllegalArgumentException("params must be a valid JSON object")
        }
        return try {
            val tokener = JSONTokener(candidate)
            val value = tokener.nextValue()
            if (tokener.nextClean() != '\u0000') {
                throw IllegalArgumentException("params must be a valid JSON object")
            }
            value
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("params must be a valid JSON object")
        }
    }

    private fun stripJsonFence(raw: String): String {
        val trimmed = raw.removePrefix("\uFEFF").trim()
        if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) {
            return trimmed
        }

        val lines = trimmed.split('\n')
        if (lines.size < 3) {
            return trimmed
        }
        val language = lines.first().trim().removePrefix("```").trim()
        if (language.isNotEmpty() && !language.equals("json", ignoreCase = true)) {
            return trimmed
        }
        return lines.subList(1, lines.lastIndex).joinToString("\n").trim()
    }
}
