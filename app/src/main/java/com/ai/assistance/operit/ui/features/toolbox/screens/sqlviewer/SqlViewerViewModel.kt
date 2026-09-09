package com.ai.assistance.operit.ui.features.toolbox.screens.sqlviewer

import android.content.Context
import android.database.Cursor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SqlViewerViewModel(
    private val context: Context,
    private val databaseFactory: () -> SupportSQLiteDatabase = { AppDatabase.getDatabase(context).openHelper.writableDatabase },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    data class QueryResult(
        val columns: List<String>,
        val rows: List<List<String>>
    )

    data class State(
        val requestId: Long = 0,
        val isRunning: Boolean = false,
        val result: QueryResult? = null,
        val error: String? = null,
        val message: String? = null,
        val affectedRows: Int? = null,
        val lastBaseQuery: String = "",
        val canPaginate: Boolean = false,
        val currentOffset: Int = 0,
        val pageSize: Int = 50,
        val lastFetchCount: Int = 0
    )

    private val _state = MutableStateFlow(State())
    private val queryGeneration = java.util.concurrent.atomic.AtomicLong()
    val state: StateFlow<State> = _state.asStateFlow()

    private val database by lazy(databaseFactory)

    fun runQuery(
        rawSql: String,
        pageSize: Int,
        offset: Int,
        canPaginate: Boolean,
        append: Boolean
    ) {
        if (_state.value.isRunning) return
        if (rawSql.isBlank()) {
            _state.value = _state.value.copy(error = context.getString(R.string.sql_viewer_empty_sql), message = null, affectedRows = null)
            return
        }
        val plan = try { planSqlViewerQuery(rawSql, pageSize, offset, canPaginate) }
        catch (error: IllegalArgumentException) {
            _state.value = _state.value.copy(error = error.message, message = null, affectedRows = null)
            return
        }
        val previous = _state.value
        if (append && (previous.result == null || !previous.canPaginate ||
                previous.lastBaseQuery != plan.baseSql || previous.pageSize != pageSize ||
                offset != previous.currentOffset + previous.lastFetchCount)) return
        val trimmed = plan.baseSql
        val effectiveSql = plan.sql

        val generation = queryGeneration.incrementAndGet()
        _state.value = _state.value.copy(requestId = generation, isRunning = true, error = null, message = null, affectedRows = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                if (plan.returnsRows) {
                    val queryResult = executeQuery(effectiveSql)
                    check(!append || previous.result?.columns == queryResult.columns) { "Query columns changed; run the query again before loading more rows" }
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.let { current ->
                            val mergedRows =
                                if (append && current.result != null) {
                                    current.result.rows + queryResult.rows
                                } else {
                                    queryResult.rows
                                }
                            current.copy(
                                isRunning = false,
                                result = queryResult.copy(rows = mergedRows),
                                lastBaseQuery = trimmed,
                                canPaginate = plan.paginated,
                                currentOffset = offset,
                                pageSize = pageSize,
                                lastFetchCount = queryResult.rows.size
                            )
                        }
                    }
                } else {
                    database.execSQL(trimmed)
                    val affected = queryChanges()
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            isRunning = false,
                            result = null,
                            affectedRows = affected,
                            message = "OK",
                            lastBaseQuery = trimmed,
                            canPaginate = false,
                            currentOffset = 0,
                            pageSize = pageSize,
                            lastFetchCount = 0
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isRunning = false,
                        error = e.message ?: "Unknown error",
                        message = null,
                        affectedRows = null
                    )
                }
            }
        }.invokeOnCompletion {
            _state.update { current -> if (current.requestId == generation) current.copy(isRunning = false) else current }
        }
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isRunning || !current.canPaginate || current.lastFetchCount < current.pageSize) return
        runQuery(current.lastBaseQuery, current.pageSize, current.currentOffset + current.lastFetchCount, true, true)
    }

    private fun executeQuery(sql: String): QueryResult {
        database.query(sql).use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = mutableListOf<List<String>>()
            while (cursor.moveToNext()) {
                val row = buildList {
                    for (index in columns.indices) {
                        add(readCell(cursor, index))
                    }
                }
                rows.add(row)
            }
            return QueryResult(columns = columns, rows = rows)
        }
    }

    private fun readCell(cursor: Cursor, index: Int): String {
        return when (cursor.getType(index)) {
            android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
            android.database.Cursor.FIELD_TYPE_BLOB -> {
                val blob = cursor.getBlob(index)
                "BLOB(${blob.size})"
            }
            else -> cursor.getString(index) ?: ""
        }
    }

    private fun queryChanges(): Int? {
        return try {
            database.query("SELECT changes()").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SqlViewerViewModel::class.java)) {
                return SqlViewerViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
