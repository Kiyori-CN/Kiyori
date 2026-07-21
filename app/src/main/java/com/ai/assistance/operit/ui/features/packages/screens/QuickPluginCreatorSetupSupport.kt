package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult

private const val OPERIT_EDITOR_PACKAGE_NAME = "operit_editor"

internal fun runQuickPluginCreatorSetup(
    context: Context
): ToolResult {
    // The upstream setup script was mutable remote code executed at runtime. Kiyori has no
    // owned replacement channel yet, so this entry remains visible but fails closed.
    return ToolResult(
        toolName = "$OPERIT_EDITOR_PACKAGE_NAME:debug_run_sandbox_script",
        success = false,
        result = StringResultData(""),
        error = context.getString(R.string.quick_plugin_creator_setup_unavailable)
    )
}
