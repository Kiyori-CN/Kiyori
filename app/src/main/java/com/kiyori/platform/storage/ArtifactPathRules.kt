package com.kiyori.platform.storage

import java.io.File
import java.io.IOException

/** 无 Android 依赖的路径规则；默认路径不是任意脚本的文件系统沙箱。 */
internal object ArtifactPathRules {
    const val DEFAULT_ANDROID_RELATIVE = "Download/Kiyori/workspace"
    const val DEFAULT_LINUX = "/workspace"

    private fun validateCharacters(path: String) {
        require(path.none { it.code < 32 || it.code == 127 } && '\\' !in path) {
            "Path must not contain control characters or backslashes"
        }
        require(path.split('/').none { it == ".." }) { "Path must not contain .. segments" }
    }

    fun androidRoot(value: String?, externalRoot: String): String {
        value?.let(::validateCharacters)
        val raw = value?.trim().orEmpty().ifBlank { DEFAULT_ANDROID_RELATIVE }
        validateCharacters(raw)
        val external = externalRoot.trimEnd('/')
        // 兼容上一版的两种相对输入；持久化与界面均统一展示绝对路径。
        val path = when {
            raw == "/sdcard" || raw.startsWith("/sdcard/") -> external + raw.removePrefix("/sdcard")
            raw.startsWith('/') -> raw
            raw == "Download" || raw.startsWith("Download/") -> "$external/$raw"
            else -> "$external/Download/$raw"
        }
        val normalized = normalizeAbsolute(path)
        require(normalized.startsWith("$external/") && normalized != "$external/Download") {
            "Choose a subdirectory in shared storage, not the storage or Download root"
        }
        require(normalized != "$external/Android" && !normalized.startsWith("$external/Android/")) {
            "Android private application directories cannot be artifact roots"
        }
        return normalized
    }

    fun linuxRoot(value: String?): String {
        value?.let(::validateCharacters)
        val path = normalizeAbsolute(value?.trim().orEmpty().ifBlank { DEFAULT_LINUX })
        require(path !in setOf("/", "/root", "/home", "/tmp", "/usr", "/var", "/etc", "/bin", "/opt")) {
            "Choose a dedicated artifact subdirectory, not a system or home root"
        }
        return path
    }

    fun normalizeAbsolute(path: String): String {
        validateCharacters(path)
        require(path.startsWith('/')) { "Path must be absolute" }
        return "/" + path.split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")
    }

    fun resolveRelative(root: String, path: String): String {
        require(path.isNotBlank()) { "Path must not be empty" }
        validateCharacters(path)
        require(!path.startsWith('/') && !path.startsWith('~') && ':' !in path) {
            "Expected a relative artifact path"
        }
        val resolved = normalizeAbsolute("$root/$path")
        require(resolved.startsWith(root.trimEnd('/') + "/")) { "Artifact path must name a child" }
        return resolved
    }

    /** 原子占位，避免两个并发截图在 exists()/write() 间选中同一个文件。 */
    fun reserveUniqueFile(base: File, relativeName: String): File {
        val safeRelative = resolveRelative("/artifacts", relativeName).removePrefix("/artifacts/")
        val desired = File(base, safeRelative)
        val parent = requireNotNull(desired.parentFile)
        val canonicalBase = base.canonicalFile
        require(parent.canonicalFile == canonicalBase ||
            parent.canonicalPath.startsWith(canonicalBase.path + File.separator)) {
            "Artifact directory escapes its root through a symbolic link"
        }
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) throw IOException("Cannot create artifact directory: $parent")
        val name = desired.name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val stem = name.substring(0, dot)
        val extension = name.substring(dot)
        for (index in 0..9999) {
            val candidate = File(parent, stem + (if (index == 0) "" else "-$index") + extension)
            if (candidate.createNewFile()) return candidate
        }
        throw IOException("Too many artifacts with the same name: $name")
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun initialDirectoryCommand(path: String): String {
        val quoted = shellQuote(normalizeAbsolute(path))
        return "mkdir -p -- $quoted && cd -- $quoted"
    }
}
