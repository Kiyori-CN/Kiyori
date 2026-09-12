package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerPreferencesTest {
    private fun preferences(values: MutableMap<String, Any> = mutableMapOf()): SharedPreferences {
        val prefs: SharedPreferences = mock()
        val editor: SharedPreferences.Editor = mock()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.getBoolean(any(), any())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1) }
        whenever(prefs.getString(any(), any())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<String>(1) }
        whenever(prefs.getFloat(any(), any())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<Float>(1) }
        whenever(editor.putBoolean(any(), any())).thenAnswer { values[it.getArgument(0)] = it.getArgument<Boolean>(1); editor }
        whenever(editor.putString(any(), any())).thenAnswer { values[it.getArgument(0)] = it.getArgument<String>(1); editor }
        whenever(editor.putFloat(any(), any())).thenAnswer { values[it.getArgument(0)] = it.getArgument<Float>(1); editor }
        return prefs
    }

    @Test fun `display preferences survive store recreation without losing unrelated fields`() {
        val disk = preferences()
        val store = FileManagerPreferences(disk)
        assertEquals(FileManagerSettings(), store.current)
        store.update { it.copy(showHiddenFiles = false, sortMode = FileManagerSortMode.MODIFIED, sortDescending = true) }
        store.update { it.copy(itemSize = 1.2f) }
        assertEquals(FileManagerSettings(false, FileManagerSortMode.MODIFIED, true, 1.2f), FileManagerPreferences(disk).current)
    }

    @Test fun `invalid display size is rejected without changing saved settings`() {
        val store = FileManagerPreferences(preferences())
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { store.update { it.copy(itemSize = value) } }
        }
        assertEquals(FileManagerSettings(), store.current)
    }

    @Test fun `old unknown sort and nonfinite size are normalized when reading preferences`() {
        val store = FileManagerPreferences(preferences(mutableMapOf("sort_mode" to "OLD", "item_size" to Float.NaN)))
        assertEquals(FileManagerSortMode.NAME, store.current.sortMode)
        assertEquals(1f, store.current.itemSize)
    }

    @Test fun `hidden preference reaches both panes while session sorting remains independent of defaults`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val models = ViewModelStore()
        Mockito.mockStatic(Log::class.java).use {
            try {
                val context: Context = mock()
                whenever(context.getString(R.string.file_manager_home)).thenReturn("Home")
                val store = FileManagerPreferences(preferences())
                store.update { it.copy(showHiddenFiles = false, itemSize = 1.2f) }
                val model = FileManagerViewModel(context, "/test", dispatcher, store) { tool ->
                    ToolResult(tool.name, true, DirectoryListingData("/test", listOf(
                        DirectoryListingData.FileEntry(".hidden", false, 1, "", "0"),
                        DirectoryListingData.FileEntry("visible", false, 2, "", "0"),
                    )), "")
                }
                models.put("files", model)
                runCurrent()
                assertEquals(1.2f, model.itemSize)
                assertEquals(listOf("visible"), model.leftPaneState.files.map { it.name })
                assertEquals(listOf("visible"), model.rightPaneState.files.map { it.name })
                store.update { it.copy(showHiddenFiles = true, sortMode = FileManagerSortMode.SIZE, sortDescending = true) }
                runCurrent()
                assertEquals(listOf(".hidden", "visible"), model.leftPaneState.files.map { it.name })
                assertEquals(listOf(".hidden", "visible"), model.rightPaneState.files.map { it.name })
                assertEquals(FileManagerSortMode.NAME, model.sortMode)
                model.toggleHiddenFiles()
                model.toggleSortDirection()
                runCurrent()
                assertFalse(store.current.showHiddenFiles)
                assertTrue(store.current.sortDescending)
                assertTrue(model.leftPaneState.sortDescending)
                assertFalse(model.rightPaneState.sortDescending)
                assertEquals(1.2f, store.current.itemSize)
            } finally {
                models.clear()
                Dispatchers.resetMain()
            }
        }
    }
}
