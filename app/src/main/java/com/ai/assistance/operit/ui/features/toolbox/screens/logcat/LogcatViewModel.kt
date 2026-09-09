package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import kotlinx.coroutines.CancellationException
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 日志查看器ViewModel - 使用AppLogger文件
 */
class LogcatViewModel(
    private val context: Context,
    private val exportLogs: suspend () -> LogcatExportResult = { LogcatExportHelper.exportLogs(context) },
    private val clearApplicationLogs: suspend () -> Unit = { AppLogger.clearApplicationLog() }
) : ViewModel() {


    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()



    private val _isClearing = MutableStateFlow(false)
    val isClearing = _isClearing.asStateFlow()

    fun clearLogs(onSuccess: () -> Unit) {
        if (_isSaving.value || _isClearing.value) return
        _isClearing.value = true
        _saveResult.value = null
        viewModelScope.launch {
            try {
                clearApplicationLogs()
                _saveResult.value = context.getString(R.string.logcat_cleared)
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _saveResult.value = context.getString(R.string.logcat_clear_failed, error.message ?: error.javaClass.simpleName)
            } finally {
                _isClearing.value = false
            }
        }.invokeOnCompletion { _isClearing.value = false }
    }

    fun dismissResult() { _saveResult.value = null }

    fun saveLogsToFile() {
        if (_isSaving.value || _isClearing.value) return

        _isSaving.value = true
        _saveResult.value = null

        viewModelScope.launch {
            try {
                val result = exportLogs()
                _saveResult.value = result.message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _saveResult.value = context.getString(
                    R.string.logcat_save_failed,
                    e.message ?: context.getString(R.string.logcat_unknown_error)
                )
            } finally {
                _isSaving.value = false
            }
        }.invokeOnCompletion { _isSaving.value = false }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogcatViewModel::class.java)) {
                return LogcatViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

}
