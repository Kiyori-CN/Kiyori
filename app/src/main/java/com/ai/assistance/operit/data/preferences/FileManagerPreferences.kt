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

data class FileManagerSettings(
    val showHiddenFiles: Boolean = true,
    val sortMode: FileManagerSortMode = FileManagerSortMode.NAME,
    val sortDescending: Boolean = false,
    val itemSize: Float = 1f,
    val showManuallyHiddenFiles: Boolean = false,
    val manuallyHiddenFiles: Set<FileManagerHiddenEntry> = emptySet(),
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
        if (updated == current) return
        preferences.edit {
            putBoolean("show_hidden", updated.showHiddenFiles)
            putString("sort_mode", updated.sortMode.name)
            putBoolean("sort_descending", updated.sortDescending)
            putFloat("item_size", updated.itemSize)
            putBoolean("show_manual_hidden", updated.showManuallyHiddenFiles)
            putString("manual_hidden_entries", Json.encodeToString(updated.manuallyHiddenFiles))
        }
        mutableState.value = updated
    }

    private fun read(): FileManagerSettings = FileManagerSettings(
        showHiddenFiles = preferences.getBoolean("show_hidden", true),
        sortMode = FileManagerSortMode.entries.firstOrNull {
            it.name == preferences.getString("sort_mode", FileManagerSortMode.NAME.name)
        } ?: FileManagerSortMode.NAME,
        sortDescending = preferences.getBoolean("sort_descending", false),
        itemSize = preferences.getFloat("item_size", 1f).let { if (it.isFinite()) it.coerceIn(0.5f, 1.3f) else 1f },
        showManuallyHiddenFiles = preferences.getBoolean("show_manual_hidden", false),
        manuallyHiddenFiles = readHiddenEntries(),
    )

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
