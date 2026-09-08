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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.RootAuthorizer
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.ui.features.agreement.screens.KiyoriAgreementDocumentScreen
import com.ai.assistance.operit.ui.features.agreement.screens.KiyoriLegalDocument
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.design.theme.resolveColors
import com.kiyori.platform.logging.KiyoriLogger
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
    // 重看仍沿用协议所有者的已同意事实，只隔离页码和页面临时状态。
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
    // 重看是一次临时阅读会话，必须绕过首次安装流程的 SavedState。
    // 不能用时间戳制造 key：它既不是页面事实，也不能阻止 SaveableStateHolder 恢复旧子树。
    val pagerState =
        if (startFromBeginning) {
            // 该 PagerState 不进入 rememberSaveable；每次进入设置重看路由都会从第一页开始。
            remember {
                PagerState(
                    currentPage = KiyoriOnboardingStep.WELCOME.ordinal,
                    pageCount = { kiyoriOnboardingPageCount(agreementAcceptedState) },
                )
            }
        } else {
            rememberPagerState(
                initialPage = initialStep.ordinal,
                pageCount = { kiyoriOnboardingPageCount(agreementAcceptedState) },
            )
        }
    val pagerScope = rememberCoroutineScope()
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
    var userAgreementRead by rememberSaveable { mutableStateOf(false) }
    var privacyPolicyRead by rememberSaveable { mutableStateOf(false) }
    var showAgreementExitConfirmation by rememberSaveable { mutableStateOf(false) }
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
                        when {
                            startFromBeginning -> emptySet()
                            preferences.hasSelectedPermissionChoice() -> preferences.readSelectedPermissions()
                            else -> setOf(KiyoriPermissionId.NOTIFICATIONS, KiyoriPermissionId.MEDIA)
                        },
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


    // 调试日志：记录重看模式的初始化状态
    LaunchedEffect(Unit) {
        if (startFromBeginning) {
            KiyoriLogger.d(
                "KiyoriOnboarding",
                "重看模式初始化: agreementAcceptedState=$agreementAcceptedState, " +
                "pagerState.currentPage=${pagerState.currentPage}, " +
                "pageCount=${kiyoriOnboardingPageCount(agreementAcceptedState)}"
            )
        }
    }

    // 防御性监控：防止重看模式下页面异常跳转到权限页
    LaunchedEffect(pagerState.currentPage, startFromBeginning, agreementAcceptedState) {
        if (startFromBeginning) {
            // 重看模式下，如果协议未接受但页面跳到了权限页，强制回到协议页
            val maxAllowedPage = if (agreementAcceptedState) {
                KiyoriOnboardingStep.entries.lastIndex
            } else {
                KiyoriOnboardingStep.AGREEMENT.ordinal
            }
            
            if (pagerState.currentPage > maxAllowedPage) {
                KiyoriLogger.w(
                    "KiyoriOnboarding",
                    "重看模式检测到异常页面跳转: 当前=${pagerState.currentPage}, 最大允许=$maxAllowedPage, 协议接受=$agreementAcceptedState"
                )
                // 立即修正到最后一个允许的页面
                pagerState.scrollToPage(maxAllowedPage)
            }
        }
    }

    fun moveTo(
        step: KiyoriOnboardingStep,
        sourceStep: KiyoriOnboardingStep = currentStep,
        skipIntroduction: Boolean = false,
    ) {
        if (!canNavigateKiyoriOnboarding(
                KiyoriOnboardingStep.entries[pagerState.settledPage], step, agreementAcceptedState,
                interactionLocked = authorizationActive || runtimeRequestInFlight || navigationBusy || completionDispatched,
                sourceStep = sourceStep,
                skipIntroduction = skipIntroduction,
            )) return
        navigationInFlight = true
        pagerScope.launch {
            // 单个动画任务避免快速点击争抢 Pager 的 scroll mutation；原生拖动仍可中断动画。
            try {
                pagerState.animateScrollToPage(step.ordinal, animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing))
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
            runtimeRequestInFlight -> Unit
            authorizationActive -> stopAuthorization()
            navigationBusy -> Unit
            currentStep == KiyoriOnboardingStep.WELCOME ->
                if (onExitReview != null) onExitReview() else context.findActivity().finish()
            else -> previousKiyoriOnboardingStep(currentStep)?.let { moveTo(it) }
        }
    }

    val pagerInputEnabled =
        shouldEnableKiyoriOnboardingPagerInput(
            interactionLocked = authorizationActive || runtimeRequestInFlight,
        )

    KiyoriOnboardingPresentation(reviewing = onExitReview != null, onBack = handleBack) {
        KiyoriOnboardingTheme {
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
                    onSkipIntroduction = { sourceStep ->
                        moveTo(KiyoriOnboardingStep.AGREEMENT, sourceStep, skipIntroduction = true)
                    },
                )
                // 仅首次安装流程保存正文状态；重看流程必须与历史页面子树隔离。
                OnboardingPageStateHost(startFromBeginning = startFromBeginning) {
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
                                    eyebrow = stringResource(R.string.onb_p2_eyebrow),
                                    title = stringResource(R.string.onb_p2_headline),
                                    description = stringResource(R.string.onb_p2_lede),
                                    featureCards =
                                        listOf(
                                            OnboardingFeatureCard(
                                                icon = Icons.Default.Shield,
                                                tone = KiyoriSemanticTone.BLUE,
                                                title = stringResource(R.string.onb_card_adblock_title),
                                                description = stringResource(R.string.onb_card_adblock_line1) + "\n" +
                                                    stringResource(R.string.onb_card_adblock_line2),
                                            ),
                                            OnboardingFeatureCard(
                                                icon = Icons.Default.Download,
                                                tone = KiyoriSemanticTone.PURPLE,
                                                title = stringResource(R.string.onb_card_download_title),
                                                description = stringResource(R.string.onb_card_download_line1) + "\n" +
                                                    stringResource(R.string.onb_card_download_line2),
                                            ),
                                            OnboardingFeatureCard(
                                                icon = Icons.Default.PlayCircle,
                                                tone = KiyoriSemanticTone.GREEN,
                                                title = stringResource(R.string.onb_card_video_title),
                                                description = stringResource(R.string.onb_card_video_line1) + "\n" +
                                                    stringResource(R.string.onb_card_video_line2),
                                            ),
                                            OnboardingFeatureCard(
                                                icon = Icons.Outlined.MusicNote,
                                                tone = KiyoriSemanticTone.ORANGE,
                                                title = stringResource(R.string.onb_card_music_title),
                                                description = stringResource(R.string.onb_card_music_line1) + "\n" +
                                                    stringResource(R.string.onb_card_music_line2),
                                                badge = stringResource(R.string.onb_badge_wip),
                                            ),
                                        ),
                                    step = KiyoriOnboardingStep.BROWSER_AND_MEDIA,
                                    onNext = {
                                        moveTo(KiyoriOnboardingStep.AI_ASSISTANT, KiyoriOnboardingStep.BROWSER_AND_MEDIA)
                                    },
                                    nextLabel = stringResource(R.string.onb_p2_cta),
                                )

                            KiyoriOnboardingStep.AI_ASSISTANT ->
                                FeatureIntroductionPage(
                                    navigationEnabled = !completionDispatched,
                                    canStartTap = { !navigationBusy && pagerState.settledPage == pageIndex },
                                    eyebrow = stringResource(R.string.onb_p3_eyebrow),
                                    title = stringResource(R.string.onb_p3_headline),
                                    description = stringResource(R.string.onb_p3_lede),
                                    featureCards =
                                        listOf(
                                            OnboardingFeatureCard(
                                                badge = stringResource(R.string.onb_badge_setup),
                                                icon = Icons.Outlined.Tune,
                                                tone = KiyoriSemanticTone.BLUE,
                                                title = stringResource(R.string.onb_card_model_title),
                                                description = stringResource(R.string.onb_card_model_line1) + "\n" +
                                                    stringResource(R.string.onb_card_model_line2),
                                            ),
                                            OnboardingFeatureCard(
                                                badge = stringResource(R.string.onb_badge_setup),
                                                icon = Icons.Outlined.Mic,
                                                tone = KiyoriSemanticTone.PURPLE,
                                                title = stringResource(R.string.onb_card_voice_title),
                                                description = stringResource(R.string.onb_card_voice_line1) + "\n" +
                                                    stringResource(R.string.onb_card_voice_line2),
                                            ),
                                            OnboardingFeatureCard(
                                                icon = Icons.AutoMirrored.Outlined.MenuBook,
                                                tone = KiyoriSemanticTone.GREEN,
                                                title = stringResource(R.string.onb_card_memory_title),
                                                description = stringResource(R.string.onb_card_memory_line1) + "\n" +
                                                    stringResource(R.string.onb_card_memory_line2),
                                            ),
                                            OnboardingFeatureCard(
                                                icon = Icons.Outlined.WorkOutline,
                                                tone = KiyoriSemanticTone.ORANGE,
                                                title = stringResource(R.string.onb_card_toolbox_title),
                                                description = stringResource(R.string.onb_card_toolbox_line1) + "\n" +
                                                    stringResource(R.string.onb_card_toolbox_line2),
                                            ),
                                        ),
                                    step = KiyoriOnboardingStep.AI_ASSISTANT,
                                    onNext = {
                                        moveTo(KiyoriOnboardingStep.FILES_AND_TOOLS, KiyoriOnboardingStep.AI_ASSISTANT)
                                    },
                                    nextLabel = stringResource(R.string.onb_p3_cta),
                                )

                            KiyoriOnboardingStep.FILES_AND_TOOLS ->
                                FeatureIntroductionPage(
                                    navigationEnabled = !completionDispatched,
                                    canStartTap = { !navigationBusy && pagerState.settledPage == pageIndex },
                                    eyebrow = stringResource(R.string.onb_p4_eyebrow),
                                    title = stringResource(R.string.onb_p4_headline),
                                    description = stringResource(R.string.onb_p4_lede),
                                    featureCards =
                                        listOf(
                                            OnboardingFeatureCard(
                                                badge = stringResource(R.string.onb_badge_setup),
                                                icon = Icons.Default.Terminal,
                                                tone = KiyoriSemanticTone.BLUE,
                                                title = stringResource(R.string.onb_card_terminal_title),
                                                description = stringResource(R.string.onb_card_terminal_line1) + "\n" +
                                                    stringResource(R.string.onb_card_terminal_line2),
                                            ),
                                            OnboardingFeatureCard(
                                                icon = Icons.Default.AccountTree,
                                                tone = KiyoriSemanticTone.PURPLE,
                                                title = stringResource(R.string.onb_card_workflow_title),
                                                description = stringResource(R.string.onb_card_workflow_line1) + "\n" +
                                                    stringResource(R.string.onb_card_workflow_line2),
                                            ),
                                            OnboardingFeatureCard(
                                                icon = Icons.Default.Palette,
                                                tone = KiyoriSemanticTone.GREEN,
                                                title = stringResource(R.string.onb_card_theme_title),
                                                description = stringResource(R.string.onb_card_theme_line1) + "\n" +
                                                    stringResource(R.string.onb_card_theme_line2),
                                            ),
                                            OnboardingFeatureCard(
                                                icon = Icons.Default.Backup,
                                                tone = KiyoriSemanticTone.ORANGE,
                                                title = stringResource(R.string.onb_card_backup_title),
                                                description = stringResource(R.string.onb_card_backup_line1) + "\n" +
                                                    stringResource(R.string.onb_card_backup_line2),
                                            ),
                                        ),
                                    step = KiyoriOnboardingStep.FILES_AND_TOOLS,
                                    onNext = {
                                        moveTo(KiyoriOnboardingStep.AGREEMENT, KiyoriOnboardingStep.FILES_AND_TOOLS)
                                    },
                                    nextLabel = stringResource(R.string.onb_p4_cta),
                                )

                            KiyoriOnboardingStep.AGREEMENT ->
                                KiyoriOnboardingAgreementPage(
                                    checked = agreementChecked,
                                    userAgreementRead = userAgreementRead,
                                    privacyPolicyRead = privacyPolicyRead,
                                    onCheckedChange = {
                                        if (!agreementAcceptedState) agreementChecked = it
                                    },
                                    onOpenUserAgreement = {
                                        if (!navigationBusy && currentStep == KiyoriOnboardingStep.AGREEMENT) {
                                            userAgreementRead = true
                                            selectedLegalDocument = KiyoriLegalDocument.USER_AGREEMENT
                                        }
                                    },
                                    onOpenPrivacyPolicy = {
                                        if (!navigationBusy && currentStep == KiyoriOnboardingStep.AGREEMENT) {
                                            privacyPolicyRead = true
                                            selectedLegalDocument = KiyoriLegalDocument.PRIVACY_POLICY
                                        }
                                    },
                                    onDecline = {
                                        if (navigationBusy || currentStep != KiyoriOnboardingStep.AGREEMENT) return@KiyoriOnboardingAgreementPage
                                        if (onExitReview != null) onExitReview() else showAgreementExitConfirmation = true
                                    },
                                    onAccept = {
                                        if (navigationBusy || currentStep != KiyoriOnboardingStep.AGREEMENT) return@KiyoriOnboardingAgreementPage
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
                                    canStartTap = { !navigationBusy && pagerState.settledPage == pageIndex },
                                )

                            KiyoriOnboardingStep.PERMISSIONS ->
                                KiyoriPermissionAuthorizationPage(
                                    snapshot = permissionSnapshot,
                                    selectedPermissionIds = selectedPermissionIds,
                                    authorizationActive = authorizationActive,
                                    waitingForExternalSettings = waitingForExternalSettings || runtimeRequestInFlight,
                                    navigationEnabled = !completionDispatched && !runtimeRequestInFlight,
                                    canStartTap = { !navigationBusy && pagerState.settledPage == pageIndex },
                                    reviewing = onExitReview != null,
                                    authorizationNeedsContinue = authorizationNeedsContinue,
                                    remainingCount = permissionQueueNames.size,
                                    onContinueAuthorization = { authorizationNeedsContinue = false },
                                    onStopAuthorization = ::stopAuthorization,
                                    onFinish = ::completeOnboarding,
                                    onTogglePermission = { permissionId ->
                                        if (!authorizationActive && !navigationBusy &&
                                            currentStep == KiyoriOnboardingStep.PERMISSIONS &&
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
                                        if (!authorizationActive && !navigationBusy &&
                                            currentStep == KiyoriOnboardingStep.PERMISSIONS) {
                                            selectedPermissionIds = emptySet()
                                            persistSelection(emptySet())
                                        }
                                    },
                                    onSelectRecommended = {
                                        if (!authorizationActive && !navigationBusy &&
                                            currentStep == KiyoriOnboardingStep.PERMISSIONS
                                        ) {
                                            selectedPermissionIds =
                                                sanitizeKiyoriPermissionSelection(
                                                    snapshot = permissionSnapshot,
                                                    selectedPermissionIds =
                                                        setOf(
                                                            KiyoriPermissionId.NOTIFICATIONS,
                                                            KiyoriPermissionId.MEDIA,
                                                        ),
                                                )
                                            persistSelection(selectedPermissionIds)
                                        }
                                    },
                                    onAuthorize = ::startAuthorization,
                                )
                        }
                    }
                }
            }
            if (showAgreementExitConfirmation) {
                AlertDialog(
                    onDismissRequest = { showAgreementExitConfirmation = false },
                    title = { Text(stringResource(R.string.onb_p5_exit_title)) },
                    text = { Text(stringResource(R.string.onb_p5_exit_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showAgreementExitConfirmation = false
                                context.findActivity().finish()
                            },
                        ) {
                            Text(stringResource(R.string.onb_p5_exit_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAgreementExitConfirmation = false }) {
                            Text(stringResource(R.string.onb_p5_exit_cancel))
                        }
                    },
                )
            }
        }
    }
}
}

