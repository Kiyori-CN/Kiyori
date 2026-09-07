package com.kiyori.integration.operit.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiyori.design.theme.KiyoriUiShapes
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun KiyoriOnboardingScreen(
    agreementAccepted: Boolean,
    onAgreementAccepted: () -> Unit,
    onComplete: () -> Unit,
    startFromBeginning: Boolean = false,
    onExitReview: (() -> Unit)? = null,
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
                persistedStep =
                    if (startFromBeginning) KiyoriOnboardingStep.WELCOME else preferences.readCurrentStep(),
                startFromBeginning = startFromBeginning,
            )
        }
    val pagerState =
        if (startFromBeginning) remember {
            // 重看是新阅读会话，不能让 SavedState 中的旧页码覆盖第一页。
            // 首启继续使用下方原生保存恢复；重看不写入首启偏好。
            PagerState(
                currentPage = KiyoriOnboardingStep.WELCOME.ordinal,
                pageCount = { kiyoriOnboardingPageCount(agreementAcceptedState) },
            )
        } else rememberPagerState(
            initialPage = initialStep.ordinal,
            pageCount = { kiyoriOnboardingPageCount(agreementAcceptedState) },
        )
    val pagerScope = rememberCoroutineScope()
    val pageStateHolder = rememberSaveableStateHolder()
    val currentStep = KiyoriOnboardingStep.entries[pagerState.settledPage]
    var navigationInFlight by remember { mutableStateOf(false) }
    val navigationBusy by remember {
        derivedStateOf { pagerState.isScrollInProgress || navigationInFlight }
    }
    val pagerFlingBehavior =
        PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
        )
    var agreementChecked by rememberSaveable { mutableStateOf(agreementAccepted) }
    // 重看页码重建为第一页时，旧协议正文也不能盖住新会话的首屏。
    var selectedLegalDocument by if (startFromBeginning) remember {
        mutableStateOf<KiyoriLegalDocument?>(null)
    } else rememberSaveable {
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
                    selectedPermissionIds =
                        if (startFromBeginning) emptySet() else preferences.readSelectedPermissions(),
                ),
            )
        }
    // 系统授权界面可能在进程回收后重新创建当前页面。授权队列只属于本次界面会话，
    // 用户选择则由 preferences 持久化，避免恢复后停在无法继续的处理中状态。
    var permissionQueueNames by remember { mutableStateOf(emptyList<String>()) }
    var authorizationActive by remember { mutableStateOf(false) }
    var runtimeRequestInFlight by remember { mutableStateOf(false) }
    var waitingForExternalSettings by remember { mutableStateOf(false) }
    var authorizationNeedsContinue by remember { mutableStateOf(false) }
    var authorizationGeneration by remember { mutableStateOf(0) }
    var completionDispatched by remember { mutableStateOf(false) }

    fun persistSelection(selection: Set<KiyoriPermissionId>) {
        if (!startFromBeginning) preferences.saveSelectedPermissions(selection)
    }

    fun moveTo(
        step: KiyoriOnboardingStep,
        sourceStep: KiyoriOnboardingStep = currentStep,
        skipIntroduction: Boolean = false,
    ) {
        if (!canNavigateKiyoriOnboarding(
                KiyoriOnboardingStep.entries[pagerState.settledPage], step, agreementAcceptedState,
                interactionLocked = authorizationActive || navigationBusy || completionDispatched,
                sourceStep = sourceStep,
                skipIntroduction = skipIntroduction,
            )) return
        navigationInFlight = true
        pagerScope.launch {
            // 单个动画任务避免快速点击争抢 Pager 的 scroll mutation；原生拖动仍可中断动画。
            try {
                pagerState.animateScrollToPage(step.ordinal, animationSpec = tween(280))
            } finally {
                navigationInFlight = false
            }
        }
    }

    LaunchedEffect(pagerState, preferences, startFromBeginning) {
        if (startFromBeginning) return@LaunchedEffect
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
            persistSelection(sanitizedSelection)
        }
    }

    fun completeOnboarding() {
        if (completionDispatched || !agreementAcceptedState || authorizationActive ||
            runtimeRequestInFlight || navigationBusy || currentStep != KiyoriOnboardingStep.PERMISSIONS) return
        completionDispatched = true
        // 重看是一段独立阅读会话，不改写初次安装的完成步骤与待授权选择。
        if (!startFromBeginning) preferences.complete()
        onComplete()
    }

    fun stopAuthorization() {
        authorizationGeneration++
        authorizationActive = false
        permissionQueueNames = emptyList()
        waitingForExternalSettings = false
        authorizationNeedsContinue = false
    }

    fun handlePermissionAction(permissionId: KiyoriPermissionId): Boolean {
        val generation = authorizationGeneration
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

                KiyoriPermissionActionKind.OPEN_RESTRICTED_SETTINGS -> {
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
                            if (generation != authorizationGeneration) return@launch
                            if (it) {
                                try {
                                    activateKiyoriShizukuExecution()
                                } catch (error: Exception) {
                                    KiyoriLogger.e(
                                        "KiyoriOnboarding",
                                        "Shizuku granted but execution state activation failed",
                                        error,
                                    )
                                }
                            }
                            if (generation != authorizationGeneration) return@launch
                            waitingForExternalSettings = false
                            refreshPermissions()
                        }
                    }
                    true
                }

                KiyoriPermissionActionKind.REQUEST_ROOT -> {
                    RootAuthorizer.requestRootPermission {
                        pagerScope.launch {
                            if (generation != authorizationGeneration) return@launch
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
        authorizationNeedsContinue,
    ) {
        if (!authorizationActive || runtimeRequestInFlight || waitingForExternalSettings ||
            authorizationNeedsContinue) {
            return@LaunchedEffect
        }
        val queue = permissionQueueNames.map(KiyoriPermissionId::valueOf)
            .filter(permissionSnapshot::canSelect)
        if (queue.isEmpty()) {
            permissionQueueNames = emptyList()
            authorizationActive = false
            refreshPermissions()
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
            authorizationNeedsContinue = true
            try {
                runtimePermissionLauncher.launch(
                    kiyoriRuntimePermissionsForSdk(
                        sdkInt = Build.VERSION.SDK_INT,
                        selectedPermissionIds = runtimeIds.toSet(),
                    ).toTypedArray(),
                )
            } catch (error: Exception) {
                runtimeRequestInFlight = false
                stopAuthorization()
                KiyoriLogger.e("KiyoriOnboarding", "无法发起所选运行时权限请求", error)
                showKiyoriPermissionActionFailure(context, runtimeIds.first())
            }
            return@LaunchedEffect
        }
        val permissionId = queue.first()
        permissionQueueNames = queue.drop(1).map(KiyoriPermissionId::name)
        authorizationGeneration++
        waitingForExternalSettings = true
        // 返回后只刷新事实，下一项必须由用户继续，避免连续拉起系统设置或自动退出引导。
        authorizationNeedsContinue = true
        val launched = handlePermissionAction(permissionId)
        if (!launched) {
            waitingForExternalSettings = false
        }
    }

    fun startAuthorization() {
        if (authorizationActive || runtimeRequestInFlight || navigationBusy || completionDispatched ||
            currentStep != KiyoriOnboardingStep.PERMISSIONS) return
        refreshPermissions()
        val selectable =
            sanitizeKiyoriPermissionSelection(
                snapshot = permissionSnapshot,
                selectedPermissionIds = selectedPermissionIds,
            )
        if (selectable.isEmpty()) {
            completeOnboarding()
            return
        }
        persistSelection(selectable)
        authorizationGeneration++
        authorizationNeedsContinue = false
        permissionQueueNames =
            orderKiyoriPermissionIdsForAuthorization(selectable.toList()).map(KiyoriPermissionId::name)
        authorizationActive = true
    }

    val handleBack: () -> Unit = {
        when {
            selectedLegalDocument != null -> selectedLegalDocument = null
            authorizationActive -> stopAuthorization()
            navigationBusy -> Unit
            currentStep == KiyoriOnboardingStep.WELCOME ->
                if (onExitReview != null) onExitReview() else context.findActivity().finish()
            else -> previousKiyoriOnboardingStep(currentStep)?.let { moveTo(it) }
        }
    }

    val pagerInputEnabled =
        shouldEnableKiyoriOnboardingPagerInput(
            interactionLocked = authorizationActive,
        )

    KiyoriOnboardingPresentation(reviewing = onExitReview != null, onBack = handleBack) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                    onBack = handleBack,
                    showBack = currentStep != KiyoriOnboardingStep.WELCOME || onExitReview != null,
                    // 滚动互斥在 moveTo 内校验，不让拖动反复切换按钮禁用配色。
                    enabled = !authorizationActive && !completionDispatched,
                    canStartTap = { !navigationBusy },
                    backLabel = if (currentStep == KiyoriOnboardingStep.WELCOME && onExitReview != null)
                        stringResource(R.string.kiyori_onboarding_review_return)
                    else stringResource(R.string.kiyori_onboarding_back),
                    onSkipIntroduction = {
                        moveTo(KiyoriOnboardingStep.AGREEMENT, skipIntroduction = true)
                    },
                )
                // 正文独立呈现时保留介绍/协议/权限的阅读位置，关闭正文后回到原处。
                pageStateHolder.SaveableStateProvider("onboarding_pages") {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).widthIn(max = 1080.dp).fillMaxWidth(),
                        userScrollEnabled = pagerInputEnabled,
                        flingBehavior = pagerFlingBehavior,
                        beyondViewportPageCount = 1,
                        key = { pageIndex -> KiyoriOnboardingStep.entries[pageIndex].name },
                    ) { pageIndex ->
                        when (KiyoriOnboardingStep.entries[pageIndex]) {
                            KiyoriOnboardingStep.WELCOME ->
                                KiyoriWelcomePage(
                                    navigationEnabled = !completionDispatched,
                                    canStartTap = { !navigationBusy && pagerState.settledPage == pageIndex },
                                    onNext = {
                                        moveTo(KiyoriOnboardingStep.BROWSER_AND_MEDIA, KiyoriOnboardingStep.WELCOME)
                                    },
                                )

                            KiyoriOnboardingStep.BROWSER_AND_MEDIA ->
                                FeatureIntroductionPage(
                                    navigationEnabled = !completionDispatched,
                                    canStartTap = { !navigationBusy && pagerState.settledPage == pageIndex },
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
                                                icon = Icons.Default.Shield,
                                                tone = KiyoriSemanticTone.ORANGE,
                                                title =
                                                    stringResource(
                                                        R.string.kiyori_onboarding_browser_card_adblock_title,
                                                    ),
                                                description =
                                                    stringResource(
                                                        R.string.kiyori_onboarding_browser_card_adblock_desc,
                                                    ),
                                            ),
                                        ),
                                    visual = { cards -> BrowserMediaVisual(cards) },
                                    onNext = {
                                        moveTo(KiyoriOnboardingStep.AI_ASSISTANT, KiyoriOnboardingStep.BROWSER_AND_MEDIA)
                                    },
                                    nextLabel =
                                        stringResource(
                                            R.string.kiyori_onboarding_browser_next,
                                        ),
                                )

                            KiyoriOnboardingStep.AI_ASSISTANT ->
                                FeatureIntroductionPage(
                                    navigationEnabled = !completionDispatched,
                                    canStartTap = { !navigationBusy && pagerState.settledPage == pageIndex },
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
                                                        R.string.kiyori_onboarding_ai_card_models_title,
                                                    ),
                                                description =
                                                    stringResource(
                                                        R.string.kiyori_onboarding_ai_card_models_desc,
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
                                                icon = Icons.Default.FolderSpecial,
                                                tone = KiyoriSemanticTone.GREEN,
                                                title =
                                                    stringResource(
                                                        R.string.kiyori_onboarding_ai_card_memory_title,
                                                    ),
                                                description =
                                                    stringResource(
                                                        R.string.kiyori_onboarding_ai_card_memory_desc,
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
                                        moveTo(KiyoriOnboardingStep.FILES_AND_TOOLS, KiyoriOnboardingStep.AI_ASSISTANT)
                                    },
                                    nextLabel =
                                        stringResource(
                                            R.string.kiyori_onboarding_ai_next,
                                        ),
                                )

                            KiyoriOnboardingStep.FILES_AND_TOOLS ->
                                FeatureIntroductionPage(
                                    navigationEnabled = !completionDispatched,
                                    canStartTap = { !navigationBusy && pagerState.settledPage == pageIndex },
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
                                                icon = Icons.Default.AccountTree,
                                                tone = KiyoriSemanticTone.GREEN,
                                                title =
                                                    stringResource(
                                                        R.string.kiyori_onboarding_files_card_workflow_title,
                                                    ),
                                                description =
                                                    stringResource(
                                                        R.string.kiyori_onboarding_files_card_workflow_desc,
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
                                        moveTo(KiyoriOnboardingStep.AGREEMENT, KiyoriOnboardingStep.FILES_AND_TOOLS)
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
                                        if (!navigationBusy && currentStep == KiyoriOnboardingStep.AGREEMENT) selectedLegalDocument =
                                            KiyoriLegalDocument.USER_AGREEMENT
                                    },
                                    onOpenPrivacyPolicy = {
                                        if (!navigationBusy && currentStep == KiyoriOnboardingStep.AGREEMENT) selectedLegalDocument =
                                            KiyoriLegalDocument.PRIVACY_POLICY
                                    },
                                    onDecline = {
                                        if (onExitReview != null) onExitReview() else context.findActivity().finish()
                                    },
                                    onAccept = {
                                        if (navigationBusy || currentStep != KiyoriOnboardingStep.AGREEMENT) return@KiyoriAgreementSummary
                                        if (!agreementAcceptedState) {
                                            onAgreementAccepted()
                                            agreementAcceptedState = true
                                            agreementChecked = true
                                        }
                                        moveTo(KiyoriOnboardingStep.PERMISSIONS, KiyoriOnboardingStep.AGREEMENT)
                                    },
                                    agreementAlreadyAccepted = agreementAcceptedState,
                                    interactionEnabled = !completionDispatched,
                                    reviewing = onExitReview != null,
                                )

                            KiyoriOnboardingStep.PERMISSIONS ->
                                KiyoriPermissionAuthorizationPage(
                                    snapshot = permissionSnapshot,
                                    selectedPermissionIds = selectedPermissionIds,
                                    authorizationActive = authorizationActive,
                                    waitingForExternalSettings = waitingForExternalSettings || runtimeRequestInFlight,
                                    navigationEnabled = !completionDispatched && !runtimeRequestInFlight,
                                    reviewing = onExitReview != null,
                                    authorizationNeedsContinue = authorizationNeedsContinue,
                                    remainingCount = permissionQueueNames.size,
                                    onContinueAuthorization = { authorizationNeedsContinue = false },
                                    onStopAuthorization = ::stopAuthorization,
                                    onFinish = ::completeOnboarding,
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
                                            persistSelection(selectedPermissionIds)
                                        }
                                    },
                                    onClearSelection = {
                                        if (!authorizationActive) {
                                            selectedPermissionIds = emptySet()
                                            persistSelection(emptySet())
                                        }
                                    },
                                    onAuthorize = ::startAuthorization,
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KiyoriOnboardingPresentation(
    reviewing: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (reviewing) {
        // 独立窗口隔离设置页的触摸与焦点，不消费子级 Final pass（那会取消拖动）。
        // Dialog 的系统 Back 必须经 onDismissRequest 回到同一个步骤处理器，
        // 不能依赖 Activity 的 BackHandler，否则会整段退出或吞掉返回。
        Dialog(
            onDismissRequest = onBack,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
            content = content,
        )
    } else {
        BackHandler(onBack = onBack)
        content()
    }
}

