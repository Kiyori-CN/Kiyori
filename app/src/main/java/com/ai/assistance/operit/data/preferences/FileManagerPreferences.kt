package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FileManagerHiddenEntry(val path: String, val environment: String? = null) {
    fun contains(candidatePath: String, candidateEnvironment: String?): Boolean =
        normalizeEnvironment(environment) == normalizeEnvironment(candidateEnvironment) &&
            (candidatePath == path || candidatePath.startsWith("$path/"))

    private fun normalizeEnvironment(value: String?): String? = value?.takeUnless { it.isBlank() || it == "android" }
}

enum class FileManagerSortMode { NAME, SIZE, MODIFIED, FORMAT }

@Serializable
data class FileManagerSettings(
    val showHiddenFiles: Boolean = true,
    val sortMode: FileManagerSortMode = FileManagerSortMode.NAME,
    val sortDescending: Boolean = false,
    val itemSize: Float = 1f,
    val showManuallyHiddenFiles: Boolean = false,
    val manuallyHiddenFiles: Set<FileManagerHiddenEntry> = emptySet(),
    val leftStartPath: String = "",
    val rightStartPath: String = "",
    val filenameLines: Int = 4,
    val showSeconds: Boolean = true,
    val showDirectorySizes: Boolean = true,
    val refreshIntervalSeconds: Int = 3,
    val showBookmarks: Boolean = true,
    val showWorkspaces: Boolean = true,
    val newBookmarksOnTop: Boolean = false,
    val drawerOrder: List<String> = emptyList(),
    val drawerHidden: Set<String> = emptySet(),
    val drawerRemoved: Set<String> = emptySet(),
    val drawerNames: Map<String, String> = emptyMap(),
    val defaultWorkspacePath: String = "",
)

/** 设置页与双栏共用显示偏好及手动隐藏路径；不保存浏览位置、选择或权限身份。 */
class FileManagerPreferences internal constructor(private val preferences: SharedPreferences) {
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<FileManagerSettings> = mutableState.asStateFlow()
    val current: FileManagerSettings get() = state.value

    @Synchronized
    fun update(transform: (FileManagerSettings) -> FileManagerSettings) {
        val updated = transform(current)
        require(updated.itemSize.isFinite() && updated.itemSize in 0.5f..1.3f)
        require(updated.filenameLines in 1..6)
        require(updated.refreshIntervalSeconds in listOf(0, 3, 10, 30))
        listOf(updated.leftStartPath, updated.rightStartPath, updated.defaultWorkspacePath).forEach {
            require(it.isEmpty() || validFileManagerStartPath(it)) { "请输入绝对目录路径，不使用 . 或 .." }
        }
        if (updated == current) return
        preferences.edit {
            putBoolean("show_hidden", updated.showHiddenFiles)
            putString("sort_mode", updated.sortMode.name)
            putBoolean("sort_descending", updated.sortDescending)
            putFloat("item_size", updated.itemSize)
            putBoolean("show_manual_hidden", updated.showManuallyHiddenFiles)
            putString("manual_hidden_entries", Json.encodeToString(updated.manuallyHiddenFiles))
            putString("settings_v2", Json.encodeToString(updated))
        }
        mutableState.value = updated
    }

    private fun read(): FileManagerSettings {
        preferences.getString("settings_v2", null)?.let { stored ->
            try { return Json { ignoreUnknownKeys = true }.decodeFromString<FileManagerSettings>(stored) }
            catch (failure: IllegalArgumentException) {
                com.ai.assistance.operit.util.AppLogger.e("FileManagerPreferences", "无法读取文件管理设置", failure)
            }
        }
        return FileManagerSettings(
        showHiddenFiles = preferences.getBoolean("show_hidden", true),
        sortMode = FileManagerSortMode.entries.firstOrNull {
            it.name == preferences.getString("sort_mode", FileManagerSortMode.NAME.name)
        } ?: FileManagerSortMode.NAME,
        sortDescending = preferences.getBoolean("sort_descending", false),
        itemSize = preferences.getFloat("item_size", 1f).let { if (it.isFinite()) it.coerceIn(0.5f, 1.3f) else 1f },
        showManuallyHiddenFiles = preferences.getBoolean("show_manual_hidden", false),
        manuallyHiddenFiles = readHiddenEntries(),
        )
    }

    private fun readHiddenEntries(): Set<FileManagerHiddenEntry> = try {
        Json.decodeFromString<Set<FileManagerHiddenEntry>>(preferences.getString("manual_hidden_entries", "[]") ?: "[]")
    } catch (failure: IllegalArgumentException) {
        com.ai.assistance.operit.util.AppLogger.e("FileManagerPreferences", "无法解析手动隐藏显示偏好", failure)
        emptySet()
    }

    companion object {
        @Volatile private var instance: FileManagerPreferences? = null
        fun getInstance(context: Context): FileManagerPreferences = instance ?: synchronized(this) {
            instance ?: FileManagerPreferences(
                context.applicationContext.getSharedPreferences("kiyori_file_manager", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}

fun validFileManagerStartPath(path: String): Boolean = path.startsWith('/') &&
    path.none { it.isISOControl() } && path.split('/').none { it == "." || it == ".." }

/** 保持旧记录的确定身份；编辑后显式保存，桌面快捷方式与排序继续指向同一入口。 */
internal fun fileBookmarkIdentity(bookmark: ApiPreferences.FileBookmark, workspace: Boolean): String =
    bookmark.entryId ?: "${if (workspace) "工作区" else "书签"}:${bookmark.environment.orEmpty()}:${bookmark.path}"
