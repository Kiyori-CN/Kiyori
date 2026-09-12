package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FileManagerSortMode { NAME, SIZE, MODIFIED }

data class FileManagerSettings(
    val showHiddenFiles: Boolean = true,
    val sortMode: FileManagerSortMode = FileManagerSortMode.NAME,
    val sortDescending: Boolean = false,
    val itemSize: Float = 1f,
)

/** 设置页与双栏快捷操作共用唯一偏好；只保存显示偏好，不保存路径、选择或权限身份。 */
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
    )

    companion object {
        @Volatile private var instance: FileManagerPreferences? = null
        fun getInstance(context: Context): FileManagerPreferences = instance ?: synchronized(this) {
            instance ?: FileManagerPreferences(
                context.applicationContext.getSharedPreferences("kiyori_file_manager", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
