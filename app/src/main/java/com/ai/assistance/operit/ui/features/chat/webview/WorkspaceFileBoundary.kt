package com.ai.assistance.operit.ui.features.chat.webview

import java.io.File

/** canonical 消除父目录与符号链接；分隔符确保同名前缀的相邻目录不属于工作区。 */
internal fun isFileWithinWorkspace(root: File, candidate: File): Boolean {
    val rootPath = root.canonicalPath
    val candidatePath = candidate.canonicalPath
    return candidatePath == rootPath || candidatePath.startsWith(rootPath.trimEnd(File.separatorChar) + File.separator)
}
