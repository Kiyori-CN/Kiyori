package com.ai.assistance.operit.ui.main.shell

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.PersonalWakeEnrollment
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarControllerFactoryImpl
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.ui.features.assistant.components.AvatarConfigSection
import com.ai.assistance.operit.ui.features.assistant.components.AvatarPreviewSection
import com.ai.assistance.operit.ui.features.assistant.components.VoiceAutoAttachGrid
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import com.ai.assistance.operit.ui.main.components.LocalKiyoriEmbeddedSettingsNavigation
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.design.theme.KiyoriSemanticTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val KIYORI_AVATAR_SETTINGS_PAGE_TITLE_RES = R.string.kiyori_avatar_settings_title
internal val KIYORI_VOICE_WAKEUP_SETTINGS_PAGE_TITLE_RES =
    R.string.kiyori_voice_wakeup_settings_title

@Composable
internal fun KiyoriAvatarSettingsPage(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navigation = LocalKiyoriEmbeddedSettingsNavigation.current
    val viewModel: AssistantConfigViewModel =
        viewModel(factory = AssistantConfigViewModel.Factory(context))
    val uiState by viewModel.uiState.collectAsState()
    val avatarControllerFactory = remember { AvatarControllerFactoryImpl() }
    val avatarController =
        uiState.currentAvatarModel?.let { model ->
            avatarControllerFactory.createController(model)
        }
    val snackbarHostState = remember { SnackbarHostState() }
    var isPreviewCollapsed by rememberSaveable { mutableStateOf(false) }

    val zipFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let(viewModel::importAvatarFromZip)
            }
        }

    val openAvatarPicker = {
        zipFileLauncher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "model/vnd.autodesk.fbx",
                        "model/fbx",
                        "application/fbx",
                        "model/gltf-binary",
                        "model/gltf+json",
                        "video/mp4",
                        "application/octet-stream",
                    ),
                )
            },
        )
    }

    LaunchedEffect(uiState.operationSuccess, uiState.errorMessage) {
        when {
            uiState.operationSuccess -> {
                snackbarHostState.showSnackbar(context.getString(R.string.operation_success))
                viewModel.clearOperationSuccess()
            }
            uiState.errorMessage != null -> {
                snackbarHostState.showSnackbar(
                    uiState.errorMessage ?: context.getString(R.string.error_occurred_simple),
                )
                viewModel.clearErrorMessage()
            }
        }
    }

    Box(modifier = modifier) {
        KiyoriCollapsingSettingsPage(
            title = stringResource(KIYORI_AVATAR_SETTINGS_PAGE_TITLE_RES),
            onBack = navigation.onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "avatar_preview") {
                if (!isPreviewCollapsed) {
                    AvatarPreviewSection(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(horizontal = 15.dp),
                        uiState = uiState,
                        avatarController = avatarController,
                        onPreviewError = viewModel::updateErrorMessage,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        onClick = { isPreviewCollapsed = !isPreviewCollapsed },
                    ) {
                        Icon(
                            imageVector =
                                if (isPreviewCollapsed) {
                                    Icons.Default.ExpandMore
                                } else {
                                    Icons.Default.ExpandLess
                                },
                            contentDescription =
                                stringResource(
                                    if (isPreviewCollapsed) {
                                        R.string.model_config_expand
                                    } else {
                                        R.string.model_config_collapse
                                    },
                                ),
                        )
                    }
                }
            }
            item(key = "avatar_configuration") {
                KiyoriSettingsGroupSection(
                    title = stringResource(R.string.avatar_config),
                    description = stringResource(R.string.kiyori_avatar_configuration_desc),
                ) {
                    AvatarConfigSection(
                        viewModel = viewModel,
                        uiState = uiState,
                        avatarController = avatarController,
                        onImportClick = openAvatarPicker,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
        )

        if (uiState.isLoading || uiState.isImporting) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                        .zIndex(2f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text =
                            if (uiState.isImporting) {
                                stringResource(R.string.importing_model)
                            } else {
                                stringResource(R.string.processing)
                            },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KiyoriVoiceWakeupSettingsPage(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navigation = LocalKiyoriEmbeddedSettingsNavigation.current
    val wakePrefs = remember { WakeWordPreferences(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val wakeListeningEnabled by
        wakePrefs.alwaysListeningEnabledFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_ALWAYS_LISTENING_ENABLED,
        )
    val wakePhrase by
        wakePrefs.wakePhraseFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE,
        )
    val wakePhraseRegexEnabled by
        wakePrefs.wakePhraseRegexEnabledFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE_REGEX_ENABLED,
        )
    val wakeRecognitionMode by
        wakePrefs.wakeRecognitionModeFlow.collectAsState(
            initial = WakeWordPreferences.WakeRecognitionMode.STT,
        )
    val personalWakeTemplates by
        wakePrefs.personalWakeTemplatesFlow.collectAsState(initial = emptyList())
    val inactivityTimeoutSeconds by
        wakePrefs.voiceCallInactivityTimeoutSecondsFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS,
        )
    val wakeGreetingEnabled by
        wakePrefs.wakeGreetingEnabledFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_ENABLED,
        )
    val wakeGreetingText by
        wakePrefs.wakeGreetingTextFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT,
        )
    val wakeCreateNewChatOnWakeEnabled by
        wakePrefs.wakeCreateNewChatOnWakeEnabledFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_WAKE_CREATE_NEW_CHAT_ON_WAKE_ENABLED,
        )
    val autoNewChatGroup by
        wakePrefs.autoNewChatGroupFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP,
        )
    val voiceAutoAttachEnabled by
        wakePrefs.voiceAutoAttachEnabledFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_VOICE_AUTO_ATTACH_ENABLED,
        )
    val voiceAutoAttachItems by
        wakePrefs.voiceAutoAttachItemsFlow.collectAsState(
            initial = WakeWordPreferences.getDefaultVoiceAutoAttachItems(context),
        )

    var wakePhraseInput by rememberSaveable { mutableStateOf("") }
    var inactivityTimeoutInput by rememberSaveable { mutableStateOf("") }
    var wakeGreetingTextInput by rememberSaveable { mutableStateOf("") }
    var autoNewChatGroupInput by rememberSaveable { mutableStateOf("") }
    var modeExpanded by rememberSaveable { mutableStateOf(false) }
    var personalWakeConfigDialogVisible by rememberSaveable { mutableStateOf(false) }
    var clearPersonalWakeConfirmVisible by rememberSaveable { mutableStateOf(false) }

    fun updateWakeSetting(
        operationName: String,
        onSuccess: () -> Unit = {},
        action: suspend () -> Unit,
    ) {
        coroutineScope.launch {
            try {
                action()
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(
                    "KiyoriVoiceWakeupSettings",
                    "$operationName failed",
                    error,
                )
                snackbarHostState.showSnackbar(
                    context.getString(R.string.voice_wakeup_update_failed),
                )
            }
        }
    }

    LaunchedEffect(wakePrefs) {
        try {
            wakePrefs.migrateVoiceAutoAttachItemsIfNeeded()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e(
                "KiyoriVoiceWakeupSettings",
                "Voice auto-attachment migration failed",
                error,
            )
            snackbarHostState.showSnackbar(
                context.getString(R.string.voice_wakeup_update_failed),
            )
        }
    }

    LaunchedEffect(wakePhrase) {
        if (wakePhraseInput.isBlank()) {
            wakePhraseInput = wakePhrase
        }
    }
    LaunchedEffect(inactivityTimeoutSeconds) {
        if (inactivityTimeoutInput.isBlank()) {
            inactivityTimeoutInput = inactivityTimeoutSeconds.toString()
        }
    }
    LaunchedEffect(wakeGreetingText) {
        if (wakeGreetingTextInput.isBlank()) {
            wakeGreetingTextInput = wakeGreetingText
        }
    }
    LaunchedEffect(autoNewChatGroup) {
        if (autoNewChatGroupInput.isBlank()) {
            autoNewChatGroupInput = autoNewChatGroup
        }
    }

    val requestMicPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                updateWakeSetting("Enable background wake listening") {
                    wakePrefs.saveAlwaysListeningEnabled(true)
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.microphone_permission_denied_toast),
                    )
                }
            }
        }

    Box(modifier = modifier) {
        KiyoriCollapsingSettingsPage(
            title = stringResource(KIYORI_VOICE_WAKEUP_SETTINGS_PAGE_TITLE_RES),
            onBack = navigation.onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "wake_recognition") {
                KiyoriSettingsGroupSection(
                    title = stringResource(R.string.kiyori_voice_wakeup_recognition_title),
                    description =
                        stringResource(R.string.kiyori_voice_wakeup_recognition_desc),
                ) {
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { modeExpanded = it },
                        modifier =
                            Modifier.padding(
                                horizontal = KIYORI_SETTINGS_FIELD_HORIZONTAL_PADDING_DP.dp,
                                vertical = 8.dp,
                            ),
                    ) {
                        val modeLabel =
                            when (wakeRecognitionMode) {
                                WakeWordPreferences.WakeRecognitionMode.STT ->
                                    stringResource(R.string.voice_wakeup_mode_stt)
                                WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE ->
                                    stringResource(R.string.voice_wakeup_mode_personal)
                            }
                        OutlinedTextField(
                            modifier =
                                Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            value = modeLabel,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text(stringResource(R.string.voice_wakeup_mode_label)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded)
                            },
                            colors =
                                kiyoriSettingsOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.voice_wakeup_mode_stt)) },
                                onClick = {
                                    modeExpanded = false
                                    updateWakeSetting("Select STT wake recognition") {
                                        wakePrefs.saveWakeRecognitionMode(
                                            WakeWordPreferences.WakeRecognitionMode.STT,
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.voice_wakeup_mode_personal)) },
                                onClick = {
                                    modeExpanded = false
                                    updateWakeSetting("Select personal wake recognition") {
                                        wakePrefs.saveWakeRecognitionMode(
                                            WakeWordPreferences.WakeRecognitionMode.PERSONAL_TEMPLATE,
                                        )
                                    }
                                },
                            )
                        }
                    }

                    KiyoriSettingsRow(
                        title = stringResource(R.string.voice_wakeup_always_listen_title),
                        description = stringResource(R.string.voice_wakeup_always_listen_desc),
                        kind = KiyoriSettingsRowKind.TOGGLE,
                        icon = Icons.Default.Mic,
                        iconTone = KiyoriSemanticTone.BLUE,
                        checked = wakeListeningEnabled,
                        onClick = {
                            if (!wakeListeningEnabled) {
                                val granted =
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    updateWakeSetting("Enable background wake listening") {
                                        wakePrefs.saveAlwaysListeningEnabled(true)
                                    }
                                } else {
                                    requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                updateWakeSetting("Disable background wake listening") {
                                    wakePrefs.saveAlwaysListeningEnabled(false)
                                }
                            }
                        },
                    )

                    if (wakeRecognitionMode == WakeWordPreferences.WakeRecognitionMode.STT) {
                        OutlinedTextField(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = KIYORI_SETTINGS_FIELD_HORIZONTAL_PADDING_DP.dp,
                                        vertical = 8.dp,
                                    ),
                            value = wakePhraseInput,
                            onValueChange = { newValue ->
                                wakePhraseInput = newValue
                                updateWakeSetting("Update wake phrase") {
                                    wakePrefs.saveWakePhrase(
                                        newValue.ifBlank {
                                            WakeWordPreferences.DEFAULT_WAKE_PHRASE
                                        },
                                    )
                                }
                            },
                            singleLine = true,
                            label = { Text(stringResource(R.string.voice_wakeup_phrase_label)) },
                            placeholder = {
                                Text(stringResource(R.string.voice_wakeup_phrase_supporting))
                            },
                            colors =
                                kiyoriSettingsOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP.dp),
                        )
                        KiyoriSettingsRow(
                            title = stringResource(R.string.voice_wakeup_regex_title),
                            description = stringResource(R.string.voice_wakeup_regex_desc),
                            kind = KiyoriSettingsRowKind.TOGGLE,
                            icon = Icons.Default.Tune,
                            iconTone = KiyoriSemanticTone.GREEN,
                            checked = wakePhraseRegexEnabled,
                            onClick = {
                                updateWakeSetting("Update wake phrase regex mode") {
                                    wakePrefs.saveWakePhraseRegexEnabled(!wakePhraseRegexEnabled)
                                }
                            },
                        )
                    } else {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal =
                                            KIYORI_SETTINGS_FIELD_HORIZONTAL_PADDING_DP.dp,
                                        vertical = 8.dp,
                                    ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { personalWakeConfigDialogVisible = true },
                            ) {
                                Text(stringResource(R.string.voice_wakeup_personal_configure))
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = personalWakeTemplates.isNotEmpty(),
                                onClick = { clearPersonalWakeConfirmVisible = true },
                            ) {
                                Text(stringResource(R.string.voice_wakeup_personal_clear))
                            }
                        }
                        if (personalWakeTemplates.isEmpty()) {
                            Text(
                                text = stringResource(R.string.voice_wakeup_personal_no_templates),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.padding(
                                        start = 18.dp,
                                        end = 18.dp,
                                        bottom = 8.dp,
                                    ),
                            )
                        }
                    }
                }
            }

            item(key = "wake_response") {
                KiyoriSettingsGroupSection(
                    title = stringResource(R.string.kiyori_voice_wakeup_response_title),
                    description = stringResource(R.string.kiyori_voice_wakeup_response_desc),
                ) {
                    OutlinedTextField(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = KIYORI_SETTINGS_FIELD_HORIZONTAL_PADDING_DP.dp,
                                    vertical = 8.dp,
                                ),
                        value = inactivityTimeoutInput,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter(Char::isDigit)
                            val parsed = filtered.toIntOrNull()
                            if (parsed == null) {
                                inactivityTimeoutInput = filtered
                            } else {
                                val bounded = parsed.coerceIn(1, 600)
                                inactivityTimeoutInput = bounded.toString()
                                updateWakeSetting("Update voice inactivity timeout") {
                                    wakePrefs.saveVoiceCallInactivityTimeoutSeconds(
                                        bounded,
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        label = {
                            Text(stringResource(R.string.voice_wakeup_inactivity_timeout_label))
                        },
                        placeholder = {
                            Text(stringResource(R.string.voice_wakeup_inactivity_timeout_supporting))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors =
                            kiyoriSettingsOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP.dp),
                    )
                    KiyoriSettingsRow(
                        title = stringResource(R.string.voice_wakeup_greeting_title),
                        description = stringResource(R.string.voice_wakeup_greeting_desc),
                        kind = KiyoriSettingsRowKind.TOGGLE,
                        icon = Icons.Default.RecordVoiceOver,
                        iconTone = KiyoriSemanticTone.CYAN,
                        checked = wakeGreetingEnabled,
                        onClick = {
                            updateWakeSetting("Update wake greeting") {
                                wakePrefs.saveWakeGreetingEnabled(!wakeGreetingEnabled)
                            }
                        },
                    )
                    OutlinedTextField(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = KIYORI_SETTINGS_FIELD_HORIZONTAL_PADDING_DP.dp,
                                    vertical = 8.dp,
                                ),
                        value = wakeGreetingTextInput,
                        onValueChange = { newValue ->
                            wakeGreetingTextInput = newValue
                            updateWakeSetting("Update wake greeting text") {
                                wakePrefs.saveWakeGreetingText(
                                    newValue.ifBlank {
                                        WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT
                                    },
                                )
                            }
                        },
                        enabled = wakeGreetingEnabled,
                        singleLine = true,
                        label = { Text(stringResource(R.string.voice_wakeup_greeting_text_label)) },
                        placeholder = {
                            Text(stringResource(R.string.voice_wakeup_greeting_text_supporting))
                        },
                        colors =
                            kiyoriSettingsOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP.dp),
                    )
                    KiyoriSettingsRow(
                        title = stringResource(R.string.voice_wakeup_create_new_chat_title),
                        description = stringResource(R.string.voice_wakeup_create_new_chat_desc),
                        kind = KiyoriSettingsRowKind.TOGGLE,
                        icon = Icons.Default.Tune,
                        iconTone = KiyoriSemanticTone.ORANGE,
                        checked = wakeCreateNewChatOnWakeEnabled,
                        onClick = {
                            updateWakeSetting("Update wake chat creation") {
                                wakePrefs.saveWakeCreateNewChatOnWakeEnabled(
                                    !wakeCreateNewChatOnWakeEnabled,
                                )
                            }
                        },
                    )
                    OutlinedTextField(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = KIYORI_SETTINGS_FIELD_HORIZONTAL_PADDING_DP.dp,
                                    vertical = 8.dp,
                                ),
                        value = autoNewChatGroupInput,
                        onValueChange = { newValue ->
                            autoNewChatGroupInput = newValue
                            updateWakeSetting("Update wake chat group") {
                                wakePrefs.saveAutoNewChatGroup(
                                    newValue.ifBlank {
                                        WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP
                                    },
                                )
                            }
                        },
                        enabled = wakeCreateNewChatOnWakeEnabled,
                        singleLine = true,
                        label = {
                            Text(stringResource(R.string.voice_wakeup_auto_new_chat_group_label))
                        },
                        placeholder = {
                            Text(
                                stringResource(
                                    R.string.voice_wakeup_auto_new_chat_group_supporting,
                                    WakeWordPreferences.DEFAULT_AUTO_NEW_CHAT_GROUP,
                                ),
                            )
                        },
                        colors =
                            kiyoriSettingsOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP.dp),
                    )
                }
            }

            item(key = "wake_attachments") {
                KiyoriSettingsGroupSection(
                    title = stringResource(R.string.kiyori_voice_wakeup_attachments_title),
                    description =
                        stringResource(R.string.kiyori_voice_wakeup_attachments_desc),
                ) {
                    KiyoriSettingsRow(
                        title = stringResource(R.string.voice_keyword_attachments_enabled_title),
                        description = stringResource(R.string.voice_keyword_attachments_enabled_desc),
                        kind = KiyoriSettingsRowKind.TOGGLE,
                        icon = Icons.Default.RecordVoiceOver,
                        iconTone = KiyoriSemanticTone.PURPLE,
                        checked = voiceAutoAttachEnabled,
                        onClick = {
                            updateWakeSetting("Update voice auto-attachments") {
                                wakePrefs.saveVoiceAutoAttachEnabled(!voiceAutoAttachEnabled)
                            }
                        },
                    )
                    if (voiceAutoAttachEnabled) {
                        VoiceAutoAttachGrid(
                            items = voiceAutoAttachItems,
                            onItemsChange = { newItems ->
                                updateWakeSetting("Update voice auto-attachment items") {
                                    wakePrefs.saveVoiceAutoAttachItems(newItems)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (personalWakeConfigDialogVisible) {
            PersonalWakeConfigDialog(
                context = context,
                coroutineScope = coroutineScope,
                onDismiss = { personalWakeConfigDialogVisible = false },
                onSave = { templates ->
                    updateWakeSetting(
                        operationName = "Save personal wake templates",
                        onSuccess = { personalWakeConfigDialogVisible = false },
                    ) {
                        wakePrefs.savePersonalWakeTemplates(templates)
                    }
                },
            )
        }

        if (clearPersonalWakeConfirmVisible) {
            AlertDialog(
                onDismissRequest = { clearPersonalWakeConfirmVisible = false },
                title = {
                    Text(stringResource(R.string.voice_wakeup_personal_clear_confirm_title))
                },
                text = {
                    Text(stringResource(R.string.voice_wakeup_personal_clear_confirm_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            updateWakeSetting(
                                operationName = "Clear personal wake templates",
                                onSuccess = { clearPersonalWakeConfirmVisible = false },
                            ) {
                                wakePrefs.savePersonalWakeTemplates(emptyList())
                            }
                        },
                    ) {
                        Text(stringResource(R.string.voice_wakeup_personal_clear))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clearPersonalWakeConfirmVisible = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun PersonalWakeConfigDialog(
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
    onSave: (List<WakeWordPreferences.PersonalWakeTemplate>) -> Unit,
) {
    var step1 by remember { mutableStateOf<FloatArray?>(null) }
    var step2 by remember { mutableStateOf<FloatArray?>(null) }
    var step3 by remember { mutableStateOf<FloatArray?>(null) }
    var recordingStep by remember { mutableIntStateOf(0) }
    var recordingError by remember { mutableStateOf<String?>(null) }
    val canSave = step1 != null && step2 != null && step3 != null && recordingStep == 0

    AlertDialog(
        onDismissRequest = { if (recordingStep == 0) onDismiss() },
        title = { Text(stringResource(R.string.voice_wakeup_personal_config_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.voice_wakeup_personal_config_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                recordingError?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                listOf(
                    1 to step1,
                    2 to step2,
                    3 to step3,
                ).forEach { (index, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.voice_wakeup_personal_config_step, index))
                        val label =
                            when {
                                recordingStep == index ->
                                    stringResource(R.string.voice_wakeup_personal_config_recording)
                                value != null ->
                                    stringResource(R.string.voice_wakeup_personal_config_record_done)
                                else ->
                                    stringResource(R.string.voice_wakeup_personal_config_record)
                            }
                        Button(
                            onClick = {
                                recordingStep = index
                                recordingError = null
                                coroutineScope.launch {
                                    try {
                                        val feature =
                                            PersonalWakeEnrollment.recordOneTemplate(context)
                                        when (index) {
                                            1 -> step1 = feature
                                            2 -> step2 = feature
                                            3 -> step3 = feature
                                        }
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        AppLogger.e(
                                            "PersonalWakeConfigDialog",
                                            "Personal wake template recording failed",
                                            error,
                                        )
                                        recordingError =
                                            context.getString(
                                                R.string.voice_wakeup_personal_record_failed,
                                            )
                                    } finally {
                                        recordingStep = 0
                                    }
                                }
                            },
                            enabled = recordingStep == 0,
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        listOfNotNull(step1, step2, step3).map { features ->
                            WakeWordPreferences.PersonalWakeTemplate(features = features.toList())
                        },
                    )
                },
            ) {
                Text(stringResource(R.string.voice_wakeup_personal_config_save))
            }
        },
        dismissButton = {
            TextButton(
                enabled = recordingStep == 0,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.voice_wakeup_personal_config_cancel))
            }
        },
    )
}
