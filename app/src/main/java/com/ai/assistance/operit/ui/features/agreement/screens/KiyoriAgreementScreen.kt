package com.ai.assistance.operit.ui.features.agreement.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.ui.main.shell.KiyoriCollapsingSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsGroupSection
import com.kiyori.design.theme.KiyoriUiShapes

internal enum class KiyoriLegalDocument(
    @StringRes val titleResId: Int,
    @StringRes val summaryResId: Int,
    @StringRes val contentResId: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    USER_AGREEMENT(
        titleResId = R.string.kiyori_onboarding_user_agreement_title,
        summaryResId = R.string.kiyori_onboarding_user_agreement_summary,
        contentResId = R.string.kiyori_onboarding_user_agreement_content,
        icon = Icons.Default.Description,
    ),
    PRIVACY_POLICY(
        titleResId = R.string.kiyori_onboarding_privacy_policy_title,
        summaryResId = R.string.kiyori_onboarding_privacy_policy_summary,
        contentResId = R.string.kiyori_onboarding_privacy_policy_content,
        icon = Icons.Default.Security,
    ),
}

internal fun canAcceptKiyoriAgreement(
    checked: Boolean,
): Boolean = checked

@Composable
internal fun KiyoriAgreementConfirmationScreen(
    onAccepted: () -> Unit,
    onDeclined: () -> Unit,
) {
    var checked by rememberSaveable { mutableStateOf(false) }
    var selectedDocument by rememberSaveable {
        mutableStateOf<KiyoriLegalDocument?>(null)
    }

    BackHandler(enabled = selectedDocument != null) {
        selectedDocument = null
    }

    val document = selectedDocument
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (document != null) {
            KiyoriAgreementDocumentScreen(
                document = document,
                onBack = { selectedDocument = null },
            )
        } else {
            KiyoriAgreementSummary(
                checked = checked,
                onCheckedChange = { checked = it },
                onOpenUserAgreement = {
                    selectedDocument = KiyoriLegalDocument.USER_AGREEMENT
                },
                onOpenPrivacyPolicy = {
                    selectedDocument = KiyoriLegalDocument.PRIVACY_POLICY
                },
                onDecline = onDeclined,
                onAccept = onAccepted,
            )
        }
    }
}

