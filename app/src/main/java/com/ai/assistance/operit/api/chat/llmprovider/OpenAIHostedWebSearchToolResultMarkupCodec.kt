package com.ai.assistance.operit.api.chat.llmprovider

/**
 * Keeps the bundled Web Search ToolPkg's serialized JSON inert while it is embedded inside the
 * chat XML tool-result envelope.
 *
 * QuickJS always delivers a JSON-serialized value to JsEngine.setCallResult. JSON Unicode escapes
 * preserve the decoded value and citation offsets, while preventing untrusted web text from
 * introducing a literal XML delimiter before the renderer parses the surrounding tool result.
 */
internal object OpenAIHostedWebSearchToolResultMarkupCodec {
    fun encodeSerializedJson(serializedJson: String): String =
        serializedJson
            .replace("&", "\\u0026")
            .replace("<", "\\u003c")
            .replace(">", "\\u003e")
}
