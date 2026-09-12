package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel

import android.content.Context
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 只保存私有历史，不执行文件任务。单实例串行原子发布，避免关闭/重开会话覆盖迟到结果。 */
class FileManagerHistoryStore(private val file: File, private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(FileManagerHistory())
    val state = mutableState.asStateFlow()
    private var loaded = false
    private val deletedSearches = mutableSetOf<String>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load() = update { it }

    private suspend fun update(
        onCommitted: (FileManagerHistory) -> Unit = {},
        transform: (FileManagerHistory) -> FileManagerHistory,
    ) = withContext(dispatcher) {
        mutex.withLock {
            if (!loaded) {
                val initial = if (!file.exists()) FileManagerHistory() else {
                    require(file.length() <= 32L * 1024 * 1024) { "文件历史过大，请先清空历史" }
                    json.decodeFromString<FileManagerHistory>(file.readText(Charsets.UTF_8)).also {
                        require(it.version == 1) { "文件历史版本不受支持" }
                    }
                }
                // 上次进程没有落下终态的任务只标记待确认，绝不自动重放。
                mutableState.value = initial.copy(
                    tasks = initial.tasks.map { if (it.finishedAt == null) it.copy(status = "结果待确认", finishedAt = System.currentTimeMillis()) else it },
                    searches = initial.searches.map { if (it.status == "搜索中") it.copy(status = "已中断") else it },
                )
                loaded = true
            }
            val next = transform(mutableState.value)
            require(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "无法创建文件历史目录" }
            val temporary = File(file.parentFile, file.name + ".tmp")
            val bytes = json.encodeToString(next).toByteArray(Charsets.UTF_8)
            require(bytes.size <= 32 * 1024 * 1024) { "文件历史过大，请先清空历史" }
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            // 删除标记与磁盘提交在同一锁内发布；写盘失败不能阻止后续结果保存。
            onCommitted(mutableState.value)
            mutableState.value = next
        }
    }

    suspend fun putSearch(record: FileManagerSearchRecord) = update { history ->
        if (record.id in deletedSearches) return@update history
        val saved = record.copy(results = record.results.take(1000), limitations = record.limitations +
            if (record.total > 1000) listOf("保存前 1000 项，共找到 ${record.total} 项；重新搜索可获取当前结果") else emptyList())
        history.copy(searches = (listOf(saved) + history.searches.filterNot { it.id == record.id }).sortedByDescending { it.time }.take(20))
    }
    suspend fun putTask(record: FileManagerTaskRecord) = update { history ->
        history.copy(tasks = (listOf(record.copy(results = record.results.take(1000), resultCount = record.results.size)) + history.tasks.filterNot { it.id == record.id })
            .sortedByDescending { it.time }.take(100))
    }
    suspend fun removeSearch(id: String?) = update(onCommitted = {
        deletedSearches.addAll(if (id == null) it.searches.map { record -> record.id } else listOf(id))
    }) {
        it.copy(searches = if (id == null) emptyList() else it.searches.filterNot { record -> record.id == id })
    }
    suspend fun removeTask(id: String?) = update { it.copy(tasks = it.tasks.filter { record -> record.finishedAt == null || (id != null && record.id != id) }) }

    companion object {
        @Volatile private var instance: FileManagerHistoryStore? = null
        fun getInstance(context: Context): FileManagerHistoryStore = instance ?: synchronized(this) {
            instance ?: FileManagerHistoryStore(File(context.applicationContext.noBackupFilesDir, "file-manager/history.json"))
                .also { instance = it }
        }
    }
}
