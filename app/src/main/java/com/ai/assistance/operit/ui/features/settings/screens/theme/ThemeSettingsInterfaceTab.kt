package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorContentMode
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorCustomizationSection
import kotlinx.coroutines.launch

@Composable
internal fun ThemeSettingsInterfaceTab(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
    onShowSaveSuccessMessage: () -> Unit,
) {
    ThemeSettingsInterfaceColorPanel(
        shared = shared,
        cardColors = cardColors,
        onShowSaveSuccessMessage = onShowSaveSuccessMessage,
    )
}


@Composable
private fun ThemeSettingsInterfaceColorPanel(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
    onShowSaveSuccessMessage: () -> Unit,
) {
    val preferencesManager = shared.preferencesManager
    val defaultPrimaryColor = Color.Magenta.toArgb()
    val defaultSecondaryColor = Color.Blue.toArgb()
    val defaultStatusBarColor = MaterialTheme.colorScheme.surface.toArgb()
    val defaultAppBarColor = MaterialTheme.colorScheme.surface.toArgb()
    val defaultHeaderIconColor = Color.Gray.toArgb()

    val useCustomColors by preferencesManager.useCustomColors.collectAsState(initial = false)
    val primaryColor by preferencesManager.customPrimaryColor.collectAsState(initial = null)
    val secondaryColor by preferencesManager.customSecondaryColor.collectAsState(initial = null)
    val statusBarHidden by preferencesManager.statusBarHidden.collectAsState(initial = false)
    val statusBarTransparent by preferencesManager.statusBarTransparent.collectAsState(initial = false)
    val useCustomStatusBarColor by preferencesManager.useCustomStatusBarColor.collectAsState(initial = false)
    val customStatusBarColor by preferencesManager.customStatusBarColor.collectAsState(initial = null)
    val toolbarTransparent by preferencesManager.toolbarTransparent.collectAsState(initial = false)
    val useCustomAppBarColor by preferencesManager.useCustomAppBarColor.collectAsState(initial = false)
    val customAppBarColor by preferencesManager.customAppBarColor.collectAsState(initial = null)
    val chatHeaderTransparent by preferencesManager.chatHeaderTransparent.collectAsState(initial = false)
    val chatHeaderOverlayMode by preferencesManager.chatHeaderOverlayMode.collectAsState(initial = false)
    val chatInputTransparent by preferencesManager.chatInputTransparent.collectAsState(initial = false)
    val chatInputFloating by preferencesManager.chatInputFloating.collectAsState(initial = false)
    val chatInputLiquidGlass by preferencesManager.chatInputLiquidGlass.collectAsState(initial = false)
    val chatInputWaterGlass by preferencesManager.chatInputWaterGlass.collectAsState(initial = false)
    val forceAppBarContentColor by preferencesManager.forceAppBarContentColor.collectAsState(initial = false)
    val appBarContentColorMode by preferencesManager.appBarContentColorMode.collectAsState(
        initial = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
    )
    val historyIconColor by preferencesManager.chatHeaderHistoryIconColor.collectAsState(initial = null)
    val pipIconColor by preferencesManager.chatHeaderPipIconColor.collectAsState(initial = null)
    val onColorMode by preferencesManager.onColorMode.collectAsState(
        initial = UserPreferencesManager.ON_COLOR_MODE_AUTO,
    )
    val recentColors by preferencesManager.recentColorsFlow.collectAsState(initial = emptyList())
    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorPickerMode by remember { mutableStateOf("primary") }

    var statusBarHiddenInput by remember { mutableStateOf(statusBarHidden) }
    var statusBarTransparentInput by remember { mutableStateOf(statusBarTransparent) }
    var useCustomStatusBarColorInput by remember { mutableStateOf(useCustomStatusBarColor) }
    var customStatusBarColorInput by remember { mutableStateOf(customStatusBarColor ?: defaultStatusBarColor) }
    var toolbarTransparentInput by remember { mutableStateOf(toolbarTransparent) }
    var useCustomAppBarColorInput by remember { mutableStateOf(useCustomAppBarColor) }
    var customAppBarColorInput by remember { mutableStateOf(customAppBarColor ?: defaultAppBarColor) }
    var chatHeaderTransparentInput by remember { mutableStateOf(chatHeaderTransparent) }
    var chatHeaderOverlayModeInput by remember { mutableStateOf(chatHeaderOverlayMode) }
    var chatInputTransparentInput by remember { mutableStateOf(chatInputTransparent) }
    var chatInputFloatingInput by remember { mutableStateOf(chatInputFloating) }
    var chatInputLiquidGlassInput by remember { mutableStateOf(chatInputLiquidGlass) }
    var chatInputWaterGlassInput by remember { mutableStateOf(chatInputWaterGlass) }
    var forceAppBarContentColorInput by remember { mutableStateOf(forceAppBarContentColor) }
    var appBarContentColorModeInput by remember { mutableStateOf(appBarContentColorMode) }
    var historyIconColorInput by remember { mutableStateOf(historyIconColor ?: defaultHeaderIconColor) }
    var pipIconColorInput by remember { mutableStateOf(pipIconColor ?: defaultHeaderIconColor) }
    var useCustomColorsInput by remember { mutableStateOf(useCustomColors) }
    var primaryColorInput by remember { mutableStateOf(primaryColor ?: defaultPrimaryColor) }
    var secondaryColorInput by remember { mutableStateOf(secondaryColor ?: defaultSecondaryColor) }
    var onColorModeInput by remember { mutableStateOf(onColorMode) }

    LaunchedEffect(
        useCustomColors,
        primaryColor,
        secondaryColor,
        statusBarHidden,
        statusBarTransparent,
        useCustomStatusBarColor,
        customStatusBarColor,
        toolbarTransparent,
        useCustomAppBarColor,
        customAppBarColor,
        chatHeaderTransparent,
        chatHeaderOverlayMode,
        chatInputTransparent,
        chatInputFloating,
        chatInputLiquidGlass,
        chatInputWaterGlass,
        forceAppBarContentColor,
        appBarContentColorMode,
        historyIconColor,
        pipIconColor,
        onColorMode,
    ) {
        useCustomColorsInput = useCustomColors
        primaryColorInput = primaryColor ?: defaultPrimaryColor
        secondaryColorInput = secondaryColor ?: defaultSecondaryColor
        statusBarHiddenInput = statusBarHidden
        statusBarTransparentInput = statusBarTransparent
        useCustomStatusBarColorInput = useCustomStatusBarColor
        customStatusBarColorInput = customStatusBarColor ?: defaultStatusBarColor
        toolbarTransparentInput = toolbarTransparent
        useCustomAppBarColorInput = useCustomAppBarColor
        customAppBarColorInput = customAppBarColor ?: defaultAppBarColor
        chatHeaderTransparentInput = chatHeaderTransparent
        chatHeaderOverlayModeInput = chatHeaderOverlayMode
        chatInputTransparentInput = chatInputTransparent
        chatInputFloatingInput = chatInputFloating
        chatInputLiquidGlassInput = chatInputLiquidGlass
        chatInputWaterGlassInput = chatInputWaterGlass
        forceAppBarContentColorInput = forceAppBarContentColor
        appBarContentColorModeInput = appBarContentColorMode
        historyIconColorInput = historyIconColor ?: defaultHeaderIconColor
        pipIconColorInput = pipIconColor ?: defaultHeaderIconColor
        onColorModeInput = onColorMode
    }

    ThemeSettingsColorCustomizationSection(
        cardColors = cardColors,
        preferencesManager = preferencesManager,
        scope = shared.scope,
        saveThemeSettingsWithCharacterCard = shared.saveThemeSettingsWithCharacterCard,
        statusBarHiddenInput = statusBarHiddenInput,
        onStatusBarHiddenInputChange = { statusBarHiddenInput = it },
        statusBarTransparentInput = statusBarTransparentInput,
        onStatusBarTransparentInputChange = { statusBarTransparentInput = it },
        useCustomStatusBarColorInput = useCustomStatusBarColorInput,
        onUseCustomStatusBarColorInputChange = { useCustomStatusBarColorInput = it },
        customStatusBarColorInput = customStatusBarColorInput,
        toolbarTransparentInput = toolbarTransparentInput,
        onToolbarTransparentInputChange = { toolbarTransparentInput = it },
        useCustomAppBarColorInput = useCustomAppBarColorInput,
        onUseCustomAppBarColorInputChange = { useCustomAppBarColorInput = it },
        customAppBarColorInput = customAppBarColorInput,
        chatHeaderTransparentInput = chatHeaderTransparentInput,
        onChatHeaderTransparentInputChange = { chatHeaderTransparentInput = it },
        chatHeaderOverlayModeInput = chatHeaderOverlayModeInput,
        onChatHeaderOverlayModeInputChange = { chatHeaderOverlayModeInput = it },
        chatInputTransparentInput = chatInputTransparentInput,
        onChatInputTransparentInputChange = { chatInputTransparentInput = it },
        chatInputFloatingInput = chatInputFloatingInput,
        onChatInputFloatingInputChange = { chatInputFloatingInput = it },
        chatInputLiquidGlassInput = chatInputLiquidGlassInput,
        onChatInputLiquidGlassInputChange = { chatInputLiquidGlassInput = it },
        chatInputWaterGlassInput = chatInputWaterGlassInput,
        onChatInputWaterGlassInputChange = { chatInputWaterGlassInput = it },
        forceAppBarContentColorInput = forceAppBarContentColorInput,
        onForceAppBarContentColorInputChange = { forceAppBarContentColorInput = it },
        appBarContentColorModeInput = appBarContentColorModeInput,
        onAppBarContentColorModeInputChange = { appBarContentColorModeInput = it },
        chatHeaderHistoryIconColorInput = historyIconColorInput,
        chatHeaderPipIconColorInput = pipIconColorInput,
        useCustomColorsInput = useCustomColorsInput,
        onUseCustomColorsInputChange = { useCustomColorsInput = it },
        primaryColorInput = primaryColorInput,
        secondaryColorInput = secondaryColorInput,
        onColorModeInput = onColorModeInput,
        onOnColorModeInputChange = { onColorModeInput = it },
        onShowColorPicker = {
            currentColorPickerMode = it
            showColorPicker = true
        },
        onShowSaveSuccessMessage = onShowSaveSuccessMessage,
        contentMode = ThemeSettingsColorContentMode.INTERFACE,
    )

    if (showColorPicker) {
        ColorPickerDialog(
            showColorPicker = showColorPicker,
            currentColorPickerMode = currentColorPickerMode,
            primaryColorInput = primaryColorInput,
            secondaryColorInput = secondaryColorInput,
            statusBarColorInput = customStatusBarColorInput,
            appBarColorInput = customAppBarColorInput,
            historyIconColorInput = historyIconColorInput,
            pipIconColorInput = pipIconColorInput,
            cursorUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleAiBubbleColorInput = MaterialTheme.colorScheme.surface.toArgb(),
            bubbleUserTextColorInput = MaterialTheme.colorScheme.onPrimaryContainer.toArgb(),
            bubbleAiTextColorInput = MaterialTheme.colorScheme.onSurface.toArgb(),
            recentColors = recentColors,
            onColorSelected = { primary,
                secondary,
                statusBar,
                appBar,
                historyIcon,
                pipIcon,
                _,
                _,
                _,
                _,
                _ ->
                saveSelectedThemeColor(
                    shared = shared,
                    currentColorPickerMode = currentColorPickerMode,
                    primaryColor = primary,
                    secondaryColor = secondary,
                    statusBarColor = statusBar,
                    appBarColor = appBar,
                    historyIconColor = historyIcon,
                    pipIconColor = pipIcon,
                )
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

private fun saveSelectedThemeColor(
    shared: ThemeSettingsShared,
    currentColorPickerMode: String,
    primaryColor: Int?,
    secondaryColor: Int?,
    statusBarColor: Int?,
    appBarColor: Int?,
    historyIconColor: Int?,
    pipIconColor: Int?,
) {
    val selectedColor =
        primaryColor ?: secondaryColor ?: statusBarColor ?: appBarColor
            ?: historyIconColor ?: pipIconColor
    selectedColor?.let { shared.scope.launch { shared.preferencesManager.addRecentColor(it) } }
    shared.saveThemeSettingsWithCharacterCard {
        when (currentColorPickerMode) {
            "primary" -> primaryColor?.let { shared.preferencesManager.saveThemeSettings(customPrimaryColor = it) }
            "secondary" -> secondaryColor?.let { shared.preferencesManager.saveThemeSettings(customSecondaryColor = it) }
            "statusBar" -> statusBarColor?.let { shared.preferencesManager.saveThemeSettings(customStatusBarColor = it) }
            "appBar" -> appBarColor?.let { shared.preferencesManager.saveThemeSettings(customAppBarColor = it) }
            "historyIcon" -> historyIconColor?.let {
                shared.preferencesManager.saveThemeSettings(chatHeaderHistoryIconColor = it)
            }
            "pipIcon" -> pipIconColor?.let {
                shared.preferencesManager.saveThemeSettings(chatHeaderPipIconColor = it)
            }
        }
    }
}

