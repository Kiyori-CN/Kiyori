package com.ai.assistance.operit.ui.features.chat.webview.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 工作区打开文件的唯一内存状态；异步操作以路径和内容快照核对，不依赖标签索引。 */
internal class WorkspaceEditorState {
    var openFiles by mutableStateOf(emptyList<OpenFileInfo>())
    var currentFileIndex by mutableStateOf(-1)
    var unsavedFiles by mutableStateOf(emptySet<String>())
        private set
    var savingFiles by mutableStateOf(emptySet<String>())
        private set
    var isRestoring by mutableStateOf(false)
        private set
    var refreshGeneration by mutableStateOf(0)
        private set

    suspend fun <T> withRewind(block: suspend () -> T): T {
        check(!isRestoring && unsavedFiles.isEmpty() && savingFiles.isEmpty())
        isRestoring = true
        try {
            return block()
        } finally {
            // 这里只允许干净标签进入。恢复可能删除文件或部分失败，关闭旧快照，重新打开时读取实际磁盘。
            // 保留失效正文会让用户随后保存它，意外覆盖刚恢复的文件。
            openFiles = emptyList()
            currentFileIndex = -1
            refreshGeneration++
            isRestoring = false
        }
    }

    fun edit(path: String, content: String) {
        if (isRestoring) return
        val index = openFiles.indexOfFirst { it.path == path }
        if (index < 0 || openFiles[index].content == content) return
        openFiles = openFiles.toMutableList().apply { this[index] = this[index].copy(content = content) }
        unsavedFiles = unsavedFiles + path
    }

    fun close(path: String) {
        if (path in savingFiles) return
        val index = openFiles.indexOfFirst { it.path == path }
        if (index < 0) return
        val activePath = openFiles.getOrNull(currentFileIndex)?.path
        openFiles = openFiles.filterNot { it.path == path }
        unsavedFiles = unsavedFiles - path
        currentFileIndex = when {
            activePath == null -> -1
            activePath != path -> openFiles.indexOfFirst { it.path == activePath }
            else -> index.coerceAtMost(openFiles.lastIndex)
        }
    }

    /** true 仅表示保存成功且该文件仍与写入快照一致，可安全关闭。 */
    suspend fun save(path: String, write: suspend (OpenFileInfo) -> Boolean): Boolean {
        if (isRestoring || path in savingFiles) return false
        val snapshot = openFiles.find { it.path == path } ?: return false
        savingFiles = savingFiles + path
        try {
            if (!write(snapshot)) return false
            if (openFiles.find { it.path == path }?.content != snapshot.content) return false
            unsavedFiles = unsavedFiles - path
            return true
        } finally {
            savingFiles = savingFiles - path
        }
    }

    fun acceptExternalUpdate(snapshot: OpenFileInfo, updated: OpenFileInfo) {
        if (isRestoring) return
        if (snapshot.path in unsavedFiles || snapshot.path in savingFiles) return
        val index = openFiles.indexOfFirst { it.path == snapshot.path }
        if (index < 0 || openFiles[index] != snapshot) return
        openFiles = openFiles.toMutableList().apply { this[index] = updated }
    }
}
