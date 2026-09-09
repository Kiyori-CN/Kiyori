package com.ai.assistance.operit.ui.features.chat.webview.workspace

/** 恢复前核对清单与对象定位；写入全部成功后才删除多余文件，失败交给调用者保留历史和备份。 */
internal suspend fun restoreWorkspaceFiles(
    currentFiles: Map<String, String>,
    targetFiles: Map<String, String>,
    resolveObject: suspend (String) -> String?,
    writeFile: suspend (path: String, objectPath: String, hash: String) -> Unit,
    deleteFile: suspend (String) -> Unit,
) {
    (currentFiles.keys + targetFiles.keys).forEach { path ->
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." } &&
            path.none { it.isISOControl() }) { "Invalid workspace backup path" }
    }
    (currentFiles.values + targetFiles.values).forEach { hash ->
        require(hash.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid workspace backup hash" }
    }
    val changed = targetFiles.filter { (path, hash) -> currentFiles[path] != hash }
    val objects = changed.values.distinct().associateWith { hash ->
        checkNotNull(resolveObject(hash)) { "Workspace backup object is missing" }
    }
    changed.forEach { (path, hash) -> writeFile(path, objects.getValue(hash), hash) }
    currentFiles.keys.filterNot { it in targetFiles }.forEach { deleteFile(it) }
}
