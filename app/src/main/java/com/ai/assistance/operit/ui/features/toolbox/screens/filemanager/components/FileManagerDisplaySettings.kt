package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.ai.assistance.operit.data.preferences.FileManagerSettings

/** 只投影设置的当前值；偏好仍由 FileManagerPreferences 持有。 */
internal val LocalFileManagerDisplaySettings = staticCompositionLocalOf { FileManagerSettings() }
