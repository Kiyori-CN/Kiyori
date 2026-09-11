package com.kiyori.platform.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

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
        require(raw.none { it in ":*?\"<>|" }) { "Shared-storage paths cannot contain URI schemes or reserved filename characters" }
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
        require(path !in setOf("/", "/root", "/home", "/tmp", "/usr", "/var", "/etc", "/bin", "/opt", "/boot", "/dev", "/proc", "/sys", "/run", "/sbin", "/lib", "/lib64", "/mnt", "/media", "/srv")) {
            "Choose a dedicated artifact subdirectory, not a system or home root"
        }
        require(listOf("/dev/", "/proc/", "/sys/").none(path::startsWith)) {
            "Virtual system filesystems cannot store artifacts"
        }
        return path
    }

    /** 工作区是显式目标，可位于私有目录；但 URI、repo: 或 ~ 不能冒充绝对本地目录。 */
    fun workspaceRoot(path: String): String = normalizeAbsolute(path).also {
        require(it != "/") { "Workspace must name a directory below the filesystem root" }
    }

    fun projectRoot(defaultRoot: () -> String, environment: String, workspace: String?, workspaceEnv: String?): String {
        require(environment == "android" || environment == "linux") { "Expected android or linux environment" }
        if (workspace.isNullOrBlank()) return defaultRoot()
        val boundEnv = workspaceEnv?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty().ifBlank { "android" }
        require(boundEnv == "android" || boundEnv == "linux") {
            "The workspace uses $boundEnv; supply an explicit destination in the matching environment"
        }
        // 另一端的绑定不改变本端默认值，也不把 Linux 路径解释成 Android 目录。
        return if (boundEnv == environment) workspaceRoot(workspace) else defaultRoot()
    }

    /** DocumentsUI 只负责选目录；content URI 的授权不等同于 File/Shell 访问授权。 */
    fun primaryTreeRoot(authority: String?, documentId: String, externalRoot: String): String {
        require(authority == "com.android.externalstorage.documents" && documentId.startsWith("primary:")) {
            "Choose a folder in this device's shared storage; cloud and removable-storage URIs are not local paths"
        }
        return androidRoot("${externalRoot.trimEnd('/')}/${documentId.removePrefix("primary:")}", externalRoot)
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
    fun reserveCategorizedFile(root: File, category: String, relativeName: String): File {
        resolveRelative("/artifacts", category)
        resolveRelative("/artifacts", relativeName)
        // 分类目录也必须处于配置根内；不能把 browser 符号链接的目标当作新边界。
        return reserveUniqueFile(root, "$category/$relativeName")
    }

    fun reserveUniqueFile(base: File, relativeName: String): File {
        val safeRelative = resolveRelative("/artifacts", relativeName).removePrefix("/artifacts/")
        val desired = File(base, safeRelative)
        val parent = requireNotNull(desired.parentFile)
        val canonicalBase = realPathWithMissingChildren(base)
        require(realPathWithMissingChildren(parent).startsWith(canonicalBase)) {
            "Artifact directory escapes its root through a symbolic link"
        }
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) throw IOException("Cannot create artifact directory: $parent")
        require(parent.toPath().toRealPath().startsWith(canonicalBase)) { "Artifact directory changed outside its root" }
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

    private fun realPathWithMissingChildren(file: File): Path {
        var existing = file.toPath().toAbsolutePath().normalize()
        val missing = java.util.ArrayDeque<String>()
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missing.addFirst(existing.fileName.toString())
            existing = existing.parent ?: throw IOException("Cannot resolve artifact parent")
        }
        // File.canonicalPath 在部分宿主上不会展开目录符号链接；toRealPath 才能验证真实边界。
        var resolved = existing.toRealPath()
        for (child in missing) resolved = resolved.resolve(child)
        return resolved
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun initialDirectoryCommand(path: String): String {
        val quoted = shellQuote(normalizeAbsolute(path))
        return "mkdir -p -- $quoted && cd -- $quoted"
    }
}