@Composable
private fun OnboardingPageStateHost(
    startFromBeginning: Boolean,
    content: @Composable () -> Unit,
) {
    if (startFromBeginning) {
        content()
    } else {
        // 首次安装允许中断后续看；重看流程不使用这个 holder，避免旧页码/协议正文泄漏。
        val stateHolder = rememberSaveableStateHolder()
        stateHolder.SaveableStateProvider("onboarding_pages", content)
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
    onSkipIntroduction: (KiyoriOnboardingStep) -> Unit,
) {
    val backGesture = remember { KiyoriOnboardingTapGesture() }
    val skipGesture = remember { KiyoriOnboardingTapGesture() }
    val latestCanStartTap = rememberUpdatedState(canStartTap)
    val pageAnnouncement = stringResource(
        R.string.kiyori_onboarding_page_announcement,
        step.ordinal + 1,
        KiyoriOnboardingStep.entries.size,
        stringResource(step.onboardingLabelResId),
    )
    Column(
        Modifier.widthIn(max = 1080.dp).fillMaxWidth()
            // 只有稳定的当前页播报；预组合页不能各自声明 paneTitle 抢焦点。
            .semantics { paneTitle = pageAnnouncement }
            .padding(horizontal = 8.dp),
    ) {
        // 六页共用参考稿的 48dp 顶栏；返回槽位始终保留，避免页间横向抖动。
        Row(
            Modifier.fillMaxWidth().height(KiyoriOnboardingMetrics.TopBarHeight.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(
                    onClick = onBack,
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
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.kiyori_onboarding_progress, step.ordinal + 1, KiyoriOnboardingStep.entries.size),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (step.ordinal < KiyoriOnboardingStep.AGREEMENT.ordinal) {
                TextButton(
                    onClick = { onSkipIntroduction(step) },
                    enabled = enabled,
                    modifier = Modifier.onboardingTapOnly(skipGesture) { latestCanStartTap.value() },
                ) {
                    Text(
                        stringResource(R.string.onb_skip),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 2.dp)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(KiyoriOnboardingMetrics.ProgressGap.dp),
        ) {
            KiyoriOnboardingStep.entries.forEach { item ->
                val isActive = item.ordinal <= step.ordinal
                Box(
                    Modifier
                        .weight(1f)
                        .height(KiyoriOnboardingMetrics.ProgressHeight.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else currentKiyoriOnboardingColors().outline,
                        )
                )
            }
        }
    }
}

// 固定操作栏没有纵向滚动父级替它取消拖动。必须在原生 clickable 看到抬起之前
// 消费无效的 up：onClick 可能异步派发，不能在 Final pass 后重置资格并让它复活。
// 不消费移动事件，Pager 仍持有横向拖动；键盘和无障碍走原生 onClick，不受旧触摸影响。
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
                if (!gesture.allowsClick) {
                    event.changes.filter { it.previousPressed && !it.pressed }.forEach { it.consume() }
                }
                awaitPointerEvent(PointerEventPass.Final)
            } while (event.changes.any { it.pressed })
        } finally {
            gesture.end()
        }
    }
}

