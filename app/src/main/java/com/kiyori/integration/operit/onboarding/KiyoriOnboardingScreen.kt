package com.kiyori.integration.operit.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PermPhoneMsg
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.RootAuthorizer
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.ai.assistance.operit.ui.features.agreement.screens.KiyoriAgreementDocumentScreen
import com.ai.assistance.operit.ui.features.agreement.screens.KiyoriAgreementSummary
import com.ai.assistance.operit.ui.features.agreement.screens.KiyoriLegalDocument
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import com.kiyori.platform.logging.KiyoriLogger
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun KiyoriOnboardingScreen(
    agreementAccepted: Boolean,
    onAgreementAccepted: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences =
        remember(context) {
            KiyoriOnboardingPreferences(context.applicationContext)
        }
    var agreementAcceptedState by rememberSaveable { mutableStateOf(agreementAccepted) }
    val initialStep =
        remember {
            resolveInitialKiyoriOnboardingStep(
                agreementAccepted = agreementAccepted,
                persistedStep = preferences.readCurrentStep(),
            )
        }
    val pagerState =
        rememberPagerState(
            initialPage = initialStep.ordinal,
            pageCount = { KiyoriOnboardingStep.entries.size },
        )
    val pagerScope = rememberCoroutineScope()
    val currentStep = KiyoriOnboardingStep.entries[pagerState.settledPage]
    val pagerFlingBehavior =
        PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
        )
    var agreementChecked by rememberSaveable { mutableStateOf(agreementAccepted) }
    var selectedLegalDocument by rememberSaveable {
        mutableStateOf<KiyoriLegalDocument?>(null)
    }
    val initialPermissionSnapshot =
        remember {
            readKiyoriPermissionSnapshot(context)
        }
    var permissionSnapshot by remember { mutableStateOf(initialPermissionSnapshot) }
    var selectedPermissionIds by
        remember {
            mutableStateOf(
                sanitizeKiyoriPermissionSelection(
                    snapshot = initialPermissionSnapshot,
                    selectedPermissionIds = preferences.readSelectedPermissions(),
                ),
            )
        }
    // 系统授权界面可能在进程回收后重新创建当前页面。授权队列只属于本次界面会话，
    // 用户选择则由 preferences 持久化，避免恢复后停在无法继续的处理中状态。
    var permissionQueueNames by remember { mutableStateOf(emptyList<String>()) }
    var authorizationActive by remember { mutableStateOf(false) }
    var runtimeRequestInFlight by remember { mutableStateOf(false) }
    var waitingForExternalSettings by remember { mutableStateOf(false) }

    fun moveTo(step: KiyoriOnboardingStep) {
        pagerScope.launch {
            pagerState.animateScrollToPage(step.ordinal)
        }
    }

    LaunchedEffect(pagerState, preferences) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                preferences.saveCurrentStep(
                    KiyoriOnboardingStep.entries[pageIndex],
                )
            }
    }

    fun refreshPermissions() {
        val updatedSnapshot = readKiyoriPermissionSnapshot(context)
        val sanitizedSelection =
            sanitizeKiyoriPermissionSelection(
                snapshot = updatedSnapshot,
                selectedPermissionIds = selectedPermissionIds,
            )
        permissionSnapshot = updatedSnapshot
        if (sanitizedSelection != selectedPermissionIds) {
            selectedPermissionIds = sanitizedSelection
            if (sanitizedSelection.isEmpty()) {
                preferences.clearSelectedPermissions()
            } else {
                preferences.saveSelectedPermissions(sanitizedSelection)
            }
        }
    }

    fun completeOnboarding() {
        authorizationActive = false
        permissionQueueNames = emptyList()
        selectedPermissionIds = emptySet()
        preferences.clearSelectedPermissions()
        preferences.complete()
        onComplete()
    }

    fun handlePermissionAction(permissionId: KiyoriPermissionId): Boolean {
        return try {
            when (
                resolveKiyoriPermissionAction(
                    permissionId = permissionId,
                    status = permissionSnapshot.status(permissionId),
                )
            ) {
                KiyoriPermissionActionKind.OPEN_APPLICATION_SETTINGS -> {
                    launchKiyoriApplicationPermissionSettings(context)
                    true
                }

                KiyoriPermissionActionKind.OPEN_SYSTEM_SETTINGS -> {
                    launchKiyoriPermissionSettings(
                        context = context,
                        permissionId = permissionId,
                    )
                    true
                }

                KiyoriPermissionActionKind.CONFIGURE_ACCESSIBILITY -> {
                    performKiyoriAccessibilityAction(context)
                    true
                }

                KiyoriPermissionActionKind.CONFIGURE_SHIZUKU -> {
                    performKiyoriShizukuAction(context) {
                        pagerScope.launch {
                            waitingForExternalSettings = false
                            refreshPermissions()
                        }
                    }
                    true
                }

                KiyoriPermissionActionKind.REQUEST_ROOT -> {
                    RootAuthorizer.requestRootPermission {
                        pagerScope.launch {
                            waitingForExternalSettings = false
                            refreshPermissions()
                        }
                    }
                    true
                }

                KiyoriPermissionActionKind.REQUEST_RUNTIME,
                KiyoriPermissionActionKind.NONE,
                -> false
            }
        } catch (error: Exception) {
            KiyoriLogger.e(
                "KiyoriOnboarding",
                "无法打开或请求首次启动权限: $permissionId",
                error,
            )
            showKiyoriPermissionActionFailure(context, permissionId)
            false
        }
    }

    val runtimePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            runtimeRequestInFlight = false
            refreshPermissions()
        }

    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshPermissions()
                    if (waitingForExternalSettings) {
                        waitingForExternalSettings = false
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(
        authorizationActive,
        permissionQueueNames,
        runtimeRequestInFlight,
        waitingForExternalSettings,
    ) {
        if (!authorizationActive || runtimeRequestInFlight || waitingForExternalSettings) {
            return@LaunchedEffect
        }
        val queue =
            permissionQueueNames.map(KiyoriPermissionId::valueOf)
        if (queue.isEmpty()) {
            completeOnboarding()
            return@LaunchedEffect
        }
        val runtimeIds =
            queue.filter { permissionId ->
                isKiyoriRuntimePermission(permissionId) &&
                    resolveKiyoriPermissionAction(
                        permissionId = permissionId,
                        status = permissionSnapshot.status(permissionId),
                    ) == KiyoriPermissionActionKind.REQUEST_RUNTIME
            }
        if (runtimeIds.isNotEmpty()) {
            permissionQueueNames =
                queue
                    .filterNot(::isKiyoriRuntimePermission)
                    .map(KiyoriPermissionId::name)
            runtimeRequestInFlight = true
            runtimePermissionLauncher.launch(
                kiyoriRuntimePermissionsForSdk(
                    sdkInt = Build.VERSION.SDK_INT,
                    selectedPermissionIds = runtimeIds.toSet(),
                ).toTypedArray(),
            )
            return@LaunchedEffect
        }
        val permissionId = queue.first()
        permissionQueueNames = queue.drop(1).map(KiyoriPermissionId::name)
        waitingForExternalSettings = true
        val launched = handlePermissionAction(permissionId)
        if (!launched) {
            waitingForExternalSettings = false
        }
    }

    fun startAuthorization() {
        val selectable =
            sanitizeKiyoriPermissionSelection(
                snapshot = permissionSnapshot,
                selectedPermissionIds = selectedPermissionIds,
            )
        if (selectable.isEmpty()) {
            completeOnboarding()
            return
        }
        preferences.saveSelectedPermissions(selectable)
        permissionQueueNames = selectable.map(KiyoriPermissionId::name)
        authorizationActive = true
    }

    BackHandler {
        when {
            selectedLegalDocument != null -> selectedLegalDocument = null
            currentStep == KiyoriOnboardingStep.WELCOME -> context.findActivity().finish()
            authorizationActive -> Unit
            else -> previousKiyoriOnboardingStep(currentStep)?.let(::moveTo)
        }
    }

    val pagerInputEnabled =
        shouldEnableKiyoriOnboardingPagerInput(
            step = currentStep,
            agreementAccepted = agreementAcceptedState,
            interactionLocked = authorizationActive,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val legalDocument = selectedLegalDocument
        if (legalDocument != null) {
            KiyoriAgreementDocumentScreen(
                document = legalDocument,
                onBack = { selectedLegalDocument = null },
                modifier = Modifier.weight(1f),
            )
        } else {
            OnboardingProgressHeader(
                step = currentStep,
                onBack = {
                    previousKiyoriOnboardingStep(currentStep)?.let(::moveTo)
                },
                showBack = currentStep != KiyoriOnboardingStep.WELCOME && !authorizationActive,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = pagerInputEnabled,
                flingBehavior = pagerFlingBehavior,
                beyondViewportPageCount = 1,
                key = { pageIndex -> KiyoriOnboardingStep.entries[pageIndex].name },
            ) { pageIndex ->
                when (KiyoriOnboardingStep.entries[pageIndex]) {
                        KiyoriOnboardingStep.WELCOME ->
                            KiyoriWelcomePage(
                                onNext = { moveTo(KiyoriOnboardingStep.BROWSER_AND_MEDIA) },
                            )

                        KiyoriOnboardingStep.BROWSER_AND_MEDIA ->
                            FeatureIntroductionPage(
                                eyebrow =
                                    stringResource(
                                        R.string.kiyori_onboarding_browser_eyebrow,
                                    ),
                                title =
                                    stringResource(
                                        R.string.kiyori_onboarding_browser_title,
                                    ),
                                description =
                                    stringResource(
                                        R.string.kiyori_onboarding_browser_desc,
                                    ),
                                featureCards =
                                    listOf(
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.Language,
                                            tone = KiyoriSemanticTone.BLUE,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_browser_card_browser_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_browser_card_browser_desc,
                                                ),
                                        ),
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.Download,
                                            tone = KiyoriSemanticTone.GREEN,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_browser_card_download_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_browser_card_download_desc,
                                                ),
                                        ),
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.PlayCircle,
                                            tone = KiyoriSemanticTone.RED,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_browser_card_video_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_browser_card_video_desc,
                                                ),
                                        ),
                                        OnboardingFeatureCard(
                                            icon = Icons.AutoMirrored.Filled.MenuBook,
                                            tone = KiyoriSemanticTone.ORANGE,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_browser_card_reading_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_browser_card_reading_desc,
                                                ),
                                        ),
                                    ),
                                visual = { cards -> BrowserMediaVisual(cards) },
                                onNext = {
                                    moveTo(KiyoriOnboardingStep.AI_ASSISTANT)
                                },
                                nextLabel =
                                    stringResource(
                                        R.string.kiyori_onboarding_browser_next,
                                    ),
                            )

                        KiyoriOnboardingStep.AI_ASSISTANT ->
                            FeatureIntroductionPage(
                                eyebrow =
                                    stringResource(
                                        R.string.kiyori_onboarding_ai_eyebrow,
                                    ),
                                title =
                                    stringResource(
                                        R.string.kiyori_onboarding_ai_title,
                                    ),
                                description =
                                    stringResource(
                                        R.string.kiyori_onboarding_ai_desc,
                                    ),
                                featureCards =
                                    listOf(
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.AutoAwesome,
                                            tone = KiyoriSemanticTone.PURPLE,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_ai_card_assistant_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_ai_card_assistant_desc,
                                                ),
                                        ),
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.RecordVoiceOver,
                                            tone = KiyoriSemanticTone.CYAN,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_ai_card_voice_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_ai_card_voice_desc,
                                                ),
                                        ),
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.AccountCircle,
                                            tone = KiyoriSemanticTone.GREEN,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_ai_card_connection_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_ai_card_connection_desc,
                                                ),
                                        ),
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.Widgets,
                                            tone = KiyoriSemanticTone.BLUE,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_ai_card_toolbox_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_ai_card_toolbox_desc,
                                                ),
                                        ),
                                    ),
                                visual = { cards -> AiCollaborationVisual(cards) },
                                onNext = {
                                    moveTo(KiyoriOnboardingStep.FILES_AND_TOOLS)
                                },
                                nextLabel =
                                    stringResource(
                                        R.string.kiyori_onboarding_ai_next,
                                    ),
                            )

                        KiyoriOnboardingStep.FILES_AND_TOOLS ->
                            FeatureIntroductionPage(
                                eyebrow =
                                    stringResource(
                                        R.string.kiyori_onboarding_files_eyebrow,
                                    ),
                                title =
                                    stringResource(
                                        R.string.kiyori_onboarding_files_title,
                                    ),
                                description =
                                    stringResource(
                                        R.string.kiyori_onboarding_files_desc,
                                    ),
                                featureCards =
                                    listOf(
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.Folder,
                                            tone = KiyoriSemanticTone.ORANGE,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_files_card_manager_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_files_card_manager_desc,
                                                ),
                                        ),
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.Terminal,
                                            tone = KiyoriSemanticTone.BLUE,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_files_card_terminal_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_files_card_terminal_desc,
                                                ),
                                        ),
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.Apps,
                                            tone = KiyoriSemanticTone.GREEN,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_files_card_miniprogram_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_files_card_miniprogram_desc,
                                                ),
                                        ),
                                        OnboardingFeatureCard(
                                            icon = Icons.Default.BugReport,
                                            tone = KiyoriSemanticTone.PURPLE,
                                            title =
                                                stringResource(
                                                    R.string.kiyori_onboarding_files_card_logs_title,
                                                ),
                                            description =
                                                stringResource(
                                                    R.string.kiyori_onboarding_files_card_logs_desc,
                                                ),
                                        ),
                                    ),
                                visual = { cards -> FilesAndToolsVisual(cards) },
                                onNext = {
                                    moveTo(KiyoriOnboardingStep.AGREEMENT)
                                },
                                nextLabel =
                                    stringResource(
                                        R.string.kiyori_onboarding_files_next,
                                    ),
                            )

                        KiyoriOnboardingStep.AGREEMENT ->
                            KiyoriAgreementSummary(
                                checked = agreementChecked,
                                onCheckedChange = { agreementChecked = it },
                                onOpenUserAgreement = {
                                    selectedLegalDocument =
                                        KiyoriLegalDocument.USER_AGREEMENT
                                },
                                onOpenPrivacyPolicy = {
                                    selectedLegalDocument =
                                        KiyoriLegalDocument.PRIVACY_POLICY
                                },
                                onDecline = { context.findActivity().finish() },
                                onAccept = {
                                    if (!agreementAcceptedState) {
                                        onAgreementAccepted()
                                        agreementAcceptedState = true
                                        agreementChecked = true
                                    }
                                    moveTo(KiyoriOnboardingStep.PERMISSIONS)
                                },
                                agreementAlreadyAccepted = agreementAcceptedState,
                                modifier =
                                    Modifier.onboardingPreviousSwipe(
                                        enabled = !agreementAcceptedState,
                                        onPrevious = {
                                            resolveKiyoriOnboardingSwipeTarget(
                                                step = KiyoriOnboardingStep.AGREEMENT,
                                                direction =
                                                    KiyoriOnboardingSwipeDirection.PREVIOUS,
                                                agreementAccepted = agreementAcceptedState,
                                                interactionLocked = authorizationActive,
                                            )?.let(::moveTo)
                                        },
                                    ),
                            )

                        KiyoriOnboardingStep.PERMISSIONS ->
                            KiyoriPermissionAuthorizationPage(
                                snapshot = permissionSnapshot,
                                selectedPermissionIds = selectedPermissionIds,
                                authorizationActive = authorizationActive,
                                waitingForExternalSettings = waitingForExternalSettings,
                                onTogglePermission = { permissionId ->
                                    if (!authorizationActive &&
                                        permissionSnapshot.canSelect(permissionId)
                                    ) {
                                        selectedPermissionIds =
                                            if (permissionId in selectedPermissionIds) {
                                                selectedPermissionIds - permissionId
                                            } else {
                                                selectedPermissionIds + permissionId
                                            }
                                        preferences.saveSelectedPermissions(
                                            selectedPermissionIds,
                                        )
                                    }
                                },
                                onClearSelection = {
                                    if (!authorizationActive) {
                                        selectedPermissionIds = emptySet()
                                        preferences.clearSelectedPermissions()
                                    }
                                },
                                onAuthorize = ::startAuthorization,
                            )
                    }
            }
        }
    }
}

