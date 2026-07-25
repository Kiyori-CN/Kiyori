package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsCharacterBindingInfoCard
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorContentMode
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorCustomizationSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsFontSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsThemeModeSection
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import kotlinx.coroutines.launch

@Composable
internal fun ThemeSettingsBasicTab(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
    onShowSaveSuccessMessage: () -> Unit,
) {
    val preferencesManager = shared.preferencesManager
    val useSystemTheme by preferencesManager.useSystemTheme.collectAsState(initial = false)
    val themeMode by preferencesManager.themeMode.collectAsState(
        initial = UserPreferencesManager.THEME_MODE_LIGHT,
    )
    val useCustomFont by preferencesManager.useCustomFont.collectAsState(initial = false)
    val fontType by preferencesManager.fontType.collectAsState(
        initial = UserPreferencesManager.FONT_TYPE_SYSTEM,
    )
    val systemFontName by preferencesManager.systemFontName.collectAsState(
        initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT,
    )
    val customFontPath by preferencesManager.customFontPath.collectAsState(initial = null)
    val fontScale by preferencesManager.fontScale.collectAsState(initial = 1.0f)
    val pickGlobalFont = rememberGlobalFontPicker(
        context = shared.context,
        shared = shared,
    )
    var useSystemThemeInput by remember { mutableStateOf(useSystemTheme) }
    var themeModeInput by remember { mutableStateOf(themeMode) }
    var useCustomFontInput by remember { mutableStateOf(useCustomFont) }
    var fontTypeInput by remember { mutableStateOf(fontType) }
    var systemFontNameInput by remember { mutableStateOf(systemFontName) }
    var customFontPathInput by remember { mutableStateOf(customFontPath) }
    var fontScaleInput by remember { mutableStateOf(fontScale) }

    LaunchedEffect(
        useSystemTheme,
        themeMode,
        useCustomFont,
        fontType,
        systemFontName,
        customFontPath,
        fontScale,
    ) {
        useSystemThemeInput = useSystemTheme
        themeModeInput = themeMode
        useCustomFontInput = useCustomFont
        fontTypeInput = fontType
        systemFontNameInput = systemFontName
        customFontPathInput = customFontPath
        fontScaleInput = fontScale
    }

    ThemeSettingsCharacterBindingInfoCard(
        aiAvatarUri = shared.activeThemeTargetAvatarUri,
        activeCharacterName = shared.activeThemeTargetName,
        isGroupTarget = shared.isGroupThemeTarget,
        cardColors = cardColors,
    )

    ThemeSettingsThemeModeSection(
        cardColors = cardColors,
        useSystemThemeInput = useSystemThemeInput,
        onUseSystemThemeInputChange = { useSystemThemeInput = it },
        themeModeInput = themeModeInput,
        onThemeModeInputChange = { themeModeInput = it },
        saveThemeSettingsWithCharacterCard = shared.saveThemeSettingsWithCharacterCard,
        preferencesManager = preferencesManager,
    )

    ThemeSettingsBasicColorPanel(
        shared = shared,
        cardColors = cardColors,
        onShowSaveSuccessMessage = onShowSaveSuccessMessage,
    )

    ThemeSettingsFontSection(
        cardColors = cardColors,
        context = shared.context,
        preferencesManager = preferencesManager,
        saveThemeSettingsWithCharacterCard = shared.saveThemeSettingsWithCharacterCard,
        useCustomFontInput = useCustomFontInput,
        onUseCustomFontInputChange = { useCustomFontInput = it },
        fontTypeInput = fontTypeInput,
        onFontTypeInputChange = { fontTypeInput = it },
        systemFontNameInput = systemFontNameInput,
        onSystemFontNameInputChange = { systemFontNameInput = it },
        customFontPathInput = customFontPathInput,
        onCustomFontPathInputChange = { customFontPathInput = it },
        fontScaleInput = fontScaleInput,
        onFontScaleInputChange = { fontScaleInput = it },
        onPickFont = pickGlobalFont,
    )
}

@Composable
private fun rememberGlobalFontPicker(
    context: Context,
    shared: ThemeSettingsShared,
): () -> Unit {
    val preferencesManager = shared.preferencesManager
    val fontPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                shared.scope.launch {
                    val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                    if (extension != null && (extension == "ttf" || extension == "otf" || extension == "ttc")) {
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, uri, "custom_font")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Font file saved to: $internalUri")
                            shared.saveThemeSettingsWithCharacterCard {
                                preferencesManager.saveThemeSettings(
                                    customFontPath = internalUri.toString(),
                                    fontType = UserPreferencesManager.FONT_TYPE_FILE,
                                )
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.font_file_saved, extension),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.font_file_save_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.unsupported_font_format),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    return { fontPickerLauncher.launch("*/*") }
}


@Composable
private fun ThemeSettingsBasicColorPanel(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
    onShowSaveSuccessMessage: () -> Unit,
) {
    val preferencesManager = shared.preferencesManager
    val defaultPrimaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val defaultSecondaryColor = MaterialTheme.colorScheme.secondary.toArgb()
    val defaultAppBarColor = MaterialTheme.colorScheme.surface.toArgb()
    val defaultHeaderIconColor = Color.Gray.toArgb()

    val useCustomColors by preferencesManager.useCustomColors.collectAsState(initial = false)
    val primaryColor by preferencesManager.customPrimaryColor.collectAsState(initial = null)
    val secondaryColor by preferencesManager.customSecondaryColor.collectAsState(initial = null)
    val statusBarHidden by preferencesManager.statusBarHidden.collectAsState(initial = false)
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
        contentMode = ThemeSettingsColorContentMode.PALETTE,
    )

    if (showColorPicker) {
        ColorPickerDialog(
            showColorPicker = showColorPicker,
            currentColorPickerMode = currentColorPickerMode,
            primaryColorInput = primaryColorInput,
            secondaryColorInput = secondaryColorInput,
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
    appBarColor: Int?,
    historyIconColor: Int?,
    pipIconColor: Int?,
) {
    val selectedColor =
        primaryColor ?: secondaryColor ?: appBarColor
            ?: historyIconColor ?: pipIconColor
    selectedColor?.let { shared.scope.launch { shared.preferencesManager.addRecentColor(it) } }
    shared.saveThemeSettingsWithCharacterCard {
        when (currentColorPickerMode) {
            "primary" -> primaryColor?.let { shared.preferencesManager.saveThemeSettings(customPrimaryColor = it) }
            "secondary" -> secondaryColor?.let { shared.preferencesManager.saveThemeSettings(customSecondaryColor = it) }
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

