package com.ai.assistance.operit.ui.main.components

import androidx.compose.runtime.staticCompositionLocalOf

/** AI 顶栏只提交导航意图，由当前 Shell 持有文件管理器会话。 */
val LocalKiyoriOpenFileManager = staticCompositionLocalOf<(() -> Unit)?> { null }