@Composable
private fun OnboardingProgressHeader(
    step: KiyoriOnboardingStep,
    onBack: () -> Unit,
    showBack: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.kiyori_onboarding_back),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OnboardingProgressBar(
                progress =
                    (step.ordinal + 1).toFloat() /
                        KiyoriOnboardingStep.entries.size,
            )
            Text(
                text =
                    stringResource(
                        R.string.kiyori_onboarding_progress,
                        step.ordinal + 1,
                        KiyoriOnboardingStep.entries.size,
                    ),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun OnboardingProgressBar(
    progress: Float,
) {
    val animatedProgress by
        animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            label = "KiyoriOnboardingProgress",
        )
    Box(
        modifier =
            Modifier
                .fillMaxWidth(0.68f)
                .height(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun Modifier.onboardingPreviousSwipe(
    enabled: Boolean,
    onPrevious: () -> Unit,
): Modifier {
    val thresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val layoutDirection = LocalLayoutDirection.current
    return pointerInput(enabled, thresholdPx, layoutDirection) {
        if (!enabled) {
            return@pointerInput
        }
        var accumulatedDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = { accumulatedDrag = 0f },
            onDragCancel = { accumulatedDrag = 0f },
            onDragEnd = {
                val isPreviousGesture =
                    when (layoutDirection) {
                        LayoutDirection.Ltr -> accumulatedDrag >= thresholdPx
                        LayoutDirection.Rtl -> accumulatedDrag <= -thresholdPx
                    }
                if (isPreviousGesture) {
                    onPrevious()
                }
                accumulatedDrag = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                accumulatedDrag += dragAmount
                change.consume()
            },
        )
    }
}

@Composable
private fun OnboardingEyebrow(
    text: String,
    tone: KiyoriSemanticTone,
) {
    val colors = tone.resolveColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(22.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colors.icon),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.icon,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class OnboardingFeatureCard(
    val icon: ImageVector,
    val tone: KiyoriSemanticTone,
    val title: String,
    val description: String,
)

@Composable
private fun OnboardingFeatureGrid(
    cards: List<OnboardingFeatureCard>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cards.chunked(2).forEach { rowCards ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowCards.forEach { card ->
                    OnboardingFeatureCardSurface(
                        card = card,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowCards.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OnboardingFeatureCardSurface(
    card: OnboardingFeatureCard,
    modifier: Modifier = Modifier,
) {
    val colors = card.tone.resolveColors()
    Surface(
        modifier =
            modifier
                .fillMaxHeight()
                .defaultMinSize(minHeight = 104.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.container),
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.Top,
        ) {
            KiyoriSemanticIconBadge(
                imageVector = card.icon,
                tone = card.tone,
                contentDescription = null,
                containerSize = 38.dp,
                iconSize = 20.dp,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = card.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun KiyoriWelcomePage(
    onNext: () -> Unit,
) {
    val featureCards =
        listOf(
            OnboardingFeatureCard(
                icon = Icons.Default.Explore,
                tone = KiyoriSemanticTone.BLUE,
                title =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_content_title,
                    ),
                description =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_content_desc,
                    ),
            ),
            OnboardingFeatureCard(
                icon = Icons.Default.SmartToy,
                tone = KiyoriSemanticTone.PURPLE,
                title =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_ai_title,
                    ),
                description =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_ai_desc,
                    ),
            ),
            OnboardingFeatureCard(
                icon = Icons.Default.Layers,
                tone = KiyoriSemanticTone.ORANGE,
                title =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_workspace_title,
                    ),
                description =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_workspace_desc,
                    ),
            ),
            OnboardingFeatureCard(
                icon = Icons.Default.Extension,
                tone = KiyoriSemanticTone.GREEN,
                title =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_extension_title,
                    ),
                description =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_extension_desc,
                    ),
            ),
        )
    FeatureIntroductionPage(
        eyebrow = null,
        title = stringResource(R.string.kiyori_onboarding_welcome_title),
        description = stringResource(R.string.kiyori_onboarding_welcome_desc),
        featureCards = featureCards,
        visual = { cards -> WelcomeProductVisual(cards) },
        onNext = onNext,
        nextLabel = stringResource(R.string.kiyori_onboarding_welcome_next),
    )
}

@Composable
private fun OnboardingFeatureIntroduction(
    eyebrow: String?,
    title: String,
    description: String,
    tone: KiyoriSemanticTone,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (eyebrow != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                OnboardingEyebrow(
                    text = eyebrow,
                    tone = tone,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 62.dp),
        ) {
            Text(
                text = title,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                lineHeight = 31.sp,
                textAlign = resolveKiyoriOnboardingTitleAlignment(eyebrow),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 66.dp),
        ) {
            Text(
                text = description,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        textIndent = TextIndent(firstLine = 2.em),
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
        if (eyebrow == null) {
            // 首页删除眉题后把等量节奏放到介绍与能力卡之间，标题更靠近主视觉，
            // 同时保持前四页 2×2 能力卡在常规手机视口中的垂直基线一致。
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

internal fun resolveKiyoriOnboardingTitleAlignment(eyebrow: String?): TextAlign =
    if (eyebrow == null) {
        TextAlign.Center
    } else {
        TextAlign.Start
    }

@Composable
private fun FeatureIntroductionPage(
    eyebrow: String?,
    title: String,
    description: String,
    featureCards: List<OnboardingFeatureCard>,
    visual: @Composable (List<OnboardingFeatureCard>) -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
) {
    check(featureCards.size == 4) {
        "Every Kiyori onboarding introduction page must contain exactly four feature cards"
    }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
    ) {
        val useTwoColumns = maxWidth >= 700.dp
        val mobileVisualHeight: Dp = 146.dp
        val content: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
            ) {
                OnboardingFeatureIntroduction(
                    eyebrow = eyebrow,
                    title = title,
                    description = description,
                    tone = featureCards.first().tone,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OnboardingFeatureGrid(
                    cards = featureCards,
                )
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            if (useTwoColumns) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(0.9f),
                        contentAlignment = Alignment.Center,
                    ) {
                        visual(featureCards)
                    }
                    Box(modifier = Modifier.weight(1.1f)) {
                        content()
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(mobileVisualHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        visual(featureCards)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    content()
                }
            }
            OnboardingPrimaryButton(
                text = nextLabel,
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showArrow: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .height(56.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (showArrow) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun WelcomeProductVisual(
    featureCards: List<OnboardingFeatureCard>,
) {
    val primaryColors = featureCards[0].tone.resolveColors()
    Box(
        modifier =
            Modifier
                .width(214.dp)
                .height(138.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    brush =
                        Brush.linearGradient(
                            listOf(
                                primaryColors.container.copy(alpha = 0.82f),
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                Color.Transparent,
                            ),
                        ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .width(148.dp)
                    .height(88.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
            shadowElevation = 5.dp,
        ) {
            Column(
                modifier = Modifier.padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) {
                        Box(
                            modifier =
                                Modifier
                                    .size(7.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape,
                                    ),
                        )
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(9.dp),
                            ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(2) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(25.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        RoundedCornerShape(9.dp),
                                    ),
                        )
                    }
                }
            }
        }
        VisualOrbitBadge(
            feature = featureCards[0],
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 7.dp, top = 5.dp),
        )
        VisualOrbitBadge(
            feature = featureCards[1],
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 7.dp, top = 5.dp),
        )
        VisualOrbitBadge(
            feature = featureCards[2],
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, bottom = 5.dp),
        )
        VisualOrbitBadge(
            feature = featureCards[3],
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 7.dp, bottom = 5.dp),
        )
    }
}

@Composable
private fun BrowserMediaVisual(
    featureCards: List<OnboardingFeatureCard>,
) {
    Column(
        modifier = Modifier.width(206.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VisualToolCard(
            feature = featureCards[0],
            height = 64.dp,
            showSignal = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            featureCards.drop(1).forEach { feature ->
                VisualToolCard(
                    feature = feature,
                    modifier = Modifier.weight(1f),
                    height = 58.dp,
                )
            }
        }
    }
}

@Composable
private fun AiCollaborationVisual(
    featureCards: List<OnboardingFeatureCard>,
) {
    Box(
        modifier =
            Modifier
                .width(214.dp)
                .height(138.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            featureCards[0].tone.resolveColors().container.copy(alpha = 0.78f),
                            Color.Transparent,
                        ),
                    ),
                ),
    ) {
        Surface(
            modifier =
                Modifier
                    .width(178.dp)
                    .height(92.dp)
                    .align(Alignment.Center),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VisualChatLine(
                    widthFraction = 0.78f,
                    color = KiyoriSemanticTone.PURPLE.resolveColors().container,
                )
                VisualChatLine(
                    widthFraction = 0.58f,
                    color = KiyoriSemanticTone.BLUE.resolveColors().container,
                )
                VisualChatLine(
                    widthFraction = 0.84f,
                    color = KiyoriSemanticTone.ORANGE.resolveColors().container,
                )
                VisualChatLine(
                    widthFraction = 0.44f,
                    color = KiyoriSemanticTone.GREEN.resolveColors().container,
                )
            }
        }
        VisualOrbitBadge(
            feature = featureCards[0],
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 4.dp),
        )
        VisualOrbitBadge(
            feature = featureCards[1],
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 5.dp, top = 4.dp),
        )
        VisualOrbitBadge(
            feature = featureCards[2],
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 5.dp, bottom = 4.dp),
        )
        VisualOrbitBadge(
            feature = featureCards[3],
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun FilesAndToolsVisual(
    featureCards: List<OnboardingFeatureCard>,
) {
    Column(
        modifier =
            Modifier
                .width(198.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VisualToolCard(
                feature = featureCards[0],
                modifier = Modifier.weight(1f),
                height = 55.dp,
            )
            VisualToolCard(
                feature = featureCards[1],
                modifier = Modifier.weight(1f),
                height = 55.dp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VisualToolCard(
                feature = featureCards[2],
                modifier = Modifier.weight(1f),
                height = 55.dp,
            )
            VisualToolCard(
                feature = featureCards[3],
                modifier = Modifier.weight(1f),
                height = 55.dp,
            )
        }
    }
}

@Composable
private fun VisualChatLine(
    widthFraction: Float,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(12.dp)
                .background(color, RoundedCornerShape(6.dp)),
    )
}

@Composable
private fun VisualToolCard(
    feature: OnboardingFeatureCard,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    showSignal: Boolean = false,
) {
    val colors = feature.tone.resolveColors()
    Surface(
        modifier =
            modifier
                .height(height),
        shape = RoundedCornerShape(18.dp),
        color = colors.container.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, colors.icon.copy(alpha = 0.12f)),
        shadowElevation = if (showSignal) 2.dp else 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement =
                if (showSignal) {
                    Arrangement.Start
                } else {
                    Arrangement.Center
                },
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                modifier = Modifier.size(if (showSignal) 28.dp else 26.dp),
                tint = colors.icon,
            )
            if (showSignal) {
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VisualChatLine(
                        widthFraction = 0.82f,
                        color = colors.icon.copy(alpha = 0.24f),
                    )
                    VisualChatLine(
                        widthFraction = 0.56f,
                        color = colors.icon.copy(alpha = 0.16f),
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualOrbitBadge(
    feature: OnboardingFeatureCard,
    modifier: Modifier = Modifier,
) {
    KiyoriSemanticIconBadge(
        imageVector = feature.icon,
        tone = feature.tone,
        contentDescription = null,
        modifier = modifier,
        containerSize = 44.dp,
        iconSize = 23.dp,
        shape = RoundedCornerShape(15.dp),
    )
}

@Composable
private fun KiyoriPermissionAuthorizationPage(
    snapshot: KiyoriPermissionSnapshot,
    selectedPermissionIds: Set<KiyoriPermissionId>,
    authorizationActive: Boolean,
    waitingForExternalSettings: Boolean,
    onTogglePermission: (KiyoriPermissionId) -> Unit,
    onClearSelection: () -> Unit,
    onAuthorize: () -> Unit,
) {
    val selectedCount =
        selectedPermissionIds.count { permissionId ->
            snapshot.canSelect(permissionId)
        }
    val groupedPermissionIds =
        remember {
            kiyoriPermissionGroups.flatMap(KiyoriPermissionGroupSpec::permissionIds)
        }
    check(groupedPermissionIds == KiyoriPermissionId.entries) {
        "Onboarding and Settings must render the same ordered permission catalog"
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.kiyori_onboarding_permissions_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 31.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.kiyori_onboarding_permissions_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PermissionOverviewCard(
                    snapshot = snapshot,
                    selectedCount = selectedCount,
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.kiyori_onboarding_permissions_all_items,
                            ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        onClick = onClearSelection,
                        enabled = selectedCount > 0 && !authorizationActive,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.kiyori_onboarding_permissions_clear_all,
                                ),
                        )
                    }
                }
            }
            kiyoriPermissionGroups.forEach { group ->
                item(key = "onboarding_permission_group_${group.id.name}") {
                    PermissionGroupHeader(group)
                }
                items(
                    items = group.permissionIds,
                    key = KiyoriPermissionId::name,
                ) { permissionId ->
                    PermissionItemCard(
                        permissionId = permissionId,
                        status = snapshot.status(permissionId),
                        selected = permissionId in selectedPermissionIds,
                        selectable = snapshot.canSelect(permissionId),
                        interactionEnabled = !authorizationActive,
                        onClick = { onTogglePermission(permissionId) },
                    )
                }
            }
        }
        OnboardingPrimaryButton(
            text =
                stringResource(
                    when {
                        authorizationActive ->
                            R.string.kiyori_onboarding_permissions_processing
                        selectedCount == 0 ->
                            R.string.kiyori_onboarding_permissions_enter
                        else ->
                            R.string.kiyori_onboarding_permissions_authorize_and_enter
                    },
                ),
            onClick = onAuthorize,
            enabled = !waitingForExternalSettings && !authorizationActive,
            showArrow = false,
            loading = authorizationActive,
        )
    }
}

