package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import com.kiyori.design.theme.resolveColors
import kotlinx.coroutines.yield

internal enum class ThemeSettingsTab(
    val titleRes: Int,
    val icon: ImageVector,
    val iconTone: KiyoriSemanticTone,
) {
    BASIC(R.string.theme_tab_basic, Icons.Default.Brightness6, KiyoriSemanticTone.BLUE),
    BACKGROUND(
        R.string.theme_tab_background,
        Icons.Default.Wallpaper,
        KiyoriSemanticTone.PINK,
    ),
    CHAT(R.string.theme_tab_chat, Icons.Default.ChatBubble, KiyoriSemanticTone.PURPLE),
    INPUT(R.string.theme_tab_input, Icons.Default.Keyboard, KiyoriSemanticTone.CYAN),
    INTERFACE(R.string.theme_tab_interface, Icons.Default.Tune, KiyoriSemanticTone.ORANGE),
}

@Composable
internal fun ThemeSettingsTabbedContent(
    selectedTab: ThemeSettingsTab,
    onSelectedTabChange: (ThemeSettingsTab) -> Unit,
    basicContent: @Composable () -> Unit,
    backgroundContent: @Composable () -> Unit,
    chatContent: @Composable () -> Unit,
    inputContent: @Composable () -> Unit,
    interfaceContent: @Composable () -> Unit,
    footerContent: @Composable () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val settingsColors = LocalKiyoriSettingsColors.current
    var renderedTab by remember { mutableStateOf(selectedTab) }
    var isSwitchingTab by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        if (renderedTab != selectedTab) {
            isSwitchingTab = true
            yield()
            scrollState.scrollTo(0)
            renderedTab = selectedTab
            isSwitchingTab = false
        }
    }

    Column(modifier = modifier) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            ThemeSettingsTab.values().forEach { tab ->
                val iconColors = tab.iconTone.resolveColors()
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onSelectedTabChange(tab) },
                    modifier = Modifier.height(56.dp),
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = iconColors.icon,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(id = tab.titleRes),
                            color =
                                if (selectedTab == tab) {
                                    settingsColors.primaryText
                                } else {
                                    settingsColors.secondaryText
                                },
                        )
                    },
                )
            }
        }

        if (isSwitchingTab) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
        ) {
            when (renderedTab) {
                ThemeSettingsTab.BASIC -> basicContent()
                ThemeSettingsTab.BACKGROUND -> backgroundContent()
                ThemeSettingsTab.CHAT -> chatContent()
                ThemeSettingsTab.INPUT -> inputContent()
                ThemeSettingsTab.INTERFACE -> interfaceContent()
            }

            footerContent()
        }
    }
}

@Composable
internal fun ThemeSettingsFooter(
    showSaveSuccessMessage: Boolean,
    onShowSaveSuccessMessageChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    OutlinedButton(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    ) {
        Text(stringResource(id = R.string.theme_reset))
    }

    if (showSaveSuccessMessage) {
        LaunchedEffect(key1 = showSaveSuccessMessage) {
            kotlinx.coroutines.delay(2000)
            onShowSaveSuccessMessageChange(false)
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(id = R.string.theme_saved),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
