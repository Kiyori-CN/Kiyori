package com.ai.assistance.operit.ui.features.packages.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.NetworkLocked
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.javascript.network.MihomoConfigSanitizer
import com.ai.assistance.operit.core.tools.javascript.network.MihomoSubscriptionClient
import com.ai.assistance.operit.core.tools.javascript.network.ScriptNetworkConfig
import com.ai.assistance.operit.core.tools.javascript.network.ScriptNetworkConfigStore
import com.ai.assistance.operit.core.tools.javascript.network.ScriptNetworkGlobalMode
import com.ai.assistance.operit.core.tools.javascript.network.ScriptNetworkHttpClientFactory
import com.ai.assistance.operit.core.tools.javascript.network.ScriptNetworkPolicy
import com.ai.assistance.operit.core.tools.javascript.network.ScriptNetworkScriptMode
import com.ai.assistance.operit.core.tools.javascript.network.ScriptNetworkStoreState
import com.ai.assistance.operit.core.tools.javascript.network.ScriptProxyRuntime
import com.ai.assistance.operit.core.tools.javascript.network.ScriptProxyRuntimePhase
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class ScriptNetworkPackageItem(
    val packageName: String,
    val displayName: String,
    val category: String,
)

@Composable
internal fun ScriptSettingsChooserDialog(
    onEnvironmentClick: () -> Unit,
    onNetworkProxyClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.script_settings)) },
        text = {
            Column {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.script_settings_environment))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.script_settings_environment_summary))
                    },
                    leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    modifier =
                        Modifier.clickable {
                            onDismiss()
                            onEnvironmentClick()
                        },
                )
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.script_settings_network))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.script_settings_network_summary))
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.NetworkLocked, contentDescription = null)
                    },
                    modifier =
                        Modifier.clickable {
                            onDismiss()
                            onNetworkProxyClick()
                        },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.script_network_close))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScriptNetworkSettingsDialog(
    packages: List<ScriptNetworkPackageItem>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ScriptNetworkConfigStore.getInstance(context) }
    val runtime = remember { ScriptProxyRuntime.getInstance(context) }
    val networkFactory = remember { ScriptNetworkHttpClientFactory.getInstance(context) }
    val subscriptionClient = remember { MihomoSubscriptionClient() }
    val storeState by store.state.collectAsState()
    val runtimeState by runtime.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val readyConfig = (storeState as? ScriptNetworkStoreState.Ready)?.config
    var draft by remember { mutableStateOf(readyConfig ?: ScriptNetworkConfig()) }
    var originalConfig by remember { mutableStateOf(readyConfig) }
    var isDirty by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var draftRuntimeTouched by remember { mutableStateOf(false) }
    var packageQuery by remember { mutableStateOf("") }
    var showSubscriptionUrl by remember { mutableStateOf(false) }
    var showExternalPassword by remember { mutableStateOf(false) }
    var showYamlEditor by remember { mutableStateOf(false) }
    var yamlEditorText by remember { mutableStateOf("") }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showResetOverridesConfirmation by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var isSystemVpnActive by remember { mutableStateOf(networkFactory.isSystemVpnActive()) }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(readyConfig) {
        if (!isDirty && readyConfig != null) {
            draft = readyConfig
            originalConfig = readyConfig
        }
    }

    fun updateDraft(updated: ScriptNetworkConfig) {
        draft = updated
        isDirty = updated != originalConfig
    }

    suspend fun saveDraft(): Boolean {
        return try {
            ScriptNetworkPolicy.validateTestUrl(draft.testUrl)
            val selectedModes =
                buildSet {
                    add(draft.globalMode)
                    draft.scriptModes.values.forEach { mode ->
                        when (mode) {
                            ScriptNetworkScriptMode.INHERIT -> Unit
                            ScriptNetworkScriptMode.DIRECT -> add(ScriptNetworkGlobalMode.DIRECT)
                            ScriptNetworkScriptMode.EXTERNAL_PROXY ->
                                add(ScriptNetworkGlobalMode.EXTERNAL_PROXY)
                            ScriptNetworkScriptMode.EMBEDDED_SUBSCRIPTION ->
                                add(ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION)
                        }
                    }
                }
            if (ScriptNetworkGlobalMode.EXTERNAL_PROXY in selectedModes) {
                ScriptNetworkPolicy.resolve(
                    config = draft.copy(globalMode = ScriptNetworkGlobalMode.EXTERNAL_PROXY),
                    packageName = "__settings_validation__",
                    isSystemVpnActive = false,
                )
            }
            if (ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION in selectedModes) {
                ScriptNetworkPolicy.resolve(
                    config = draft.copy(globalMode = ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION),
                    packageName = "__settings_validation__",
                    isSystemVpnActive = false,
                )
            }
            val saved = withContext(Dispatchers.IO) { store.replace(draft) }
            val embeddedRuntimeMustStop =
                originalConfig?.embeddedProxy?.sanitizedYaml != saved.embeddedProxy.sanitizedYaml ||
                    !saved.usesEmbeddedRuntime() ||
                    (isSystemVpnActive && !saved.allowEmbeddedUnderVpn)
            if (embeddedRuntimeMustStop) {
                networkFactory.stopEmbeddedRuntime()
            }
            draft = saved
            originalConfig = saved
            isDirty = false
            draftRuntimeTouched = false
            true
        } catch (error: Exception) {
            notify(error.message ?: context.getString(R.string.script_network_save_failed))
            false
        }
    }

    fun requestDismiss() {
        if (isDirty) {
            showDiscardConfirmation = true
        } else {
            onDismiss()
        }
    }

    val yamlPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                isBusy = true
                try {
                    val text =
                        withContext(Dispatchers.IO) {
                            val resolver = context.contentResolver
                            val declaredSize =
                                resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                                    descriptor.statSize
                                } ?: -1L
                            if (declaredSize > MihomoConfigSanitizer.MAX_YAML_BYTES) {
                                error(context.getString(R.string.script_network_yaml_too_large))
                            }
                            resolver.openInputStream(uri)?.use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (output.size() + read > MihomoConfigSanitizer.MAX_YAML_BYTES) {
                                        error(context.getString(R.string.script_network_yaml_too_large))
                                    }
                                    output.write(buffer, 0, read)
                                }
                                MihomoConfigSanitizer.decodeUtf8(
                                    output.toByteArray(),
                                    com.ai.assistance.operit.core.tools.javascript.network
                                        .ScriptNetworkErrorCode.CONFIG_INVALID,
                                )
                            } ?: error(context.getString(R.string.script_network_yaml_read_failed))
                        }
                    val sanitized = withContext(Dispatchers.Default) {
                        MihomoConfigSanitizer.sanitize(text)
                    }
                    updateDraft(
                        draft.copy(
                                embeddedProxy =
                                    draft.embeddedProxy.copy(subscriptionUrl = ""),
                            )
                            .withSanitizedSubscription(sanitized.yaml, sanitized.summary),
                    )
                    notify(context.getString(R.string.script_network_yaml_imported))
                } catch (error: Exception) {
                    notify(
                        error.message
                            ?: context.getString(R.string.script_network_yaml_import_failed),
                    )
                } finally {
                    isBusy = false
                }
            }
        }

    Dialog(
        onDismissRequest = { if (!isBusy) requestDismiss() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.script_network_title)) },
                        navigationIcon = {
                            IconButton(onClick = ::requestDismiss, enabled = !isBusy) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.script_network_back),
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isBusy = true
                                        if (saveDraft()) {
                                            notify(context.getString(R.string.script_network_saved))
                                        }
                                        isBusy = false
                                    }
                                },
                                enabled = !isBusy && isDirty,
                            ) {
                                Icon(
                                    Icons.Outlined.Save,
                                    contentDescription = stringResource(R.string.script_network_save),
                                )
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                when (val currentStoreState = storeState) {
                    is ScriptNetworkStoreState.Unreadable -> {
                        Column(
                            modifier =
                                Modifier.fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.script_network_config_unreadable),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(stringResource(R.string.script_network_config_unreadable_summary))
                            Button(onClick = { showResetConfirmation = true }) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.script_network_reset_settings))
                            }
                        }
                    }
                    is ScriptNetworkStoreState.Ready -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                ScriptNetworkSection(
                                    title = stringResource(R.string.script_network_section_status),
                                ) {
                                    ListItem(
                                        headlineContent = {
                                            Text(runtimeState.phase.toDisplayText())
                                        },
                                        supportingContent = {
                                            Text(
                                                runtimeState.message
                                                    ?: if (isSystemVpnActive) {
                                                        stringResource(R.string.script_network_vpn_detected)
                                                    } else {
                                                        stringResource(R.string.script_network_vpn_not_detected)
                                                    },
                                            )
                                        },
                                        leadingContent = {
                                            if (isBusy || runtimeState.phase == ScriptProxyRuntimePhase.STARTING) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(22.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Outlined.NetworkCheck,
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            IconButton(
                                                enabled = !isBusy,
                                                onClick = {
                                                    isSystemVpnActive = networkFactory.isSystemVpnActive()
                                                    if (
                                                        isSystemVpnActive &&
                                                            !draft.allowEmbeddedUnderVpn &&
                                                            runtimeState.phase != ScriptProxyRuntimePhase.STOPPED
                                                    ) {
                                                        scope.launch {
                                                            networkFactory.stopEmbeddedRuntime()
                                                            draftRuntimeTouched = false
                                                            notify(
                                                                context.getString(
                                                                    R.string.script_network_vpn_core_stopped,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Refresh,
                                                    contentDescription =
                                                        stringResource(
                                                            R.string.script_network_refresh_status,
                                                        ),
                                                )
                                            }
                                        },
                                    )
                                    Text(
                                        stringResource(
                                            R.string.script_network_current_global_mode,
                                            draft.globalMode.toDisplayText(),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (draft.embeddedProxy.updatedAtEpochMillis > 0L) {
                                        Text(
                                            stringResource(
                                                R.string.script_network_subscription_updated_at,
                                                DateFormat.getDateTimeInstance().format(
                                                    Date(draft.embeddedProxy.updatedAtEpochMillis),
                                                ),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            item {
                                ScriptNetworkSection(
                                    title = stringResource(R.string.script_network_section_global_mode),
                                ) {
                                    GlobalModeSelector(
                                        selected = draft.globalMode,
                                        onSelected = { mode -> updateDraft(draft.copy(globalMode = mode)) },
                                    )
                                    Text(
                                        text = draft.globalMode.supportingText(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            item {
                                ScriptNetworkSection(
                                    title = stringResource(R.string.script_network_section_external),
                                ) {
                                    OutlinedTextField(
                                        value = draft.externalProxy.host,
                                        onValueChange = { value ->
                                            updateDraft(
                                                draft.copy(
                                                    externalProxy =
                                                        draft.externalProxy.copy(host = value),
                                                ),
                                            )
                                        },
                                        label = { Text(stringResource(R.string.script_network_host)) },
                                        placeholder = { Text("127.0.0.1") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value =
                                            draft.externalProxy.port.takeIf { it > 0 }?.toString().orEmpty(),
                                        onValueChange = { value ->
                                            updateDraft(
                                                draft.copy(
                                                    externalProxy =
                                                        draft.externalProxy.copy(
                                                            port = value.filter(Char::isDigit).toIntOrNull() ?: 0,
                                                        ),
                                                ),
                                            )
                                        },
                                        label = { Text(stringResource(R.string.script_network_port)) },
                                        placeholder = { Text("7890") },
                                        keyboardOptions =
                                            KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value = draft.externalProxy.username,
                                        onValueChange = { value ->
                                            updateDraft(
                                                draft.copy(
                                                    externalProxy =
                                                        draft.externalProxy.copy(username = value),
                                                ),
                                            )
                                        },
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.script_network_username_optional,
                                                ),
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value = draft.externalProxy.password,
                                        onValueChange = { value ->
                                            updateDraft(
                                                draft.copy(
                                                    externalProxy =
                                                        draft.externalProxy.copy(password = value),
                                                ),
                                            )
                                        },
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.script_network_password_optional,
                                                ),
                                            )
                                        },
                                        visualTransformation =
                                            if (showExternalPassword) {
                                                VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    showExternalPassword = !showExternalPassword
                                                },
                                            ) {
                                                Icon(
                                                    if (showExternalPassword) {
                                                        Icons.Outlined.VisibilityOff
                                                    } else {
                                                        Icons.Outlined.Visibility
                                                    },
                                                    contentDescription =
                                                        stringResource(
                                                            R.string.script_network_toggle_password,
                                                        ),
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            item {
                                ScriptNetworkSection(
                                    title = stringResource(R.string.script_network_section_embedded),
                                ) {
                                    OutlinedTextField(
                                        value = draft.embeddedProxy.subscriptionUrl,
                                        onValueChange = { value ->
                                            updateDraft(
                                                draft.copy(
                                                    embeddedProxy =
                                                        draft.embeddedProxy.copy(subscriptionUrl = value),
                                                ),
                                            )
                                        },
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.script_network_subscription_url,
                                                ),
                                            )
                                        },
                                        visualTransformation =
                                            if (showSubscriptionUrl) {
                                                VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { showSubscriptionUrl = !showSubscriptionUrl },
                                            ) {
                                                Icon(
                                                    if (showSubscriptionUrl) {
                                                        Icons.Outlined.VisibilityOff
                                                    } else {
                                                        Icons.Outlined.Visibility
                                                    },
                                                    contentDescription =
                                                        stringResource(
                                                            R.string.script_network_toggle_subscription_url,
                                                        ),
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (
                                        draft.embeddedProxy.subscriptionUrl.trim()
                                            .startsWith("http://", ignoreCase = true)
                                    ) {
                                        Text(
                                            stringResource(
                                                R.string.script_network_insecure_subscription_warning,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    if (draft.embeddedProxy.summary.insecureProviderCount > 0) {
                                        Text(
                                            stringResource(
                                                R.string.script_network_insecure_provider_warning,
                                                draft.embeddedProxy.summary.insecureProviderCount,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(
                                            enabled = !isBusy,
                                            onClick = {
                                                scope.launch {
                                                    isBusy = true
                                                    try {
                                                        val sanitized =
                                                            subscriptionClient.fetchAndSanitize(
                                                                draft.embeddedProxy.subscriptionUrl,
                                                            )
                                                        updateDraft(
                                                            draft.withSanitizedSubscription(
                                                                sanitized.yaml,
                                                                sanitized.summary,
                                                            ),
                                                        )
                                                        notify(
                                                            context.getString(
                                                                R.string.script_network_subscription_updated,
                                                            ),
                                                        )
                                                    } catch (error: Exception) {
                                                        notify(
                                                            error.message
                                                                ?: context.getString(
                                                                    R.string
                                                                        .script_network_subscription_update_failed,
                                                                ),
                                                        )
                                                    } finally {
                                                        isBusy = false
                                                    }
                                                }
                                            },
                                        ) {
                                            Icon(Icons.Outlined.Download, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                stringResource(
                                                    R.string.script_network_update_subscription,
                                                ),
                                            )
                                        }
                                        OutlinedButton(
                                            enabled = !isBusy,
                                            onClick = {
                                                yamlEditorText = draft.embeddedProxy.sanitizedYaml
                                                showYamlEditor = true
                                            },
                                        ) {
                                            Text(stringResource(R.string.script_network_paste_yaml))
                                        }
                                        OutlinedButton(
                                            enabled = !isBusy,
                                            onClick = {
                                                yamlPicker.launch(
                                                    arrayOf(
                                                        "application/yaml",
                                                        "application/x-yaml",
                                                        "text/yaml",
                                                        "text/plain",
                                                    ),
                                                )
                                            },
                                        ) {
                                            Icon(Icons.Outlined.UploadFile, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.script_network_import_yaml))
                                        }
                                    }
                                    Text(
                                        stringResource(
                                            R.string.script_network_config_summary,
                                            draft.embeddedProxy.summary.proxyCount,
                                            draft.embeddedProxy.summary.providerCount,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ProxyNodeSelector(
                                        selected = draft.embeddedProxy.selectedProxyName,
                                        names =
                                            (runtimeState.availableProxyNames +
                                                    draft.embeddedProxy.summary.staticProxyNames)
                                                .distinct(),
                                        onSelected = { name ->
                                            scope.launch {
                                                try {
                                                    if (
                                                        runtimeState.phase ==
                                                            ScriptProxyRuntimePhase.RUNNING ||
                                                            runtimeState.phase ==
                                                            ScriptProxyRuntimePhase.AWAITING_SELECTION
                                                    ) {
                                                        draftRuntimeTouched = true
                                                        networkFactory.selectEmbeddedProxy(draft, name)
                                                    }
                                                    updateDraft(
                                                        draft.copy(
                                                            embeddedProxy =
                                                                draft.embeddedProxy.copy(
                                                                    selectedProxyName = name,
                                                                ),
                                                        ),
                                                    )
                                                } catch (error: Exception) {
                                                    notify(
                                                        error.message
                                                            ?: context.getString(
                                                                R.string.script_network_select_node_failed,
                                                            ),
                                                    )
                                                }
                                            }
                                        },
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            enabled = !isBusy && draft.embeddedProxy.sanitizedYaml.isNotBlank(),
                                            onClick = {
                                                scope.launch {
                                                    isBusy = true
                                                    try {
                                                        isSystemVpnActive = networkFactory.isSystemVpnActive()
                                                        draftRuntimeTouched = true
                                                        networkFactory.discoverEmbeddedProxyNames(draft)
                                                    } catch (error: Exception) {
                                                        notify(
                                                            error.message
                                                                ?: context.getString(
                                                                    R.string.script_network_load_nodes_failed,
                                                                ),
                                                        )
                                                    } finally {
                                                        isBusy = false
                                                    }
                                                }
                                            },
                                        ) {
                                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.script_network_load_nodes))
                                        }
                                        OutlinedButton(
                                            enabled =
                                                !isBusy &&
                                                    runtimeState.phase != ScriptProxyRuntimePhase.STOPPED,
                                            onClick = {
                                                scope.launch {
                                                    networkFactory.stopEmbeddedRuntime()
                                                    draftRuntimeTouched = false
                                                }
                                            },
                                        ) {
                                            Icon(Icons.Outlined.Stop, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.script_network_stop_core))
                                        }
                                    }
                                }
                            }

                            item {
                                ScriptNetworkSection(
                                    title = stringResource(R.string.script_network_section_boundaries),
                                ) {
                                    BooleanSettingRow(
                                        title =
                                            stringResource(
                                                R.string.script_network_proxy_private_networks,
                                            ),
                                        supporting =
                                            stringResource(
                                                R.string
                                                    .script_network_proxy_private_networks_summary,
                                            ),
                                        checked = draft.proxyPrivateNetworks,
                                        onCheckedChange = { checked ->
                                            updateDraft(draft.copy(proxyPrivateNetworks = checked))
                                        },
                                    )
                                    BooleanSettingRow(
                                        title =
                                            stringResource(R.string.script_network_allow_under_vpn),
                                        supporting =
                                            stringResource(
                                                R.string.script_network_allow_under_vpn_summary,
                                            ),
                                        checked = draft.allowEmbeddedUnderVpn,
                                        onCheckedChange = { checked ->
                                            updateDraft(draft.copy(allowEmbeddedUnderVpn = checked))
                                        },
                                    )
                                    OutlinedTextField(
                                        value = draft.testUrl,
                                        onValueChange = { value ->
                                            updateDraft(draft.copy(testUrl = value))
                                        },
                                        label = {
                                            Text(stringResource(R.string.script_network_test_url))
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Button(
                                        enabled = !isBusy,
                                        onClick = {
                                            scope.launch {
                                                isBusy = true
                                                try {
                                                    isSystemVpnActive = networkFactory.isSystemVpnActive()
                                                    if (
                                                        draft.globalMode ==
                                                            ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION
                                                    ) {
                                                        draftRuntimeTouched = true
                                                    }
                                                    val status =
                                                        testCurrentGlobalRoute(
                                                            context = context,
                                                            config = draft,
                                                            rawUrl = draft.testUrl,
                                                        )
                                                    notify(
                                                        context.getString(
                                                            R.string.script_network_test_success,
                                                            status,
                                                        ),
                                                    )
                                                } catch (error: Exception) {
                                                    notify(
                                                        error.message
                                                            ?: context.getString(
                                                                R.string.script_network_test_failed,
                                                            ),
                                                    )
                                                }
                                                isBusy = false
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Outlined.NetworkCheck, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(
                                                R.string.script_network_test_global_mode,
                                            ),
                                        )
                                    }
                                }
                            }

                            item {
                                ScriptNetworkSection(
                                    title =
                                        stringResource(
                                            R.string.script_network_section_script_rules,
                                        ),
                                ) {
                                    OutlinedTextField(
                                        value = packageQuery,
                                        onValueChange = { packageQuery = it },
                                        label = {
                                            Text(stringResource(R.string.script_network_search_scripts))
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    TextButton(
                                        enabled = draft.scriptModes.isNotEmpty(),
                                        onClick = { showResetOverridesConfirmation = true },
                                    ) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(
                                                R.string.script_network_reset_inheritance,
                                            ),
                                        )
                                    }
                                }
                            }

                            val visiblePackages =
                                packages.filter { item ->
                                    packageQuery.isBlank() ||
                                        item.packageName.contains(packageQuery, ignoreCase = true) ||
                                        item.displayName.contains(packageQuery, ignoreCase = true) ||
                                        item.category.contains(packageQuery, ignoreCase = true)
                                }
                            items(visiblePackages, key = ScriptNetworkPackageItem::packageName) { item ->
                                ScriptModeRow(
                                    item = item,
                                    selected =
                                        draft.scriptModes[item.packageName]
                                            ?: ScriptNetworkScriptMode.INHERIT,
                                    onSelected = { mode ->
                                        val updated = draft.scriptModes.toMutableMap()
                                        if (mode == ScriptNetworkScriptMode.INHERIT) {
                                            updated.remove(item.packageName)
                                        } else {
                                            updated[item.packageName] = mode
                                        }
                                        updateDraft(draft.copy(scriptModes = updated))
                                    },
                                )
                            }

                            item {
                                HorizontalDivider()
                                TextButton(onClick = { showResetConfirmation = true }) {
                                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.script_network_reset_all))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showYamlEditor) {
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    showYamlEditor = false
                    yamlEditorText = ""
                }
            },
            title = { Text(stringResource(R.string.script_network_yaml_editor_title)) },
            text = {
                OutlinedTextField(
                    value = yamlEditorText,
                    onValueChange = { yamlEditorText = it },
                    minLines = 10,
                    maxLines = 18,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            try {
                                val sanitized = withContext(Dispatchers.Default) {
                                    MihomoConfigSanitizer.sanitize(yamlEditorText)
                                }
                                updateDraft(
                                    draft.copy(
                                            embeddedProxy =
                                                draft.embeddedProxy.copy(subscriptionUrl = ""),
                                        )
                                        .withSanitizedSubscription(
                                            sanitized.yaml,
                                            sanitized.summary,
                                        ),
                                )
                                showYamlEditor = false
                                yamlEditorText = ""
                                notify(
                                    context.getString(R.string.script_network_yaml_sanitized),
                                )
                            } catch (error: Exception) {
                                notify(
                                    error.message
                                        ?: context.getString(R.string.script_network_yaml_invalid),
                                )
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.script_network_import)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showYamlEditor = false
                        yamlEditorText = ""
                    },
                    enabled = !isBusy,
                ) {
                    Text(stringResource(R.string.script_network_cancel))
                }
            },
        )
    }

    if (showResetOverridesConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetOverridesConfirmation = false },
            title = {
                Text(stringResource(R.string.script_network_reset_overrides_title))
            },
            text = {
                Text(stringResource(R.string.script_network_reset_overrides_summary))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetOverridesConfirmation = false
                        updateDraft(draft.copy(scriptModes = emptyMap()))
                    },
                ) { Text(stringResource(R.string.script_network_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetOverridesConfirmation = false }) {
                    Text(stringResource(R.string.script_network_cancel))
                }
            },
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(stringResource(R.string.script_network_reset_title)) },
            text = { Text(stringResource(R.string.script_network_reset_summary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        scope.launch {
                            isBusy = true
                            try {
                                networkFactory.stopEmbeddedRuntime()
                                val reset = withContext(Dispatchers.IO) { store.reset() }
                                draft = reset
                                originalConfig = reset
                                isDirty = false
                                draftRuntimeTouched = false
                                notify(context.getString(R.string.script_network_reset_done))
                            } catch (error: Exception) {
                                notify(
                                    error.message
                                        ?: context.getString(R.string.script_network_reset_failed),
                                )
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.script_network_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.script_network_cancel))
                }
            },
        )
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text(stringResource(R.string.script_network_discard_title)) },
            text = { Text(stringResource(R.string.script_network_discard_summary)) },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        showDiscardConfirmation = false
                        scope.launch {
                            isBusy = true
                            try {
                                if (draftRuntimeTouched) {
                                    networkFactory.stopEmbeddedRuntime()
                                }
                                isBusy = false
                                onDismiss()
                            } catch (error: Exception) {
                                isBusy = false
                                notify(
                                    error.message
                                        ?: context.getString(
                                            R.string.script_network_discard_failed,
                                        ),
                                )
                            }
                        }
                    },
                ) { Text(stringResource(R.string.script_network_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text(stringResource(R.string.script_network_continue_editing))
                }
            },
        )
    }
}

@Composable
private fun ScriptNetworkSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalModeSelector(
    selected: ScriptNetworkGlobalMode,
    onSelected: (ScriptNetworkGlobalMode) -> Unit,
) {
    val modes = ScriptNetworkGlobalMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                label = { Text(mode.toDisplayText(), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun ProxyNodeSelector(
    selected: String,
    names: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = names.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                selected.ifBlank { stringResource(R.string.script_network_select_node) },
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            names.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelected(name)
                    },
                )
            }
        }
    }
}

@Composable
private fun BooleanSettingRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun ScriptModeRow(
    item: ScriptNetworkPackageItem,
    selected: ScriptNetworkScriptMode,
    onSelected: (ScriptNetworkScriptMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(item.displayName.ifBlank { item.packageName }) },
        supportingContent = { Text("${item.packageName} · ${item.category}") },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(selected.toDisplayText())
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ScriptNetworkScriptMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.toDisplayText()) },
                            onClick = {
                                expanded = false
                                onSelected(mode)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ScriptNetworkGlobalMode.toDisplayText(): String =
    when (this) {
        ScriptNetworkGlobalMode.DIRECT -> stringResource(R.string.script_network_mode_direct)
        ScriptNetworkGlobalMode.EXTERNAL_PROXY ->
            stringResource(R.string.script_network_mode_external)
        ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION ->
            stringResource(R.string.script_network_mode_embedded)
    }

@Composable
private fun ScriptNetworkGlobalMode.supportingText(): String =
    when (this) {
        ScriptNetworkGlobalMode.DIRECT ->
            stringResource(R.string.script_network_mode_direct_summary)
        ScriptNetworkGlobalMode.EXTERNAL_PROXY ->
            stringResource(R.string.script_network_mode_external_summary)
        ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION ->
            stringResource(R.string.script_network_mode_embedded_summary)
    }

@Composable
private fun ScriptNetworkScriptMode.toDisplayText(): String =
    when (this) {
        ScriptNetworkScriptMode.INHERIT -> stringResource(R.string.script_network_mode_inherit)
        ScriptNetworkScriptMode.DIRECT -> stringResource(R.string.script_network_mode_direct)
        ScriptNetworkScriptMode.EXTERNAL_PROXY ->
            stringResource(R.string.script_network_mode_external)
        ScriptNetworkScriptMode.EMBEDDED_SUBSCRIPTION ->
            stringResource(R.string.script_network_mode_embedded)
    }

@Composable
private fun ScriptProxyRuntimePhase.toDisplayText(): String =
    when (this) {
        ScriptProxyRuntimePhase.STOPPED -> stringResource(R.string.script_network_phase_stopped)
        ScriptProxyRuntimePhase.STARTING -> stringResource(R.string.script_network_phase_starting)
        ScriptProxyRuntimePhase.AWAITING_SELECTION ->
            stringResource(R.string.script_network_phase_awaiting_selection)
        ScriptProxyRuntimePhase.RUNNING -> stringResource(R.string.script_network_phase_running)
        ScriptProxyRuntimePhase.STOPPING -> stringResource(R.string.script_network_phase_stopping)
        ScriptProxyRuntimePhase.ERROR -> stringResource(R.string.script_network_phase_error)
    }

private fun ScriptNetworkConfig.withSanitizedSubscription(
    yaml: String,
    summary: com.ai.assistance.operit.core.tools.javascript.network.MihomoSubscriptionSummary,
): ScriptNetworkConfig {
    val currentSelection = embeddedProxy.selectedProxyName
    val retainedSelection =
        currentSelection.takeIf { selected ->
            selected.isNotBlank() &&
                (selected in summary.staticProxyNames || summary.providerCount > 0)
        }.orEmpty()
    return copy(
        embeddedProxy =
            embeddedProxy.copy(
                sanitizedYaml = yaml,
                selectedProxyName = retainedSelection,
                updatedAtEpochMillis = System.currentTimeMillis(),
                summary = summary,
            ),
    )
}

private fun ScriptNetworkConfig.usesEmbeddedRuntime(): Boolean =
    globalMode == ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION ||
        scriptModes.values.any { mode ->
            mode == ScriptNetworkScriptMode.EMBEDDED_SUBSCRIPTION
        }

private suspend fun testCurrentGlobalRoute(
    context: android.content.Context,
    config: ScriptNetworkConfig,
    rawUrl: String,
): Int =
    withContext(Dispatchers.IO) {
        val url = ScriptNetworkPolicy.validateTestUrl(rawUrl)
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
        ScriptNetworkHttpClientFactory.getInstance(context).applyGlobalRouteForTest(builder, config)
        val request = Request.Builder().url(url).get().build()
        builder.build().newCall(request).execute().use { response ->
            if (response.code !in 200..399) {
                throw com.ai.assistance.operit.core.tools.javascript.network.ScriptNetworkException(
                    com.ai.assistance.operit.core.tools.javascript.network.ScriptNetworkErrorCode.HTTP_FAILED,
                    context.getString(R.string.script_network_test_http_failed, response.code),
                )
            }
            response.code
        }
    }
