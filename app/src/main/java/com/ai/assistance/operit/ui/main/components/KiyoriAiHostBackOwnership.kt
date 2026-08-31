package com.ai.assistance.operit.ui.main.components

import androidx.compose.runtime.compositionLocalOf

/**
 * The retained AI host stays composed while the software-home pager shows another page.
 * Back handlers inside AI children must follow the visible pager page, not composition lifetime.
 */
internal val LocalKiyoriAiHostSystemBackEnabled = compositionLocalOf { false }
