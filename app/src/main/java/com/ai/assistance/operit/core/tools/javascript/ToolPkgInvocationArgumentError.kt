package com.ai.assistance.operit.core.tools.javascript

import org.json.JSONObject

internal enum class ToolPkgInvocationArgumentReason {
    MISSING,
    INVALID_TYPE,
}

internal data class ToolPkgInvocationArgumentError(
    val toolName: String,
    val field: String,
    val expectedType: String,
    val reason: ToolPkgInvocationArgumentReason,
) {
    fun toJson(): String =
        JSONObject()
            .put("code", CODE)
            .put("tool", toolName)
            .put("field", field)
            .put("expected_type", expectedType)
            .put("reason", reason.name)
            .put("message", "ToolPkg invocation argument was rejected before execution.")
            .toString()

    fun formatLog(): String =
        "ToolPkg invocation argument rejected: " +
            "code=$CODE tool=$toolName field=$field expected_type=$expectedType " +
            "reason=${reason.name}"

    companion object {
        const val CODE = "INVALID_ARGUMENT"
    }
}
