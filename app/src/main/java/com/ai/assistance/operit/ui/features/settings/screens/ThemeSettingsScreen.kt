package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.settings.screens.theme.ThemeSettingsContent
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsWorkspacePage

@Composable
fun ThemeSettingsScreen(onBackPressed: () -> Unit) {
    KiyoriSettingsWorkspacePage(
        title = stringResource(R.string.screen_title_theme_settings),
        onBack = onBackPressed,
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            ThemeSettingsContent()
        }
    }
}