@Composable
private fun PermissionOverviewCard(
    snapshot: KiyoriPermissionSnapshot,
    selectedCount: Int,
) {
    val summary = summarizeKiyoriPermissions(snapshot)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.kiyori_onboarding_permissions_overview_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PermissionOverviewMetric(
                    label = stringResource(R.string.kiyori_onboarding_permissions_ready),
                    value = summary.readyCount,
                    modifier = Modifier.weight(1f),
                )
                PermissionOverviewMetric(
                    label = stringResource(R.string.kiyori_onboarding_permissions_pending),
                    value = summary.actionRequiredCount,
                    modifier = Modifier.weight(1f),
                )
                PermissionOverviewMetric(
                    label = stringResource(R.string.kiyori_onboarding_permissions_on_demand),
                    value = summary.onDemandCount,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text =
                    stringResource(
                        R.string.kiyori_onboarding_permissions_selected_count,
                        selectedCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionOverviewMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PermissionGroupHeader(group: KiyoriPermissionGroupSpec) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = group.title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = group.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun PermissionItemCard(
    permissionId: KiyoriPermissionId,
    status: KiyoriPermissionStatus,
    selected: Boolean,
    selectable: Boolean,
    interactionEnabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val metadata = kiyoriPermissionMetadata(permissionId)
    val statusColors = kiyoriPermissionStatusTone(status).resolveColors()
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = selectable && interactionEnabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color =
            if (selected && selectable) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        border =
            if (selected && selectable) {
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                )
            } else {
                null
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectable) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = {
                        if (interactionEnabled) {
                            onClick()
                        }
                    },
                    enabled = interactionEnabled,
                )
            } else {
                Icon(
                    imageVector =
                        if (status == KiyoriPermissionStatus.ON_DEMAND) {
                            Icons.Default.Visibility
                        } else {
                            Icons.Default.CheckCircle
                        },
                    contentDescription = kiyoriPermissionStatusLabel(status),
                    tint = statusColors.icon,
                    modifier = Modifier.size(24.dp).padding(2.dp),
                )
            }
            KiyoriSemanticIconBadge(
                imageVector = metadata.icon,
                tone = metadata.tone,
                contentDescription = null,
                containerSize = 38.dp,
                iconSize = 20.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = metadata.title(context),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = kiyoriPermissionStatusLabel(status),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColors.icon,
                    )
                }
                Text(
                    text = metadata.description(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> error("Kiyori onboarding requires an Activity context")
    }

internal fun showKiyoriPermissionActionFailure(
    context: Context,
    permissionId: KiyoriPermissionId,
) {
    val message =
        context.getString(
            R.string.kiyori_onboarding_permission_action_failed,
            kiyoriPermissionMetadata(permissionId).title(context),
        )
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
