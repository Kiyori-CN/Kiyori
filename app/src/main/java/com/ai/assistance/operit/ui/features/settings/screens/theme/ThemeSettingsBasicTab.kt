package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.sections.SaveThemeSettingsAction
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsFontSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsSectionTitle
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsThemeModeSection
import com.ai.assistance.operit.ui.theme.KiyoriSemanticTone
import com.ai.assistance.operit.ui.theme.resolveColors
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
    val themeMode by
        preferencesManager.themeMode.collectAsState(
            initial = UserPreferencesManager.THEME_MODE_LIGHT,
        )
    val statusBarHidden by preferencesManager.statusBarHidden.collectAsState(initial = false)
    val useCustomFont by preferencesManager.useCustomFont.collectAsState(initial = false)
    val fontType by
        preferencesManager.fontType.collectAsState(
            initial = UserPreferencesManager.FONT_TYPE_SYSTEM,
        )
    val systemFontName by
        preferencesManager.systemFontName.collectAsState(
            initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT,
        )
    val customFontPath by preferencesManager.customFontPath.collectAsState(initial = null)
    val fontScale by preferencesManager.fontScale.collectAsState(initial = 1.0f)

    var useSystemThemeInput by remember { mutableStateOf(useSystemTheme) }
    var themeModeInput by remember { mutableStateOf(themeMode) }
    var statusBarHiddenInput by remember { mutableStateOf(statusBarHidden) }
    var useCustomFontInput by remember { mutableStateOf(useCustomFont) }
    var fontTypeInput by remember { mutableStateOf(fontType) }
    var systemFontNameInput by remember { mutableStateOf(systemFontName) }
    var customFontPathInput by remember { mutableStateOf(customFontPath) }
    var fontScaleInput by remember { mutableStateOf(fontScale) }

    LaunchedEffect(
        useSystemTheme,
        themeMode,
        statusBarHidden,
        useCustomFont,
        fontType,
        systemFontName,
        customFontPath,
        fontScale,
    ) {
        useSystemThemeInput = useSystemTheme
        themeModeInput = themeMode
        statusBarHiddenInput = statusBarHidden
        useCustomFontInput = useCustomFont
        fontTypeInput = fontType
        systemFontNameInput = systemFontName
        customFontPathInput = customFontPath
        fontScaleInput = fontScale
    }

    // 应用主题与全局字体不能进入角色卡主题，否则切换角色会改变整个设置页和应用壳。
    val saveGlobalSetting: SaveThemeSettingsAction = { action ->
        shared.scope.launch {
            action()
            onShowSaveSuccessMessage()
        }
    }

    KiyoriFixedThemeInfoCard(cardColors)

    ThemeSettingsThemeModeSection(
        cardColors = cardColors,
        useSystemThemeInput = useSystemThemeInput,
        onUseSystemThemeInputChange = { useSystemThemeInput = it },
        themeModeInput = themeModeInput,
        onThemeModeInputChange = { themeModeInput = it },
        saveThemeSettingsWithCharacterCard = saveGlobalSetting,
        preferencesManager = preferencesManager,
    )

    ThemeSettingsSectionTitle(
        title = stringResource(id = R.string.theme_statusbar),
        icon = Icons.Default.VisibilityOff,
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = cardColors,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = stringResource(id = R.string.theme_statusbar_hidden),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(id = R.string.theme_statusbar_hidden_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = statusBarHiddenInput,
                onCheckedChange = {
                    statusBarHiddenInput = it
                    saveGlobalSetting {
                        preferencesManager.saveThemeSettings(statusBarHidden = it)
                    }
                },
            )
        }
    }

    ThemeSettingsFontSection(
        cardColors = cardColors,
        context = shared.context,
        preferencesManager = preferencesManager,
        saveThemeSettingsWithCharacterCard = saveGlobalSetting,
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
        onPickFont =
            rememberGlobalFontPicker(
                context = shared.context,
                shared = shared,
                onSaved = {
                    customFontPathInput = it
                    fontTypeInput = UserPreferencesManager.FONT_TYPE_FILE
                    onShowSaveSuccessMessage()
                },
            ),
    )
}

@Composable
private fun KiyoriFixedThemeInfoCard(cardColors: CardColors) {
    val iconColors = KiyoriSemanticTone.BLUE.resolveColors()
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = cardColors,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(iconColors.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = iconColors.icon,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.theme_fixed_palette_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(id = R.string.theme_fixed_palette_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun rememberGlobalFontPicker(
    context: Context,
    shared: ThemeSettingsShared,
    onSaved: (String) -> Unit,
): () -> Unit {
    val preferencesManager = shared.preferencesManager
    val fontPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                shared.scope.launch {
                    val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                    if (extension == "ttf" || extension == "otf" || extension == "ttc") {
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, uri, "custom_font")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Font file saved to: $internalUri")
                            preferencesManager.saveThemeSettings(
                                customFontPath = internalUri.toString(),
                                fontType = UserPreferencesManager.FONT_TYPE_FILE,
                            )
                            onSaved(internalUri.toString())
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
