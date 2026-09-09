package com.ai.assistance.operit.ui.features.toolbox.screens.sqlviewer

import android.content.Context
import android.database.Cursor
import androidx.lifecycle.ViewModelStore
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SqlViewerOperationTest {
    @Test fun `duplicate query is excluded and paging keeps the result snapshot after invalid draft input`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val database = mock<SupportSQLiteDatabase>()
            whenever(database.query(any<String>())).thenAnswer {
                mock<Cursor>().also { cursor ->
                    whenever(cursor.columnNames).thenReturn(arrayOf("id"))
                    whenever(cursor.moveToNext()).thenReturn(true, false)
                    whenever(cursor.getType(0)).thenReturn(Cursor.FIELD_TYPE_INTEGER)
                    whenever(cursor.getLong(0)).thenReturn(1L)
                }
            }
            val state = SqlViewerViewModel(mock<Context>(), { database }, dispatcher)
            store.put("sql", state)
            state.runQuery("SELECT id FROM chats", 1, 0, true, false)
            state.runQuery("SELECT another FROM chats", 50, 0, true, false)
            assertTrue(state.state.value.isRunning)
            runCurrent()
            verify(database, times(1)).query(any<String>())
            assertFalse(state.state.value.isRunning)
            state.runQuery("SELECT invalid", 0, 0, true, false)
            assertNotNull(state.state.value.error)
            state.loadNextPage()
            state.loadNextPage()
            runCurrent()
            verify(database).query("SELECT * FROM (\nSELECT id FROM chats\n) LIMIT 1 OFFSET 1")
            assertEquals(2, state.state.value.result!!.rows.size)
            assertEquals(1, state.state.value.pageSize)
            assertNull(state.state.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
