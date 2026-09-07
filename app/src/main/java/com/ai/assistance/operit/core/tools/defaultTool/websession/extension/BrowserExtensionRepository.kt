package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage.writeAtomicText
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage.readAtomicText
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage.atomicFileExists
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
internal data class InstalledBrowserExtension(
    val bundle: BrowserExtensionPackage,
    val enabled: Boolean = false,
    val installedAt: Long,
    val updatedAt: Long,
    val revision: String = bundle.revision,
) {
    val id: String get() = bundle.id
}

/** 单文件原子快照同时保存注册与源码，避免 registry 已提交但包文件缺失的中间状态。 */
internal class BrowserExtensionRepository(
    root: File,
    private val validateJavaScript: (String) -> String?,
    private val readSnapshot: (File) -> String? = { file -> if (atomicFileExists(file)) readAtomicText(file) else null },
    private val writeSnapshot: (File, String) -> Unit = ::writeAtomicText,
) {
    private val file = File(root, "extensions.json")
    private val lock = Any()
    private val mutableState = MutableStateFlow(readState())
    val state = mutableState.asStateFlow()

    private fun readState(): List<InstalledBrowserExtension> {
        require(file.length() <= MAX_STORE_BYTES) { "EXTENSION_STORE_TOO_LARGE" }
        require(File(file.parentFile, "${file.name}.bak").length() <= MAX_STORE_BYTES) { "EXTENSION_STORE_TOO_LARGE" }
        val raw = readSnapshot(file) ?: return emptyList()
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_STORE_BYTES) { "EXTENSION_STORE_TOO_LARGE" }
        return BrowserExtensionPackage.codec.decodeFromString<List<InstalledBrowserExtension>>(raw).also { items ->
            require(items.size <= 16 && items.map { it.id }.distinct().size == items.size) { "INVALID_EXTENSION_STORE" }
            items.forEach {
                it.bundle.validate()
                require(it.revision == it.bundle.revision) { "INVALID_EXTENSION_REVISION" }
            }
        }
    }

    fun get(id: String): InstalledBrowserExtension = state.value.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("EXTENSION_NOT_FOUND: $id")

    fun validate(bundle: BrowserExtensionPackage): BrowserExtensionPackage = bundle.validate(validateJavaScript)

    fun install(bundle: BrowserExtensionPackage, expectedRevision: String?): InstalledBrowserExtension {
        validate(bundle)
        val revision = bundle.revision
        return synchronized(lock) {
            val current = state.value.firstOrNull { it.id == bundle.id }
            // 显式 compare-and-swap；create 不能按名称或 ID 偷偷变成覆盖安装。
            require(current?.revision == expectedRevision) { "REVISION_CONFLICT: read current revision before updating" }
            require(current != null || state.value.size < 16) { "EXTENSION_LIMIT_REACHED" }
            if (current != null) {
                require(BrowserExtensionPackage.compareVersions(bundle.manifest.version, current.bundle.manifest.version) >= 0) {
                    "VERSION_DOWNGRADE"
                }
                if (current.revision == revision) return@synchronized current
            }
            val now = System.currentTimeMillis()
            // 更新也先关闭；新代码不继承旧代码的执行授权，由 Agent/用户显式启用后测试。
            val item = InstalledBrowserExtension(bundle, false, current?.installedAt ?: now, now, revision)
            publish(state.value.filterNot { it.id == item.id } + item)
            item
        }
    }

    fun setEnabled(id: String, enabled: Boolean, expectedRevision: String): InstalledBrowserExtension = synchronized(lock) {
        val old = get(id)
        require(old.revision == expectedRevision) { "REVISION_CONFLICT" }
        val item = old.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
        publish(state.value.map { if (it.id == id) item else it })
        item
    }

    fun delete(id: String, expectedRevision: String) = synchronized(lock) {
        require(get(id).revision == expectedRevision) { "REVISION_CONFLICT" }
        publish(state.value.filterNot { it.id == id })
    }

    private fun publish(items: List<InstalledBrowserExtension>) {
        val snapshot = BrowserExtensionPackage.codec.encodeToString(items)
        require(snapshot.toByteArray(Charsets.UTF_8).size <= MAX_STORE_BYTES) { "EXTENSION_STORE_TOO_LARGE" }
        writeSnapshot(file, snapshot)
        mutableState.value = items
    }

    private companion object {
        const val MAX_STORE_BYTES = 40L * BrowserExtensionPackage.MAX_BYTES
    }
}