@Composable
private fun OnboardingProgressHeader(
    step: KiyoriOnboardingStep,
    onBack: () -> Unit,
    showBack: Boolean,
    enabled: Boolean,
    canStartTap: () -> Boolean,
    backLabel: String,
    onSkipIntroduction: () -> Unit,
) {
    val backGesture = remember { KiyoriOnboardingTapGesture() }
    val skipGesture = remember { KiyoriOnboardingTapGesture() }
    val latestCanStartTap = rememberUpdatedState(canStartTap)
    Column(
        Modifier.widthIn(max = 1080.dp).fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 所有六页保留相同 56dp 行高与 48dp 返回槽位。跳过按钮消失不能改变 Pager 高度。
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(
                    onClick = { if (backGesture.allowsClick) onBack() },
                    enabled = enabled,
                    modifier = Modifier.size(48.dp).onboardingTapOnly(backGesture) { latestCanStartTap.value() },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel)
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            Row(
                Modifier.weight(1f).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(step.onboardingLabelResId),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.kiyori_onboarding_progress, step.ordinal + 1, KiyoriOnboardingStep.entries.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (step.ordinal < KiyoriOnboardingStep.AGREEMENT.ordinal) {
                TextButton(
                    onClick = { if (skipGesture.allowsClick) onSkipIntroduction() },
                    enabled = enabled,
                    modifier = Modifier.onboardingTapOnly(skipGesture) { latestCanStartTap.value() },
                ) {
                    Text(stringResource(R.string.kiyori_onboarding_skip_intro), maxLines = 1)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = (step.ordinal + 1).toFloat(),
                    range = 0f..KiyoriOnboardingStep.entries.size.toFloat(),
                    steps = KiyoriOnboardingStep.entries.size - 1,
                )
            },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            KiyoriOnboardingStep.entries.forEach { item ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(
                    if (item.ordinal <= step.ordinal) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ))
            }
        }
    }
}

