package com.ai.assistance.operit.ui.features.chat.webview.workspace

/** 新建框只接受当前目录的一个名字；路径导航由目录浏览器负责。 */
internal fun isWorkspaceEntryNameValid(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." &&
        name.none { it == '/' || it == '\\' || it.isISOControl() }
