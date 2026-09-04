package com.ai.assistance.operit.core.tools.system

import org.json.JSONArray
import org.json.JSONObject

/** A typed extra encoded for Android's `am` command line. */
internal data class AndroidIntentShellExtra(
    val option: String,
    val key: String,
    val value: String,
)

/**
 * Builds shell-safe `am` commands for the privileged Intent and broadcast tools.
 *
 * The shell command is intentionally assembled from individual arguments. Values are quoted at
 * the final boundary, so an action, URI, component, extra key, or extra value cannot introduce a
 * second shell command. Unsupported JSON values fail explicitly instead of being stringified into
 * a different payload.
 */
internal object AndroidIntentShellCommand {

    private const val TYPE_ACTIVITY = "activity"
    private const val TYPE_BROADCAST = "broadcast"
    private const val TYPE_SERVICE = "service"

    fun parseFlags(rawFlags: String?): Int {
        val raw = rawFlags?.trim().orEmpty()
        if (raw.isEmpty()) {
            return 0
        }

        return if (raw.startsWith("[")) {
            val array = JSONArray(raw)
            var combined = 0
            for (index in 0 until array.length()) {
                val value = array.get(index)
                require(value is Number) { "Intent flags array must contain only numbers" }
                val longValue = value.toString().toLongOrNull()
                    ?: throw IllegalArgumentException("Intent flags array must contain integers")
                require(longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    "Intent flag is outside the 32-bit range"
                }
                combined = combined or longValue.toInt()
            }
            combined
        } else {
            parseInteger(raw, "Intent flags")
        }
    }

    fun parseExtras(rawExtras: String?): List<AndroidIntentShellExtra> {
        val raw = rawExtras?.trim().orEmpty()
        if (raw.isEmpty()) {
            return emptyList()
        }

        val json = JSONObject(raw)
        val keys = mutableListOf<String>()
        val iterator = json.keys()
        while (iterator.hasNext()) {
            keys += iterator.next()
        }
        return keys.sorted().flatMap { key -> encodeValue(key, json.get(key)) }
    }

    fun stringExtra(key: String, value: String): AndroidIntentShellExtra {
        require(key.isNotBlank()) { "Intent extra key must not be blank" }
        return AndroidIntentShellExtra("--es", key, value)
    }

    fun normalizeComponent(rawComponent: String?): String? {
        val raw = rawComponent?.trim().orEmpty()
        if (raw.isEmpty()) {
            return null
        }
        val separator = raw.indexOf('/')
        require(separator > 0 && separator < raw.lastIndex) {
            "Intent component must use package/class form"
        }
        require(raw.indexOf('/', separator + 1) < 0) {
            "Intent component must contain exactly one separator"
        }
        val packageName = raw.substring(0, separator).trim()
        val className = raw.substring(separator + 1).trim()
        require(packageName.isNotEmpty() && className.isNotEmpty()) {
            "Intent component must use package/class form"
        }
        val normalizedClass = if (className.startsWith('.')) packageName + className else className
        return "$packageName/$normalizedClass"
    }

    fun build(
        type: String,
        action: String?,
        uri: String?,
        packageName: String?,
        component: String?,
        flags: Int,
        extras: List<AndroidIntentShellExtra>,
    ): String {
        val command =
            when (type) {
                TYPE_ACTIVITY -> "am start"
                TYPE_BROADCAST -> "am broadcast"
                TYPE_SERVICE -> "am startservice"
                else -> throw IllegalArgumentException("Unsupported Intent type: $type")
            }
        val normalizedAction = action?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedUri = uri?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedPackage = packageName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedComponent = normalizeComponent(component)
        require(normalizedAction != null || normalizedComponent != null) {
            "Intent must have either an action or component specified"
        }
        if (type == TYPE_SERVICE) {
            require(normalizedComponent != null) {
                "Intent component is required when starting a service"
            }
        }

        val arguments = mutableListOf<String>()
        normalizedAction?.let {
            arguments += "-a"
            arguments += shellQuote(it)
        }
        normalizedUri?.let {
            arguments += "-d"
            arguments += shellQuote(it)
        }
        normalizedPackage?.let {
            arguments += "-p"
            arguments += shellQuote(it)
        }
        normalizedComponent?.let {
            arguments += "-n"
            arguments += shellQuote(it)
        }
        if (flags != 0) {
            arguments += "-f"
            arguments += flags.toString()
        }
        extras.forEach { extra ->
            require(extra.key.isNotBlank()) { "Intent extra key must not be blank" }
            arguments += extra.option
            arguments += shellQuote(extra.key)
            arguments += shellQuote(extra.value)
        }

        return buildString {
            append(command)
            if (arguments.isNotEmpty()) {
                append(' ')
                append(arguments.joinToString(" "))
            }
        }
    }

    /** `am` can print protocol errors even when its shell process reports exit code zero. */
    fun outputIndicatesFailure(stdout: String, stderr: String): Boolean {
        val output = listOf(stdout, stderr).filter(String::isNotBlank).joinToString("\n")
        return output.contains("Permission Denial", ignoreCase = true) ||
            output.contains("permission denied", ignoreCase = true) ||
            output.contains("Error:", ignoreCase = true)
    }

