package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

/** 名称只表示单个目录项，防止新建或重命名逃出用户看到的目录。 */
fun fileManagerNameError(name: String): String? = when {
    name.isBlank() -> "请输入名称"
    name == "." || name == ".." -> "不能使用当前目录或上级目录作为名称"
    name.any { it == '/' || it == '\\' || it.isISOControl() } -> "名称不能包含路径分隔符或控制字符"
    else -> null
}

fun fileManagerJoinPath(parent: String, name: String): String =
    "${parent.trimEnd('/')}/$name"
