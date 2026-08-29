package com.ai.assistance.operit.ui.features.agreement.screens

import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.core.widget.TextViewCompat
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
                text = stringResource(R.string.kiyori_onboarding_agreement_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
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
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !agreementAlreadyAccepted) {
                            onCheckedChange(!checked)
                        },
                shape = KiyoriUiShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange =
                            if (agreementAlreadyAccepted) {
                                null
                            } else {
                                onCheckedChange
                            },
                    )
                    Text(
                        text = stringResource(R.string.kiyori_onboarding_agreement_checkbox),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
        Button(
            onClick = onAccept,
            enabled = canAcceptKiyoriAgreement(checked),
            shape = KiyoriUiShapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
        ) {
            Text(
                text = stringResource(R.string.kiyori_onboarding_agreement_accept),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        TextButton(
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Text(stringResource(R.string.kiyori_onboarding_agreement_decline))
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
    val textColor = MaterialTheme.colorScheme.onSurface
    val bodyTypography = MaterialTheme.typography.bodyMedium
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                val displayMetrics = context.resources.displayMetrics
                val horizontalPadding =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        12f,
                        displayMetrics,
                    ).toInt()
                setPadding(
                    horizontalPadding,
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        16f,
                        displayMetrics,
                    ).toInt(),
                    horizontalPadding,
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        24f,
                        displayMetrics,
                    ).toInt(),
                )
                setTextIsSelectable(true)
                TextViewCompat.setLineHeight(
                    this,
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        bodyTypography.lineHeight.value,
                        displayMetrics,
                    ).toInt(),
                )
            }
        },
        update = { textView ->
            textView.movementMethod =
                if (independentlyScrollable) ScrollingMovementMethod.getInstance() else null
            textView.isVerticalScrollBarEnabled = independentlyScrollable
            textView.setTextColor(textColor.toArgb())
            textView.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                bodyTypography.fontSize.value,
            )
            val contentKey =
                document.contentResId to AgreementPreferences.CURRENT_AGREEMENT_VERSION
            if (textView.tag != contentKey) {
                textView.tag = contentKey
                textView.text =
                    HtmlCompat.fromHtml(
                        textView.context.getString(
                            document.contentResId,
                            AgreementPreferences.CURRENT_AGREEMENT_VERSION,
                        ),
                        HtmlCompat.FROM_HTML_MODE_COMPACT,
                    )
                textView.scrollTo(0, 0)
            }
        },
        modifier = modifier,
    )
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