// 固定操作栏没有父级纵向滚动器替它取消拖动。Initial pass 只记录点击资格，
// Main pass 的原生 Button 保留焦点、键盘、无障碍与 ripple；Final pass 后才结束资格判断。
// 不消费 PointerInputChange，避免按钮附近的横向手势与 Pager 争抢事件。
private fun Modifier.onboardingTapOnly(
    gesture: KiyoriOnboardingTapGesture,
    canStartTap: () -> Boolean = { true },
): Modifier = pointerInput(gesture) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        gesture.begin(allowed = canStartTap())
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == down.id }
                gesture.update(
                    distance = pointer?.let { (it.position - down.position).getDistance() } ?: 0f,
                    touchSlop = viewConfiguration.touchSlop,
                    singlePointer = event.changes.size == 1 && pointer != null,
                    pressed = event.changes.any { it.pressed },
                )
                awaitPointerEvent(PointerEventPass.Final)
            } while (event.changes.any { it.pressed })
        } finally {
            gesture.end()
        }
    }
}

private val KiyoriOnboardingStep.onboardingLabelResId: Int
    get() =
        when (this) {
            KiyoriOnboardingStep.WELCOME -> R.string.kiyori_onboarding_step_welcome
            KiyoriOnboardingStep.BROWSER_AND_MEDIA -> R.string.kiyori_onboarding_step_content
            KiyoriOnboardingStep.AI_ASSISTANT -> R.string.kiyori_onboarding_step_ai
            KiyoriOnboardingStep.FILES_AND_TOOLS -> R.string.kiyori_onboarding_step_workspace
            KiyoriOnboardingStep.AGREEMENT -> R.string.kiyori_onboarding_step_trust
            KiyoriOnboardingStep.PERMISSIONS -> R.string.kiyori_onboarding_step_ready
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowCards.forEach { card ->
                    OnboardingFeatureCardSurface(
                        card = card,
                        modifier = Modifier.weight(1f),
                    )
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
                .defaultMinSize(minHeight = 104.dp),
        shape = KiyoriUiShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.container),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KiyoriSemanticIconBadge(
                imageVector = card.icon,
                tone = card.tone,
                contentDescription = null,
                containerSize = 38.dp,
                iconSize = 20.dp,
                shape = RoundedCornerShape(12.dp),
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                // 先按真实字体、字号缩放与可用宽度测量，避免 2×2 卡片在窄屏截字。
                // 显式换行固定阅读节奏；仅宽度不足时等比例收敛字号，不用省略号隐藏内容。
                fun fittedStyle(text: String, style: TextStyle): TextStyle {
                    fun fits(candidate: TextStyle): Boolean =
                        measurer.measure(text, candidate, softWrap = false).size.width <= constraints.maxWidth
                    if (fits(style)) return style
                    var lower = 0f
                    var upper = style.fontSize.value
                    // Android 大字体可使用非线性 sp 缩放，因此按实际测量二分，不能只除以宽度比例。
                    repeat(10) {
                        val size = (lower + upper) / 2f
                        if (fits(style.copy(fontSize = size.sp))) lower = size else upper = size
                    }
                    return style.copy(fontSize = lower.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = card.title,
                        style = fittedStyle(card.title, MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = card.description,
                        style = fittedStyle(card.description, MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp,
                        minLines = 2,
                        maxLines = 2,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun KiyoriWelcomePage(
    onNext: () -> Unit,
    navigationEnabled: Boolean,
    canStartTap: () -> Boolean,
) {
    val featureCards =
        listOf(
            OnboardingFeatureCard(
                icon = Icons.Default.AccountCircle,
                tone = KiyoriSemanticTone.BLUE,
                title =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_account_title,
                    ),
                description =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_account_desc,
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
                icon = Icons.Default.Extension,
                tone = KiyoriSemanticTone.ORANGE,
                title =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_extensions_title,
                    ),
                description =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_extensions_desc,
                    ),
            ),
            OnboardingFeatureCard(
                icon = Icons.Default.Hub,
                tone = KiyoriSemanticTone.GREEN,
                title =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_network_title,
                    ),
                description =
                    stringResource(
                        R.string.kiyori_onboarding_welcome_card_network_desc,
                    ),
            ),
        )
    FeatureIntroductionPage(
        navigationEnabled = navigationEnabled,
        canStartTap = canStartTap,
        eyebrow = stringResource(R.string.kiyori_onboarding_welcome_eyebrow),
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
                        .heightIn(min = 22.dp),
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
                    MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
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
    navigationEnabled: Boolean,
    canStartTap: () -> Boolean,
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
                .background(MaterialTheme.colorScheme.background)
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
                        modifier = Modifier.weight(0.9f).clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) {
                        visual(featureCards)
                    }
                    Box(modifier = Modifier.weight(1.1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)) {
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
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(mobileVisualHeight)
                                .clearAndSetSemantics {},
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border =
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f),
                            ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            visual(featureCards)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    content()
                }
            }
            OnboardingPrimaryButton(
                text = nextLabel,
                onClick = onNext,
                enabled = navigationEnabled,
                canStartTap = canStartTap,
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
    canStartTap: () -> Boolean = { true },
) {
    val gesture = remember { KiyoriOnboardingTapGesture() }
    val latestCanStartTap = rememberUpdatedState(canStartTap)
    Button(
        onClick = { if (gesture.allowsClick) onClick() },
        enabled = enabled,
        shape = KiyoriUiShapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .heightIn(min = 56.dp)
                .onboardingTapOnly(gesture) { latestCanStartTap.value() },
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
            modifier = Modifier.weight(1f, fill = false),
            textAlign = TextAlign.Center,
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
    navigationEnabled: Boolean,
    reviewing: Boolean,
    authorizationNeedsContinue: Boolean,
    remainingCount: Int,
    onContinueAuthorization: () -> Unit,
    onStopAuthorization: () -> Unit,
    onFinish: () -> Unit,
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
    check(groupedPermissionIds.size == KiyoriPermissionId.entries.size &&
        groupedPermissionIds.toSet() == KiyoriPermissionId.entries.toSet()) {
        "Onboarding and Settings must render the same ordered permission catalog"
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
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
                        enabled = selectedCount > 0 && !authorizationActive && navigationEnabled,
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
                    KiyoriPermissionDisclosure(
                        group = group,
                        selectedCount = group.permissionIds.count { it in selectedPermissionIds },
                    ) {
                        group.permissionIds.forEach { permissionId ->
                            PermissionItemCard(
                                permissionId = permissionId,
                                status = snapshot.status(permissionId),
                                selected = permissionId in selectedPermissionIds,
                                selectable = snapshot.canSelect(permissionId),
                                interactionEnabled = !authorizationActive && navigationEnabled,
                                onClick = { onTogglePermission(permissionId) },
                            )
                        }
                    }
                }
            }
            item { KiyoriPermissionScopeNote() }
        }
        val canContinue = authorizationActive && authorizationNeedsContinue && !waitingForExternalSettings
        if (authorizationActive) {
            Text(
                text = stringResource(
                    if (remainingCount == 0) R.string.kiyori_onboarding_permissions_result_hint
                    else R.string.kiyori_onboarding_permissions_continue_hint,
                    remainingCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        OnboardingPrimaryButton(
            text =
                stringResource(
                    when {
                        canContinue && remainingCount == 0 ->
                            R.string.kiyori_onboarding_permissions_result
                        canContinue -> R.string.kiyori_onboarding_permissions_continue
                        authorizationActive ->
                            R.string.kiyori_onboarding_permissions_processing
                        selectedCount == 0 && reviewing ->
                            R.string.kiyori_onboarding_review_done
                        selectedCount == 0 ->
                            R.string.kiyori_onboarding_permissions_enter
                        else ->
                            R.string.kiyori_onboarding_permissions_authorize_and_enter
                    },
                ),
            onClick = if (canContinue) onContinueAuthorization else onAuthorize,
            enabled = navigationEnabled && !waitingForExternalSettings && (!authorizationActive || canContinue),
            showArrow = false,
            loading = authorizationActive && !canContinue,
        )
        // 无论哪些系统入口不可用，都保留明确的停下路径；不撤销已经授予的系统权限。
        if (authorizationActive || selectedCount > 0) TextButton(
            onClick = if (authorizationActive) onStopAuthorization else onFinish,
            enabled = authorizationActive || navigationEnabled,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        ) {
            Text(stringResource(
                when {
                    authorizationActive -> R.string.kiyori_onboarding_permissions_stop
                    reviewing -> R.string.kiyori_onboarding_review_return
                    else -> R.string.kiyori_onboarding_permissions_later
                },
            ))
        } else {
            Spacer(Modifier.height(52.dp))
        }
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
                .toggleable(value = selected, enabled = selectable && interactionEnabled,
                    role = Role.Checkbox, onValueChange = { onClick() })
                .animateContentSize(),
        shape = KiyoriUiShapes.card,
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
                    onCheckedChange = null,
                    enabled = interactionEnabled,
                )
            } else {
                Icon(
                    imageVector =
                        if (status != KiyoriPermissionStatus.GRANTED) {
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