@Composable
internal fun KiyoriAgreementSummary(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    agreementAlreadyAccepted: Boolean = false,
    interactionEnabled: Boolean = true,
    reviewing: Boolean = false,
    acceptModifier: Modifier = Modifier,
    declineModifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.kiyori_onboarding_agreement_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                lineHeight = 31.sp,
            )
            Text(
                text = stringResource(
                    if (reviewing) R.string.kiyori_onboarding_agreement_review_hint
                    else R.string.kiyori_onboarding_agreement_subtitle,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
            AgreementHighlights()
            AgreementVersionChip()
            Text(
                text = stringResource(R.string.kiyori_onboarding_agreement_read_prompt),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LegalDocumentEntry(
                document = KiyoriLegalDocument.USER_AGREEMENT,
                onClick = onOpenUserAgreement,
            )
            LegalDocumentEntry(
                document = KiyoriLegalDocument.PRIVACY_POLICY,
                onClick = onOpenPrivacyPolicy,
            )
            // 勾选说明随正文滚动，窄屏横屏或大字体下不挤占整个正文视口；底部仅固定操作。
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = checked, enabled = !agreementAlreadyAccepted && interactionEnabled,
                            role = Role.Checkbox, onValueChange = onCheckedChange),
                shape = KiyoriUiShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        enabled = !agreementAlreadyAccepted && interactionEnabled,
                    )
                    Text(
                        text = stringResource(R.string.kiyori_onboarding_agreement_checkbox),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                    )
                }
            }
            if (!agreementAlreadyAccepted) Text(
                text = stringResource(R.string.kiyori_onboarding_agreement_checked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onAccept,
            enabled = interactionEnabled && canAcceptKiyoriAgreement(checked),
            shape = KiyoriUiShapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            modifier =
                acceptModifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
        ) {
            Text(
                text = stringResource(
                    if (agreementAlreadyAccepted) R.string.kiyori_onboarding_agreement_continue
                    else R.string.kiyori_onboarding_agreement_accept,
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        TextButton(
            onClick = onDecline,
            enabled = interactionEnabled,
            modifier = declineModifier.fillMaxWidth().heightIn(min = 48.dp),
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Text(stringResource(
                if (reviewing) R.string.kiyori_onboarding_review_return
                else R.string.kiyori_onboarding_agreement_decline,
            ))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AgreementVersionChip() {
    Surface(
        shape = KiyoriUiShapes.control,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text =
                stringResource(
                    R.string.kiyori_onboarding_agreement_version,
                    AgreementPreferences.CURRENT_AGREEMENT_VERSION,
                ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun KiyoriLegalDocumentsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDocument by rememberSaveable {
        mutableStateOf<KiyoriLegalDocument?>(null)
    }

    BackHandler(enabled = selectedDocument != null) {
        selectedDocument = null
    }

    val document = selectedDocument
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (document != null) {
            KiyoriAgreementDocumentScreen(
                document = document,
                onBack = { selectedDocument = null },
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
            ) {
                AgreementDocumentTopBar(
                    title = stringResource(R.string.kiyori_onboarding_legal_documents_title),
                    onBack = onBack,
                )
                HorizontalDivider()
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.kiyori_onboarding_legal_documents_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.kiyori_onboarding_agreement_version,
                                AgreementPreferences.CURRENT_AGREEMENT_VERSION,
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AgreementHighlights()
                    LegalDocumentEntry(
                        document = KiyoriLegalDocument.USER_AGREEMENT,
                        onClick = {
                            selectedDocument = KiyoriLegalDocument.USER_AGREEMENT
                        },
                    )
                    LegalDocumentEntry(
                        document = KiyoriLegalDocument.PRIVACY_POLICY,
                        onClick = {
                            selectedDocument = KiyoriLegalDocument.PRIVACY_POLICY
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun KiyoriLegalDocumentScreen(
    document: KiyoriLegalDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
    ) {
        KiyoriCollapsingSettingsPage(
            title = stringResource(document.titleResId),
            onBack = onBack,
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(document.summaryResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 21.sp,
                    )
                    AgreementVersionChip()
                }
            }
            item {
                KiyoriSettingsGroupSection(
                    title = stringResource(R.string.kiyori_onboarding_legal_full_text),
                    description =
                        stringResource(R.string.kiyori_onboarding_legal_full_text_note),
                ) {
                    LegalDocumentBody(
                        document = document,
                        independentlyScrollable = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun KiyoriAgreementDocumentScreen(
    document: KiyoriLegalDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        AgreementDocumentTopBar(
            title = stringResource(document.titleResId),
            onBack = onBack,
        )
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(document.summaryResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
            Text(
                text = stringResource(R.string.kiyori_onboarding_legal_full_text),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.kiyori_onboarding_legal_selectable_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
        HorizontalDivider()
        LegalDocumentBody(
            document = document,
            independentlyScrollable = true,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun LegalDocumentBody(
    document: KiyoriLegalDocument,
    independentlyScrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    val raw = stringResource(document.contentResId, AgreementPreferences.CURRENT_AGREEMENT_VERSION)
    val contentKey = document.contentResId to AgreementPreferences.CURRENT_AGREEMENT_VERSION
    val sections = remember(contentKey, raw) { parseKiyoriLegalSections(raw) }
    val requesters = remember(contentKey, sections.size) { sections.map { BringIntoViewRequester() } }
    val scope = rememberCoroutineScope()
    var showContents by rememberSaveable(document.name) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.then(
            if (independentlyScrollable) Modifier.verticalScroll(scrollState) else Modifier,
        ).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            TextButton(onClick = { showContents = !showContents }) {
                Text(if (showContents) "收起目录" else "浏览目录 · ${sections.size - 1} 个章节")
            }
            if (showContents) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sections.drop(1).forEachIndexed { index, section ->
                        AssistChip(
                            onClick = { scope.launch { requesters[index + 1].bringIntoView() } },
                            label = { Text(section.title, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                sections.forEachIndexed { index, section ->
                    Column(
                        Modifier.fillMaxWidth().bringIntoViewRequester(requesters[index]),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            section.title,
                            modifier = Modifier.semantics { heading() },
                            style = if (index == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(section.body, style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
            }
        }
        Text("以上为当前完整中文正文 · 版本 ${AgreementPreferences.CURRENT_AGREEMENT_VERSION}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal data class KiyoriLegalSection(val title: String, val body: String)

/** 只解析随包发布的受控正文，不渲染远程 HTML；标题和正文均保留供选择复制。 */
internal fun parseKiyoriLegalSections(html: String): List<KiyoriLegalSection> =
    Regex("<p><b>(.*?)</b><br>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        .findAll(html).map { match ->
            KiyoriLegalSection(match.groupValues[1], match.groupValues[2].replace("<br>", "\n\n"))
        }.toList().also { require(it.isNotEmpty()) { "Bundled legal document must contain sections" } }

@Composable
private fun AgreementHighlights() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                Text("开始前，了解这三件事", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            listOf(
                "数据去向有边界" to "浏览网站、调用模型或联网扩展时，相关内容会发送给对应服务；本地保存不等于所有处理都离线。",
                "授权是独立选择" to "同意协议不会开启设备权限。你可以暂不授权，使用具体功能时再决定。",
                "重要操作请核验" to "AI 可能出错；脚本与自动化可能修改文件或设备。第三方服务可能收费，重要数据请先备份。",
            ).forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(body, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                }
            }
            Text("这里是阅读提示，完整权利与责任以以下两份文档为准。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgreementDocumentTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.kiyori_onboarding_back),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    stringResource(
                        R.string.kiyori_onboarding_agreement_version,
                        AgreementPreferences.CURRENT_AGREEMENT_VERSION,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegalDocumentEntry(
    document: KiyoriLegalDocument,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = KiyoriUiShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = KiyoriUiShapes.field,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = document.icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(document.titleResId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(document.summaryResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
