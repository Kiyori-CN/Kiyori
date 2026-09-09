package com.ai.assistance.operit.util.ripgrep

/** JNI 使用宿主路径，工具结果始终使用调用方的路径空间，供后续 read_file 直接使用。 */
internal data class NativeGrepTarget(val searchPath: String, val displayPath: String) {
    fun displayFilePath(nativePath: String): String {
        val root = searchPath.trimEnd('/').ifEmpty { "/" }
        if (nativePath == root) return displayPath
        val prefix = if (root == "/") root else "$root/"
        require(nativePath.startsWith(prefix)) { "Search returned a path outside the requested directory" }
        return displayPath.trimEnd('/') + "/" + nativePath.removePrefix(prefix)
    }

    companion object {
        fun linux(path: String, mapToHost: (String) -> String): NativeGrepTarget {
            require(path.startsWith('/') || path == "~" || path.startsWith("~/")) {
                "Linux search path must be absolute or use ~/; ~username is not supported"
            }
            require('\u0000' !in path) { "Search path must not contain NUL" }
            val expanded = when {
                path == "~" -> "/root"
                path.startsWith("~/") -> "/root/" + path.substring(2)
                else -> path
            }
            // 先在 guest 空间消解 ..，避免 rootfs 拼接后逃逸到 Android 父目录。
            val parts = mutableListOf<String>()
            expanded.split('/').forEach { part ->
                when (part) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts.add(part)
                }
            }
            val guestPath = "/" + parts.joinToString("/")
            return NativeGrepTarget(mapToHost(guestPath), guestPath)
        }
    }
}
