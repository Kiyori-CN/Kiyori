package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune

import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.components.ColorSelectionItem
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsCharacterBindingInfoCard
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsSectionTitle
import kotlinx.coroutines.launch

@Composable
internal fun ThemeSettingsInterfaceTab(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
    onShowSaveSuccessMessage: () -> Unit,
) {
    val preferencesManager = shared.preferencesManager
    val chatHeaderTransparent by
        preferencesManager.chatHeaderTransparent.collectAsState(initial = false)
    val chatHeaderOverlayMode by
        preferencesManager.chatHeaderOverlayMode.collectAsState(initial = false)
    val historyIconColor by
        preferencesManager.chatHeaderHistoryIconColor.collectAsState(initial = null)
    val pipIconColor by preferencesManager.chatHeaderPipIconColor.collectAsState(initial = null)
    val recentColors by preferencesManager.recentColorsFlow.collectAsState(initial = emptyList())
    val defaultHeaderIconColor = MaterialTheme.colorScheme.onSurface.toArgb()

    var chatHeaderTransparentInput by remember { mutableStateOf(chatHeaderTransparent) }
    var chatHeaderOverlayModeInput by remember { mutableStateOf(chatHeaderOverlayMode) }
    var historyIconColorInput by
        remember { mutableStateOf(historyIconColor ?: defaultHeaderIconColor) }
    var pipIconColorInput by remember { mutableStateOf(pipIconColor ?: defaultHeaderIconColor) }
    var currentColorPickerMode by remember { mutableStateOf("historyIcon") }
    var showColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(
        chatHeaderTransparent,
        chatHeaderOverlayMode,
        historyIconColor,
        pipIconColor,
        defaultHeaderIconColor,
    ) {
        chatHeaderTransparentInput = chatHeaderTransparent
        chatHeaderOverlayModeInput = chatHeaderOverlayMode
        historyIconColorInput = historyIconColor ?: defaultHeaderIconColor
        pipIconColorInput = pipIconColor ?: defaultHeaderIconColor
    }

    ThemeSettingsCharacterBindingInfoCard(
        aiAvatarUri = shared.activeThemeTargetAvatarUri,
        activeCharacterName = shared.activeThemeTargetName,
        isGroupTarget = shared.isGroupThemeTarget,
        cardColors = cardColors,
    )

    ThemeSettingsSectionTitle(
        title = stringResource(id = R.string.theme_tab_interface),
        icon = Icons.Outlined.Tune,
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = cardColors,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ThemeSettingsInterfaceSwitch(
                title = stringResource(id = R.string.theme_chat_header_transparent),
                description = stringResource(id = R.string.theme_chat_header_transparent_desc),
                checked = chatHeaderTransparentInput,
                onCheckedChange = {
                    chatHeaderTransparentInput = it
                    shared.saveThemeSettingsWithCharacterCard {
                        preferencesManager.saveThemeSettings(chatHeaderTransparent = it)
                    }
                    onShowSaveSuccessMessage()
                },
            )

            if (chatHeaderTransparentInput) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ThemeSettingsInterfaceSwitch(
                    title = stringResource(id = R.string.theme_chat_header_overlay_mode),
                    description = stringResource(id = R.string.theme_chat_header_overlay_mode_desc),
                    checked = chatHeaderOverlayModeInput,
                    onCheckedChange = {
                        chatHeaderOverlayModeInput = it
                        shared.saveThemeSettingsWithCharacterCard {
                            preferencesManager.saveThemeSettings(chatHeaderOverlayMode = it)
                        }
                        onShowSaveSuccessMessage()
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(id = R.string.theme_chat_header_icons_color_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ColorSelectionItem(
                title = stringResource(id = R.string.theme_chat_header_history_icon_color),
                color = Color(historyIconColorInput),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    currentColorPickerMode = "historyIcon"
                    showColorPicker = true
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ColorSelectionItem(
                title = stringResource(id = R.string.theme_chat_header_pip_icon_color),
                color = Color(pipIconColorInput),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    currentColorPickerMode = "pipIcon"
                    showColorPicker = true
                },
            )
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            showColorPicker = showColorPicker,
            currentColorPickerMode = currentColorPickerMode,
            primaryColorInput = MaterialTheme.colorScheme.primary.toArgb(),
            secondaryColorInput = MaterialTheme.colorScheme.secondary.toArgb(),
            appBarColorInput = MaterialTheme.colorScheme.surface.toArgb(),
            historyIconColorInput = historyIconColorInput,
            pipIconColorInput = pipIconColorInput,
            cursorUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleAiBubbleColorInput = MaterialTheme.colorScheme.surface.toArgb(),
            bubbleUserTextColorInput = MaterialTheme.colorScheme.onPrimaryContainer.toArgb(),
            bubbleAiTextColorInput = MaterialTheme.colorScheme.onSurface.toArgb(),
            recentColors = recentColors,
            onColorSelected = { _, _, _, historyIcon, pipIcon, _, _, _, _, _ ->
                val selectedColor =
                    if (currentColorPickerMode == "historyIcon") historyIcon else pipIcon
                selectedColor?.let { color ->
                    shared.scope.launch { preferencesManager.addRecentColor(color) }
                    if (currentColorPickerMode == "historyIcon") {
                        historyIconColorInput = color
                    } else {
                        pipIconColorInput = color
                    }
                    shared.saveThemeSettingsWithCharacterCard {
                        if (currentColorPickerMode == "historyIcon") {
                            preferencesManager.saveThemeSettings(
                                chatHeaderHistoryIconColor = color,
                            )
                        } else {
                            preferencesManager.saveThemeSettings(
                                chatHeaderPipIconColor = color,
                            )
                        }
                    }
                    onShowSaveSuccessMessage()
                }
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

@Composable
private fun ThemeSettingsInterfaceSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