    internal fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private fun encodeValue(key: String, value: Any): List<AndroidIntentShellExtra> {
        require(key.isNotBlank()) { "Intent extra key must not be blank" }
        return when (value) {
            is String -> listOf(AndroidIntentShellExtra("--es", key, value))
            is Boolean -> listOf(AndroidIntentShellExtra("--ez", key, value.toString()))
            is Byte, is Short, is Int ->
                listOf(AndroidIntentShellExtra("--ei", key, value.toString()))
            is Long -> listOf(AndroidIntentShellExtra("--el", key, value.toString()))
            is Float -> listOf(AndroidIntentShellExtra("--ef", key, value.toString()))
            is Double -> listOf(AndroidIntentShellExtra("--ed", key, value.toString()))
            is Number -> listOf(numberExtra(key, value))
            is JSONArray -> encodeArray(key, value)
            JSONObject.NULL -> throw IllegalArgumentException("Intent extra '$key' cannot be null")
            else -> throw IllegalArgumentException(
                "Intent extra '$key' has unsupported type ${value.javaClass.simpleName}"
            )
        }
    }

    private fun encodeArray(key: String, array: JSONArray): List<AndroidIntentShellExtra> {
        require(array.length() > 0) { "Intent extra '$key' cannot be an empty array" }
        val values = (0 until array.length()).map { index -> array.get(index) }
        val first = values.first()
        return when (first) {
            is String -> {
                require(values.all { it is String }) {
                    "Intent extra '$key' array must contain one value type"
                }
                require(values.none { (it as String).contains(',') }) {
                    "String array extra '$key' contains a comma and cannot be represented safely"
                }
                listOf(AndroidIntentShellExtra("--esa", key, values.joinToString(",")))
            }
            is Boolean -> {
                require(values.all { it is Boolean }) {
                    "Intent extra '$key' array must contain one value type"
                }
                listOf(AndroidIntentShellExtra("--eza", key, values.joinToString(",")))
            }
            is Byte, is Short, is Int -> {
                require(values.all { it is Byte || it is Short || it is Int }) {
                    "Intent extra '$key' array must contain one value type"
                }
                listOf(AndroidIntentShellExtra("--eia", key, values.joinToString(",")))
            }
            is Long -> {
                require(values.all { it is Long }) {
                    "Intent extra '$key' array must contain one value type"
                }
                listOf(AndroidIntentShellExtra("--ela", key, values.joinToString(",")))
            }
            is Float -> {
                require(values.all { it is Float }) {
                    "Intent extra '$key' array must contain one value type"
                }
                listOf(AndroidIntentShellExtra("--efa", key, values.joinToString(",")))
            }
            is Double -> {
                require(values.all { it is Double }) {
                    "Intent extra '$key' array must contain one value type"
                }
                listOf(AndroidIntentShellExtra("--eda", key, values.joinToString(",")))
            }
            is Number -> {
                require(values.all { it is Number }) {
                    "Intent extra '$key' array must contain one value type"
                }
                val numericValues = values.map { it as Number }
                val rendered = numericValues.joinToString(",") { it.toString() }
                if (numericValues.any { !isIntegralNumber(it.toString()) }) {
                    listOf(AndroidIntentShellExtra("--eda", key, rendered))
                } else if (
                    numericValues.all {
                        it.toLong() in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                    }
                ) {
                    listOf(AndroidIntentShellExtra("--eia", key, rendered))
                } else {
                    listOf(AndroidIntentShellExtra("--ela", key, rendered))
                }
            }
            else -> throw IllegalArgumentException(
                "Intent extra '$key' contains an unsupported array value type"
            )
        }
    }

    private fun numberExtra(key: String, value: Number): AndroidIntentShellExtra {
        val rendered = value.toString()
        return when {
            isIntegralNumber(rendered) -> {
                val longValue = rendered.toLongOrNull()
                    ?: throw IllegalArgumentException("Intent extra '$key' integer is out of range")
                if (longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    AndroidIntentShellExtra("--ei", key, rendered)
                } else {
                    AndroidIntentShellExtra("--el", key, rendered)
                }
            }
            else -> AndroidIntentShellExtra("--ed", key, rendered)
        }
    }

    private fun isIntegralNumber(rendered: String): Boolean =
        !rendered.contains('.') && !rendered.contains('e', ignoreCase = true)

    private fun parseInteger(raw: String, label: String): Int {
        return try {
            if (raw.startsWith("0x", ignoreCase = true)) {
                raw.substring(2).toLong(16).toInt().also {
                    require(raw.substring(2).toLong(16) in Int.MIN_VALUE.toLong()..0xFFFFFFFFL) {
                        "$label is outside the 32-bit range"
                    }
                }
            } else {
                raw.toLong().also {
                    require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                        "$label is outside the 32-bit range"
                    }
                }.toInt()
            }
        } catch (error: Exception) {
            if (error is IllegalArgumentException && error.message?.contains("outside") == true) {
                throw error
            }
            throw IllegalArgumentException("$label must be an integer", error)
        }
    }
}
