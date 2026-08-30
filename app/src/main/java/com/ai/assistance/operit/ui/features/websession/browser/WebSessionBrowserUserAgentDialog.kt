package com.ai.assistance.operit.ui.features.websession.browser

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSiteUserAgentRule
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionUserAgentMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.extractWebSessionUserAgentHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.normalizeWebSessionUserAgentDomain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveWebSessionPresetUserAgent
import com.kiyori.design.theme.KiyoriUiShapes

private enum class UserAgentDialogPage {
    CHOOSER,
    CUSTOM_GLOBAL,
    CUSTOM_SITE,
}

private enum class UserAgentChoice {
    ANDROID,
    PC_DESKTOP,
    IPHONE,
    SYMBIAN_WAP,
    CUSTOM_GLOBAL,
    CUSTOM_SITE,
}

@Composable
internal fun WebSessionBrowserUserAgentDialog(
    globalMode: WebSessionUserAgentMode,
    customGlobalUserAgent: String,
    currentUrl: String,
    activeSiteRule: WebSessionSiteUserAgentRule?,
    onSelectGlobalMode: (WebSessionUserAgentMode) -> Unit,
    onSaveCustomGlobalUserAgent: (String) -> Unit,
    onSaveSiteUserAgentRule: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentHost = remember(currentUrl) { extractWebSessionUserAgentHost(currentUrl).orEmpty() }
    var page by remember { mutableStateOf(UserAgentDialogPage.CHOOSER) }
    var customGlobalDraft by remember(customGlobalUserAgent) {
        mutableStateOf(customGlobalUserAgent)
    }
    var siteDomainDraft by remember(currentHost, activeSiteRule) {
        mutableStateOf(activeSiteRule?.domain ?: currentHost)
    }
    var siteUserAgentDraft by remember(currentHost, activeSiteRule) {
        mutableStateOf(activeSiteRule?.userAgent.orEmpty())
    }
    val dismissCurrentPage = {
        if (page == UserAgentDialogPage.CHOOSER) onDismiss() else page = UserAgentDialogPage.CHOOSER
    }

    when (page) {
        UserAgentDialogPage.CHOOSER ->
            UserAgentChooserDialog(
                selectedChoice =
                    activeSiteRule?.let { UserAgentChoice.CUSTOM_SITE }
                        ?: globalMode.toUserAgentChoice(),
                onSelect = { choice ->
                    when (choice) {
                        UserAgentChoice.ANDROID ->
                            onSelectGlobalMode(WebSessionUserAgentMode.ANDROID)
                        UserAgentChoice.PC_DESKTOP ->
                            onSelectGlobalMode(WebSessionUserAgentMode.PC_DESKTOP)
                        UserAgentChoice.IPHONE ->
                            onSelectGlobalMode(WebSessionUserAgentMode.IPHONE)
                        UserAgentChoice.SYMBIAN_WAP ->
                            onSelectGlobalMode(WebSessionUserAgentMode.SYMBIAN_WAP)
                        UserAgentChoice.CUSTOM_GLOBAL -> page = UserAgentDialogPage.CUSTOM_GLOBAL
                        UserAgentChoice.CUSTOM_SITE -> {
                            if (currentHost.isBlank()) {
                                Toast.makeText(
                                    context,
                                    R.string.web_session_user_agent_site_unavailable,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                page = UserAgentDialogPage.CUSTOM_SITE
                            }
                        }
                    }
                },
                onDismiss = onDismiss,
            )

        UserAgentDialogPage.CUSTOM_GLOBAL ->
            CustomGlobalUserAgentDialog(
                value = customGlobalDraft,
                onValueChange = { customGlobalDraft = it },
                onDismiss = dismissCurrentPage,
                onConfirm = { onSaveCustomGlobalUserAgent(customGlobalDraft.trim()) },
            )

        UserAgentDialogPage.CUSTOM_SITE ->
            CustomSiteUserAgentDialog(
                currentHost = currentHost,
                domain = siteDomainDraft,
                userAgent = siteUserAgentDraft,
                onDomainChange = { siteDomainDraft = it },
                onUserAgentChange = { siteUserAgentDraft = it },
                onUseCurrentHost = { siteDomainDraft = currentHost },
                onDismiss = dismissCurrentPage,
                onConfirm = {
                    onSaveSiteUserAgentRule(siteDomainDraft, siteUserAgentDraft.trim())
                },
            )
    }
}

@Composable
private fun UserAgentChooserDialog(
    selectedChoice: UserAgentChoice,
    onSelect: (UserAgentChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = UserAgentChoice.entries
    val blueColors = WebSessionBrowserMenuTone.USER_AGENT.resolveColors()
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Language,
            tone = WebSessionBrowserMenuTone.USER_AGENT,
            title = stringResource(R.string.web_session_user_agent_dialog_title),
            modifier = Modifier.widthIn(min = 300.dp, max = 380.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 10.dp),
            ) {
                choices.forEachIndexed { index, choice ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { onSelect(choice) }
                                .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = choice.displayName(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (choice == selectedChoice) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = blueColors.icon,
                            )
                        }
                    }
                    if (index != choices.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomGlobalUserAgentDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Language,
            tone = WebSessionBrowserMenuTone.USER_AGENT,
            title = stringResource(R.string.web_session_user_agent_custom_global_title),
            modifier = Modifier.widthIn(min = 300.dp, max = 380.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(stringResource(R.string.web_session_user_agent_input_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = KiyoriUiShapes.field,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                    TextButton(onClick = onConfirm, enabled = value.isNotBlank()) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomSiteUserAgentDialog(
    currentHost: String,
    domain: String,
    userAgent: String,
    onDomainChange: (String) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onUseCurrentHost: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val domainIsValid = remember(domain) { normalizeWebSessionUserAgentDomain(domain) != null }
    val presets =
        listOf(
            stringResource(R.string.web_session_user_agent_default) to "",
            stringResource(R.string.web_session_user_agent_android) to
                resolveWebSessionPresetUserAgent(WebSessionUserAgentMode.ANDROID, ""),
            stringResource(R.string.web_session_user_agent_pc_desktop) to
                resolveWebSessionPresetUserAgent(WebSessionUserAgentMode.PC_DESKTOP, ""),
            stringResource(R.string.web_session_user_agent_iphone) to
                resolveWebSessionPresetUserAgent(WebSessionUserAgentMode.IPHONE, ""),
            stringResource(R.string.web_session_user_agent_symbian_wap) to
                resolveWebSessionPresetUserAgent(WebSessionUserAgentMode.SYMBIAN_WAP, ""),
        )
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Language,
            tone = WebSessionBrowserMenuTone.USER_AGENT,
            title = stringResource(R.string.web_session_user_agent_custom_site_title),
            modifier = Modifier.widthIn(min = 300.dp, max = 420.dp).heightIn(max = 680.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.web_session_user_agent_custom_site_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = onDomainChange,
                    label = { Text(stringResource(R.string.web_session_user_agent_domain)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = KiyoriUiShapes.field,
                    isError = domain.isNotBlank() && !domainIsValid,
                )
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = onUserAgentChange,
                    label = { Text(stringResource(R.string.web_session_user_agent_rule_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = KiyoriUiShapes.field,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { (title, preset) ->
                        Surface(
                            shape = KiyoriUiShapes.control,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .height(36.dp)
                                .clickable { onUserAgentChange(preset) },
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onUseCurrentHost,
                        enabled = currentHost.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.web_session_user_agent_full_domain))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = onConfirm,
                        enabled = domainIsValid,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

private fun WebSessionUserAgentMode.toUserAgentChoice(): UserAgentChoice =
    when (this) {
        WebSessionUserAgentMode.ANDROID -> UserAgentChoice.ANDROID
        WebSessionUserAgentMode.PC_DESKTOP -> UserAgentChoice.PC_DESKTOP
        WebSessionUserAgentMode.IPHONE -> UserAgentChoice.IPHONE
        WebSessionUserAgentMode.SYMBIAN_WAP -> UserAgentChoice.SYMBIAN_WAP
        WebSessionUserAgentMode.CUSTOM_GLOBAL -> UserAgentChoice.CUSTOM_GLOBAL
    }

@Composable
private fun UserAgentChoice.displayName(): String =
    when (this) {
        UserAgentChoice.ANDROID -> stringResource(R.string.web_session_user_agent_android)
        UserAgentChoice.PC_DESKTOP -> stringResource(R.string.web_session_user_agent_pc_desktop)
        UserAgentChoice.IPHONE -> stringResource(R.string.web_session_user_agent_iphone)
        UserAgentChoice.SYMBIAN_WAP -> stringResource(R.string.web_session_user_agent_symbian_wap)
        UserAgentChoice.CUSTOM_GLOBAL -> stringResource(R.string.web_session_user_agent_custom_global)
        UserAgentChoice.CUSTOM_SITE -> stringResource(R.string.web_session_user_agent_custom_site)
    }