@Composable
private fun Modifier.onboardingTapOnly(canStartTap: () -> Boolean): Modifier {
    val gesture = remember { KiyoriOnboardingTapGesture() }
    val latestCanStartTap = rememberUpdatedState(canStartTap)
    return onboardingTapOnly(gesture) { latestCanStartTap.value() }
}

private val KiyoriOnboardingStep.onboardingLabelResId: Int
    get() =
        when (this) {
            KiyoriOnboardingStep.WELCOME -> R.string.onb_p1_nav
            KiyoriOnboardingStep.BROWSER_AND_MEDIA -> R.string.onb_p2_nav
            KiyoriOnboardingStep.AI_ASSISTANT -> R.string.onb_p3_nav
            KiyoriOnboardingStep.FILES_AND_TOOLS -> R.string.onb_p4_nav
            KiyoriOnboardingStep.AGREEMENT -> R.string.onb_p5_nav
            KiyoriOnboardingStep.PERMISSIONS -> R.string.onb_p6_nav
        }

@Composable
private fun OnboardingEyebrow(
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class OnboardingFeatureCard(
    val icon: ImageVector,
    val tone: KiyoriSemanticTone,
    val title: String,
    val description: String,
    val badge: String? = null,
)

@Composable
private fun OnboardingFeatureGrid(cards: List<OnboardingFeatureCard>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 常规字号保持设计稿的 2×2 布局；从 1.3 倍起改为单列，避免压缩字号或截断标题。
        val columns = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(KiyoriOnboardingMetrics.GridGap.dp)) {
            cards.chunked(columns).forEach { rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(KiyoriOnboardingMetrics.GridGap.dp),
                ) {
                    rowCards.forEach { card ->
                        OnboardingFeatureCardSurface(card, Modifier.weight(1f).fillMaxHeight())
                    }
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
    val palette = currentKiyoriOnboardingColors()
    val toneIndex =
        when (card.tone) {
            KiyoriSemanticTone.BLUE -> 0
            KiyoriSemanticTone.PURPLE -> 1
            KiyoriSemanticTone.GREEN -> 2
            else -> 3
        }
    val colors = palette.tones[toneIndex]
    val descriptionLines = card.description.split('\n', limit = 2)
    val showSecondLine = LocalDensity.current.fontScale < 1.6f
    Surface(
        modifier =
            modifier
                .defaultMinSize(minHeight = KiyoriOnboardingMetrics.CardMinHeight.dp)
                .shadow(1.dp, RoundedCornerShape(KiyoriOnboardingMetrics.CardRadius.dp), clip = false)
                .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(KiyoriOnboardingMetrics.CardRadius.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.outline),
    ) {
        Column(
            modifier = Modifier.padding(KiyoriOnboardingMetrics.CardPadding.dp),
        ) {
            Surface(
                modifier = Modifier.size(KiyoriOnboardingMetrics.IconBox.dp),
                shape = RoundedCornerShape(11.dp),
                color = colors.background,
                contentColor = colors.foreground,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = null,
                        modifier = Modifier.size(KiyoriOnboardingMetrics.IconSize.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    card.title,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                card.badge?.let { badge ->
                    val workInProgress = badge == stringResource(R.string.onb_badge_wip)
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (workInProgress) palette.tones[3].background else palette.primaryWeak,
                    ) {
                        Text(
                            badge,
                            Modifier.padding(horizontal = 5.dp),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (workInProgress) palette.warning else palette.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = descriptionLines.first(),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = palette.onSurfaceVariant,
            )
            if (showSecondLine && descriptionLines.size > 1) {
                Text(
                    text = descriptionLines[1],
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = palette.onSurfaceVariant.copy(alpha = 0.82f),
                )
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
                icon = Icons.Default.Language,
                tone = KiyoriSemanticTone.BLUE,
                title = stringResource(R.string.onb_card_browser_title),
                description = stringResource(R.string.onb_card_browser_line1) + "\n" +
                    stringResource(R.string.onb_card_browser_line2),
            ),
            OnboardingFeatureCard(
                icon = Icons.Default.SmartToy,
                tone = KiyoriSemanticTone.PURPLE,
                title = stringResource(R.string.onb_card_ai_title),
                description = stringResource(R.string.onb_card_ai_line1) + "\n" +
                    stringResource(R.string.onb_card_ai_line2),
            ),
            OnboardingFeatureCard(
                icon = Icons.Default.Widgets,
                tone = KiyoriSemanticTone.GREEN,
                title = stringResource(R.string.onb_card_miniapp_title),
                description = stringResource(R.string.onb_card_miniapp_line1) + "\n" +
                    stringResource(R.string.onb_card_miniapp_line2),
                badge = stringResource(R.string.onb_badge_wip),
            ),
            OnboardingFeatureCard(
                icon = Icons.Default.Folder,
                tone = KiyoriSemanticTone.ORANGE,
                title = stringResource(R.string.onb_card_files_title),
                description = stringResource(R.string.onb_card_files_line1) + "\n" +
                    stringResource(R.string.onb_card_files_line2),
            ),
        )
    FeatureIntroductionPage(
        navigationEnabled = navigationEnabled,
        canStartTap = canStartTap,
        eyebrow = stringResource(R.string.onb_p1_eyebrow),
        title = stringResource(R.string.onb_p1_headline),
        description = stringResource(R.string.onb_p1_lede),
        featureCards = featureCards,
        step = KiyoriOnboardingStep.WELCOME,
        onNext = onNext,
        nextLabel = stringResource(R.string.onb_p1_cta),
    )
}

@Composable
private fun OnboardingFeatureIntroduction(
    eyebrow: String?,
    title: String,
    description: String,
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
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(),
        ) {
            Text(
                text = title,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp,
                textAlign = resolveKiyoriOnboardingTitleAlignment(eyebrow),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(),
        ) {
            Text(
                text = description,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp,
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
    step: KiyoriOnboardingStep,
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
        val useTwoColumns = maxWidth >= 600.dp
        val showMap = maxHeight >= 440.dp && LocalDensity.current.fontScale < 1.3f
        val titleBlock: @Composable () -> Unit = {
            if (showMap) {
                OnboardingNavigationMap(step)
                Spacer(Modifier.height(16.dp))
            }
            OnboardingFeatureIntroduction(eyebrow, title, description)
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // 正文与固定操作栏分配实际高度，不用固定 96dp 覆盖层猜测按钮在大字体下的高度。
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(top = 12.dp, bottom = 20.dp),
            ) {
                if (useTwoColumns) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f)) { titleBlock() }
                        Box(Modifier.weight(1f)) { OnboardingFeatureGrid(featureCards) }
                    }
                } else {
                    titleBlock()
                    Spacer(Modifier.height(14.dp))
                    OnboardingFeatureGrid(featureCards)
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    stringResource(when (step) {
                        KiyoriOnboardingStep.WELCOME -> R.string.onb_p1_note
                        KiyoriOnboardingStep.BROWSER_AND_MEDIA -> R.string.onb_p2_note
                        KiyoriOnboardingStep.AI_ASSISTANT -> R.string.onb_p3_note
                        else -> R.string.onb_p4_note
                    }),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OnboardingBottomActions {
                OnboardingPrimaryButton(
                    text = nextLabel,
                    onClick = onNext,
                    enabled = navigationEnabled,
                    canStartTap = canStartTap,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    canStartTap: () -> Boolean = { true },
) {
    val palette = currentKiyoriOnboardingColors()
    val gesture = remember { KiyoriOnboardingTapGesture() }
    val latestCanStartTap = rememberUpdatedState(canStartTap)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(KiyoriOnboardingMetrics.PrimaryButtonRadius.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = palette.primary,
                contentColor = palette.onPrimary,
                disabledContainerColor = palette.outline,
                disabledContentColor = palette.onSurfaceVariant,
            ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(KiyoriOnboardingMetrics.PrimaryButtonHeight.dp)
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
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnboardingBottomActions(content: @Composable () -> Unit) {
    val palette = currentKiyoriOnboardingColors()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to palette.background.copy(alpha = 0f),
                        0.42f to palette.background,
                        1f to palette.background,
                    ),
                )
                .padding(top = 16.dp, bottom = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun OnboardingNavigationMap(step: KiyoriOnboardingStep) {
    val palette = currentKiyoriOnboardingColors()
    val panelStates =
        when (step) {
            KiyoriOnboardingStep.WELCOME -> listOf(true, true, true)
            KiyoriOnboardingStep.BROWSER_AND_MEDIA -> listOf(true, false, false)
            KiyoriOnboardingStep.AI_ASSISTANT,
            KiyoriOnboardingStep.FILES_AND_TOOLS,
            -> listOf(false, false, true)
            else -> listOf(false, false, false)
        }
    val barStates =
        when (step) {
            KiyoriOnboardingStep.WELCOME -> setOf(0, 1, 2, 3, 4)
            KiyoriOnboardingStep.BROWSER_AND_MEDIA -> setOf(1)
            KiyoriOnboardingStep.FILES_AND_TOOLS -> setOf(4)
            else -> emptySet()
        }
    val panels =
        listOf(
            Triple(Icons.Default.Download, stringResource(R.string.kiyori_onboarding_map_left), false),
            Triple(Icons.Default.Widgets, stringResource(R.string.kiyori_onboarding_map_home), true),
            Triple(Icons.Default.AutoAwesome, stringResource(R.string.kiyori_onboarding_map_ai), false),
        )
    Surface(
        modifier = Modifier.fillMaxWidth().height(KiyoriOnboardingMetrics.MapHeight.dp),
        shape = RoundedCornerShape(KiyoriOnboardingMetrics.MapRadius.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.outline),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                panels.forEachIndexed { index, (icon, label, isMiddle) ->
                    OnboardingNavigationPanel(
                        icon = icon,
                        label = label,
                        active = panelStates[index],
                        isMiddle = isMiddle,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(
                modifier =
                    Modifier
                        .width(104.dp)
                        .height(15.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = palette.outline,
                                cornerRadius = CornerRadius(5.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(5) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index in barStates) palette.primary else palette.outline),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(when (step) {
                    KiyoriOnboardingStep.WELCOME -> R.string.onb_p1_map_cap
                    KiyoriOnboardingStep.BROWSER_AND_MEDIA -> R.string.onb_p2_map_cap
                    KiyoriOnboardingStep.AI_ASSISTANT -> R.string.onb_p3_map_cap
                    else -> R.string.onb_p4_map_cap
                }),
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                color = palette.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingNavigationPanel(
    icon: ImageVector,
    label: String,
    active: Boolean,
    isMiddle: Boolean,
) {
    val palette = currentKiyoriOnboardingColors()
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier =
            Modifier
                .width(if (isMiddle) 104.dp else 74.dp)
                .height(if (isMiddle) 56.dp else 50.dp)
                .clip(shape)
                .background(if (active) palette.primaryWeak else palette.surface)
                .drawBehind {
                    drawRoundRect(
                        color = if (active) palette.primary else palette.outline,
                        cornerRadius = CornerRadius(9.dp.toPx()),
                        style =
                            Stroke(
                                width = 1.dp.toPx(),
                                pathEffect =
                                    if (active) null else PathEffect.dashPathEffect(
                                        floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                                    ),
                            ),
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (active) palette.primary else palette.onSurfaceVariant,
            )
            Text(
                text = label,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = if (active) palette.primary else palette.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KiyoriOnboardingAgreementPage(
    checked: Boolean,
    userAgreementRead: Boolean,
    privacyPolicyRead: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
    agreementAlreadyAccepted: Boolean,
    interactionEnabled: Boolean,
    reviewing: Boolean,
    canStartTap: () -> Boolean,
) {
    val palette = currentKiyoriOnboardingColors()
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = KiyoriOnboardingMetrics.PagePadding.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp, bottom = 20.dp),
        ) {
            OnboardingFeatureIntroduction(
                eyebrow = stringResource(R.string.onb_p5_eyebrow),
                title = stringResource(R.string.onb_p5_headline),
                description = stringResource(R.string.onb_p5_lede),
            )
            Spacer(Modifier.height(12.dp))
            AgreementNotice()
            Spacer(Modifier.height(8.dp))
            AgreementDocumentCard(
                document = KiyoriLegalDocument.USER_AGREEMENT,
                description = stringResource(R.string.onb_p5_doc_terms_desc),
                read = userAgreementRead || agreementAlreadyAccepted,
                toneIndex = 0,
                enabled = interactionEnabled,
                onClick = onOpenUserAgreement,
            )
            Spacer(Modifier.height(8.dp))
            AgreementDocumentCard(
                document = KiyoriLegalDocument.PRIVACY_POLICY,
                description = stringResource(R.string.onb_p5_doc_privacy_desc),
                read = privacyPolicyRead || agreementAlreadyAccepted,
                toneIndex = 2,
                enabled = interactionEnabled,
                onClick = onOpenPrivacyPolicy,
            )
            Spacer(Modifier.height(9.dp))
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            enabled = !agreementAlreadyAccepted && interactionEnabled,
                            role = Role.Checkbox,
                            onValueChange = onCheckedChange,
                        ),
                shape = RoundedCornerShape(14.dp),
                color = palette.surface,
                border = BorderStroke(1.dp, palette.outline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        enabled = !agreementAlreadyAccepted && interactionEnabled,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = stringResource(R.string.onb_p5_checkbox),
                        modifier = Modifier.weight(1f),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text =
                    stringResource(
                        R.string.onb_p5_version_line,
                        AgreementPreferences.CURRENT_AGREEMENT_VERSION,
                    ),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = palette.onSurfaceVariant,
            )
        }
        OnboardingBottomActions {
            OnboardingPrimaryButton(
                text =
                    stringResource(
                        if (agreementAlreadyAccepted) R.string.onb_p5_cta_replay
                        else R.string.onb_p5_cta,
                    ),
                onClick = onAccept,
                enabled = interactionEnabled && checked,
                canStartTap = canStartTap,
            )
            TextButton(
                onClick = onDecline,
                enabled = interactionEnabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(KiyoriOnboardingMetrics.SecondaryButtonHeight.dp)
                        .onboardingTapOnly(canStartTap),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.onSurfaceVariant),
            ) {
                Text(
                    text =
                        stringResource(
                            if (reviewing) R.string.onb_p5_secondary_replay
                            else R.string.onb_p5_secondary,
                        ),
                    fontSize = 13.5.sp,
                )
            }
        }
    }
}

@Composable
private fun AgreementNotice() {
    val palette = currentKiyoriOnboardingColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = palette.info,
    ) {
        Column(
            modifier = Modifier.padding(start = 13.dp, top = 4.dp, end = 13.dp, bottom = 11.dp),
        ) {
            AgreementNoticeItem(R.string.onb_p5_point1_title, R.string.onb_p5_point1_body)
            AgreementNoticeItem(R.string.onb_p5_point2_title, R.string.onb_p5_point2_body)
            AgreementNoticeItem(R.string.onb_p5_point3_title, R.string.onb_p5_point3_body)
        }
    }
}

@Composable
private fun AgreementNoticeItem(
    titleResId: Int,
    bodyResId: Int,
) {
    val palette = currentKiyoriOnboardingColors()
    Column(modifier = Modifier.padding(top = 9.dp)) {
        Text(
            text = stringResource(titleResId),
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(bodyResId),
            modifier = Modifier.padding(top = 3.dp),
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = palette.onSurfaceVariant,
        )
    }
}

@Composable
private fun AgreementDocumentCard(
    document: KiyoriLegalDocument,
    description: String,
    read: Boolean,
    toneIndex: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = currentKiyoriOnboardingColors()
    val tone = palette.tones[toneIndex]
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(14.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(tone.background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (document == KiyoriLegalDocument.USER_AGREEMENT) {
                            Icons.Outlined.Description
                        } else {
                            Icons.Outlined.Security
                        },
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = tone.foreground,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(document.titleResId),
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = palette.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(if (read) R.string.onb_doc_read else R.string.onb_doc_unread) + " ›",
                fontSize = 10.5.sp,
                color = palette.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KiyoriPermissionAuthorizationPage(
    snapshot: KiyoriPermissionSnapshot,
    selectedPermissionIds: Set<KiyoriPermissionId>,
    authorizationActive: Boolean,
    waitingForExternalSettings: Boolean,
    navigationEnabled: Boolean,
    canStartTap: () -> Boolean,
    reviewing: Boolean,
    authorizationNeedsContinue: Boolean,
    remainingCount: Int,
    onContinueAuthorization: () -> Unit,
    onStopAuthorization: () -> Unit,
    onFinish: () -> Unit,
    onTogglePermission: (KiyoriPermissionId) -> Unit,
    onClearSelection: () -> Unit,
    onSelectRecommended: () -> Unit,
    onAuthorize: () -> Unit,
) {
    val palette = currentKiyoriOnboardingColors()
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
                .padding(horizontal = KiyoriOnboardingMetrics.PagePadding.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
        ) {
            item {
                OnboardingFeatureIntroduction(
                    eyebrow = stringResource(R.string.onb_p6_eyebrow),
                    title = stringResource(R.string.onb_p6_headline),
                    description = stringResource(R.string.onb_p6_lede),
                )
                Spacer(Modifier.height(12.dp))
                PermissionSelectionSummary(
                    selectedCount = selectedCount,
                    interactionEnabled = !authorizationActive && navigationEnabled,
                    onSelectRecommended = onSelectRecommended,
                    onClearSelection = onClearSelection,
                )
                Spacer(Modifier.height(12.dp))
                OnboardingPermissionGroupHeader(
                    title = stringResource(R.string.onb_p6_daily_title),
                    hint = stringResource(R.string.onb_p6_daily_count),
                )
                PermissionPreviewRow(
                    permissionId = KiyoriPermissionId.NOTIFICATIONS,
                    title = stringResource(R.string.onb_p6_permission_notification),
                    description = stringResource(R.string.onb_p6_permission_notification_desc),
                    toneIndex = 0,
                    status = snapshot.status(KiyoriPermissionId.NOTIFICATIONS),
                    selected = KiyoriPermissionId.NOTIFICATIONS in selectedPermissionIds,
                    selectable = snapshot.canSelect(KiyoriPermissionId.NOTIFICATIONS),
                    interactionEnabled = !authorizationActive && navigationEnabled,
                    onClick = { onTogglePermission(KiyoriPermissionId.NOTIFICATIONS) },
                )
                PermissionPreviewRow(
                    permissionId = KiyoriPermissionId.MEDIA,
                    title = stringResource(R.string.onb_p6_permission_media),
                    description = stringResource(R.string.onb_p6_permission_media_desc),
                    toneIndex = 1,
                    status = snapshot.status(KiyoriPermissionId.MEDIA),
                    selected = KiyoriPermissionId.MEDIA in selectedPermissionIds,
                    selectable = snapshot.canSelect(KiyoriPermissionId.MEDIA),
                    interactionEnabled = !authorizationActive && navigationEnabled,
                    onClick = { onTogglePermission(KiyoriPermissionId.MEDIA) },
                )
                PermissionPreviewRow(
                    permissionId = KiyoriPermissionId.MICROPHONE,
                    title = stringResource(R.string.onb_p6_permission_microphone),
                    description = stringResource(R.string.onb_p6_permission_microphone_desc),
                    toneIndex = 2,
                    status = snapshot.status(KiyoriPermissionId.MICROPHONE),
                    selected = KiyoriPermissionId.MICROPHONE in selectedPermissionIds,
                    selectable = snapshot.canSelect(KiyoriPermissionId.MICROPHONE),
                    interactionEnabled = !authorizationActive && navigationEnabled,
                    onClick = { onTogglePermission(KiyoriPermissionId.MICROPHONE) },
                )
                Spacer(Modifier.height(12.dp))
                OnboardingPermissionGroupHeader(
                    title = stringResource(R.string.onb_p6_storage_title),
                    hint = stringResource(R.string.onb_p6_storage_count),
                )
                PermissionPreviewRow(
                    permissionId = KiyoriPermissionId.ALL_FILES,
                    title = stringResource(R.string.onb_p6_permission_files),
                    description = stringResource(R.string.onb_p6_permission_files_desc),
                    toneIndex = 3,
                    status = snapshot.status(KiyoriPermissionId.ALL_FILES),
                    selected = KiyoriPermissionId.ALL_FILES in selectedPermissionIds,
                    selectable = false,
                    interactionEnabled = false,
                    stateText = stringResource(R.string.onb_p6_use_when_needed),
                    onClick = {},
                )
                PermissionPreviewRow(
                    permissionId = KiyoriPermissionId.INSTALL_PACKAGES,
                    title = stringResource(R.string.onb_p6_permission_install),
                    description = stringResource(R.string.onb_p6_permission_install_desc),
                    toneIndex = 0,
                    status = snapshot.status(KiyoriPermissionId.INSTALL_PACKAGES),
                    selected = KiyoriPermissionId.INSTALL_PACKAGES in selectedPermissionIds,
                    selectable = false,
                    interactionEnabled = false,
                    stateText = stringResource(R.string.onb_p6_use_when_needed),
                    onClick = {},
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = palette.outline)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onb_p6_catalog_title),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.onb_p6_advanced_hint),
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = palette.onSurfaceVariant,
                )
            }
            kiyoriPermissionGroups.forEach { group ->
                item(key = "onboarding_permission_group_${group.id.name}") {
                    KiyoriPermissionDisclosure(
                        group = group.copy(initiallyExpanded = false),
                        selectedCount = group.permissionIds.count { it in selectedPermissionIds },
                        autoExpandWhenSelected = false,
                        modifier = Modifier.padding(top = 8.dp),
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
        OnboardingBottomActions {
            OnboardingPrimaryButton(
                text =
                    stringResource(
                        when {
                            canContinue && remainingCount == 0 ->
                                R.string.kiyori_onboarding_permissions_result
                            canContinue -> R.string.kiyori_onboarding_permissions_continue
                            authorizationActive ->
                                R.string.kiyori_onboarding_permissions_processing
                            reviewing -> R.string.onb_p6_cta_replay
                            selectedCount == 0 -> R.string.onb_p6_cta_zero
                            else -> R.string.onb_p6_cta
                        },
                        selectedCount,
                    ),
                onClick =
                    when {
                        canContinue -> onContinueAuthorization
                        reviewing -> onFinish
                        else -> onAuthorize
                    },
                enabled = navigationEnabled && !waitingForExternalSettings && (!authorizationActive || canContinue),
                canStartTap = canStartTap,
                loading = authorizationActive && !canContinue,
            )
            TextButton(
                onClick = {
                    when {
                        authorizationActive -> onStopAuthorization()
                        reviewing -> onFinish()
                        else -> {
                            onClearSelection()
                            onFinish()
                        }
                    }
                },
                enabled = authorizationActive || navigationEnabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(KiyoriOnboardingMetrics.SecondaryButtonHeight.dp)
                        .onboardingTapOnly(canStartTap),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.onSurfaceVariant),
            ) {
                Text(
                    text =
                        stringResource(
                            when {
                                authorizationActive -> R.string.kiyori_onboarding_permissions_stop
                                reviewing -> R.string.onb_p5_secondary_replay
                                else -> R.string.onb_p6_secondary
                            },
                        ),
                    fontSize = 13.5.sp,
                )
            }
        }
    }
}

@Composable
private fun PermissionSelectionSummary(
    selectedCount: Int,
    interactionEnabled: Boolean,
    onSelectRecommended: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val palette = currentKiyoriOnboardingColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.outline),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(
                text = stringResource(R.string.onb_p6_summary, selectedCount),
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = palette.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OnboardingChoiceChip(
                    text = stringResource(R.string.onb_p6_chip_recommend),
                    emphasized = true,
                    enabled = interactionEnabled,
                    onClick = onSelectRecommended,
                )
                OnboardingChoiceChip(
                    text = stringResource(R.string.onb_p6_chip_none),
                    emphasized = false,
                    enabled = interactionEnabled && selectedCount > 0,
                    onClick = onClearSelection,
                )
            }
        }
    }
}

@Composable
private fun OnboardingChoiceChip(
    text: String,
    emphasized: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = currentKiyoriOnboardingColors()
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(11.dp),
        color = if (emphasized) palette.primaryWeak else palette.surface,
        contentColor = if (emphasized) palette.primary else palette.onSurfaceVariant,
        border = if (emphasized) null else BorderStroke(1.dp, palette.outline),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OnboardingPermissionGroupHeader(
    title: String,
    hint: String,
) {
    val palette = currentKiyoriOnboardingColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = hint,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = palette.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionPreviewRow(
    permissionId: KiyoriPermissionId,
    title: String,
    description: String,
    toneIndex: Int,
    status: KiyoriPermissionStatus,
    selected: Boolean,
    selectable: Boolean,
    interactionEnabled: Boolean,
    stateText: String? = null,
    onClick: () -> Unit,
) {
    val palette = currentKiyoriOnboardingColors()
    val tone = palette.tones[toneIndex]
    val granted = status == KiyoriPermissionStatus.GRANTED
    var rowModifier: Modifier = Modifier.fillMaxWidth()
    if (selectable) {
        rowModifier =
            rowModifier.toggleable(
                value = selected,
                enabled = interactionEnabled,
                role = Role.Switch,
                onValueChange = { onClick() },
            )
    }
    Surface(
        modifier = rowModifier.padding(top = 8.dp).semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(14.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tone.background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = kiyoriPermissionMetadata(permissionId).icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = tone.foreground,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = palette.onSurfaceVariant,
                )
            }
            if (stateText != null) {
                Text(
                    text = stateText,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurfaceVariant,
                )
            } else {
                OnboardingToggle(
                    checked = selected || granted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun OnboardingToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = currentKiyoriOnboardingColors()
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        label = "onboardingPermissionToggle",
    )
    Box(
        modifier =
            modifier
                .width(38.dp)
                .height(22.dp)
                .clip(CircleShape)
                .background(if (checked) palette.primary else palette.outline),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset, y = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White),
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
                    role = Role.Checkbox, onValueChange = { onClick() }),
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = metadata.title(context),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = kiyoriPermissionStatusLabel(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColors.icon,
                )
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
