package com.ai.assistance.operit.ui.features.chat.components

internal fun validExportPackageName(value: String): Boolean =
    Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(value)

internal fun validExportVersionName(value: String): Boolean =
    Regex("[0-9]+(\\.[0-9]+){0,2}(-[a-zA-Z0-9]+)?").matches(value)

internal fun validExportVersionCode(value: String): Boolean =
    value.isNotEmpty() && value.all { it in '0'..'9' } && value.toIntOrNull()?.let { it > 0 } == true
