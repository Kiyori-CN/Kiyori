package com.ai.assistance.operit.ui.main

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.Choreographer
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingScreenWithState
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingState
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingStateRegistry
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.util.AnrMonitor
import com.ai.assistance.operit.util.AnrMonitorObservationState
import com.ai.assistance.operit.util.LocaleUtils
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import com.ai.assistance.operit.core.player.PlayerPresentation
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadManager
import com.ai.assistance.operit.ui.features.player.PlayerActivity
import com.ai.assistance.operit.ui.features.github.GitHubOAuthCoordinator
import com.kiyori.app.theme.KiyoriTheme
import com.kiyori.app.shell.KiyoriShellExternalDestination
import com.kiyori.app.startup.KiyoriMainContentHost
import com.kiyori.app.startup.KiyoriMainIntentCommand
import com.kiyori.app.startup.KiyoriMainIntentContract
import com.kiyori.app.startup.KiyoriMainDisplayCoordinator
import com.kiyori.app.startup.KiyoriMainOrientationCoordinator
import com.kiyori.app.startup.KiyoriMainOrientationDialog
import com.kiyori.app.startup.KiyoriMainPendingRequests
import com.kiyori.app.startup.KiyoriMainSharedContentCoordinator
import com.kiyori.app.startup.KiyoriMainStartupGate
import com.kiyori.app.startup.KiyoriMainStartupGateCoordinator
import com.kiyori.app.startup.KiyoriMainTaskVisibilityCoordinator
import com.kiyori.app.startup.decodeKiyoriMainIntent
import com.kiyori.platform.lifecycle.MainApplicationInitialization
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import org.json.JSONObject

private data class KiyoriMainIntentHandlingResult(
    val handledShortcutIntent: Boolean,
    val processPendingSharedContent: Boolean,
)

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_SETTINGS_SHORTCUT =
            KiyoriMainIntentContract.ACTION_OPEN_SETTINGS_SHORTCUT
        const val ACTION_OPEN_KIYORI_BROWSER =
            KiyoriMainIntentContract.ACTION_OPEN_KIYORI_BROWSER
        const val ACTION_RESTORE_KIYORI_BROWSER_FROM_INDICATOR =
            KiyoriMainIntentContract.ACTION_RESTORE_KIYORI_BROWSER_FROM_INDICATOR
        const val ACTION_OPEN_KIYORI_DOWNLOADS =
            KiyoriMainIntentContract.ACTION_OPEN_KIYORI_DOWNLOADS
        const val ACTION_OPEN_KIYORI_DOWNLOAD_TASK =
            KiyoriMainIntentContract.ACTION_OPEN_KIYORI_DOWNLOAD_TASK
        const val EXTRA_KIYORI_DOWNLOAD_TASK_ID =
            KiyoriMainIntentContract.EXTRA_KIYORI_DOWNLOAD_TASK_ID
        const val ACTION_OPEN_KIYORI_BROWSER_SETTINGS =
            KiyoriMainIntentContract.ACTION_OPEN_KIYORI_BROWSER_SETTINGS
        const val ACTION_OPEN_KIYORI_DOWNLOAD_SETTINGS =
            KiyoriMainIntentContract.ACTION_OPEN_KIYORI_DOWNLOAD_SETTINGS
        const val ACTION_RESTART_PLAYER_AFTER_CRASH =
            KiyoriMainIntentContract.ACTION_RESTART_PLAYER_AFTER_CRASH
        const val EXTRA_PLAYER_RUNTIME_GENERATION =
            KiyoriMainIntentContract.EXTRA_PLAYER_RUNTIME_GENERATION
    }

    private val TAG = "MainActivity"

    // ======== 工具和管理器 ========
    private lateinit var startupGateCoordinator: KiyoriMainStartupGateCoordinator
    private lateinit var anrMonitor: AnrMonitor

    // ======== MCP插件状态 ========
    private val pluginLoadingState = PluginLoadingState()
    private var mainApplicationReady = false
    private var pluginLoadingStarted = false

    // ======== 双击返回退出相关变量 ========
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000 // 两次点击的时间间隔，单位为毫秒

    private val orientationCoordinator = KiyoriMainOrientationCoordinator()
    private val pendingRequests = KiyoriMainPendingRequests()
    private val sharedContentCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        KiyoriMainSharedContentCoordinator(
            activity = this,
            pendingRequests = pendingRequests,
        )
    }

    override fun attachBaseContext(newBase: Context) {
        // 获取当前设置的语言
        val code = LocaleUtils.getCurrentLanguage(newBase)
        val locale = LocaleUtils.getLocaleForLanguageCode(code, newBase)
        val config = LocaleUtils.createLocaleOverrideConfiguration(locale)

        // 设置语言配置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
        } else {
            @Suppress("DEPRECATION")
            Locale.setDefault(locale)
        }

        // 使用createConfigurationContext创建新的本地化上下文
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
        AppLogger.d(TAG, "MainActivity应用语言设置: $code")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orientationCoordinator.initialize(resources.configuration.orientation)
        AppLogger.d(TAG, "onCreate: Android SDK version: ${Build.VERSION.SDK_INT}")

        // Handle the intent that started the activity
        handleIntent(intent)

        (application as MainApplicationInitialization).initializeMainUiPrerequisites()

        // 语言设置已在Application中初始化，这里无需重复
        initializeComponents()
        anrMonitor.markProcessLifecycleState(
            if (ActivityLifecycleManager.isAppInForeground()) {
                AnrMonitorObservationState.PROCESS_STATE_FOREGROUND
            } else {
                AnrMonitorObservationState.PROCESS_STATE_UNKNOWN
            }
        )
        anrMonitor.start()
        KiyoriMainDisplayCoordinator.configure(this)

        // 设置上下文以便获取插件元数据
        pluginLoadingState.setAppContext(this)
        PluginLoadingStateRegistry.bind(pluginLoadingState, lifecycleScope)

        // 设置跳过加载的回调
        pluginLoadingState.setOnSkipCallback {
            AppLogger.d(TAG, "用户跳过了插件加载过程")
            Toast.makeText(this, getString(R.string.plugin_loading_skipped), Toast.LENGTH_SHORT).show()
        }

        // 设置初始界面
        setAppContent()
        scheduleMainApplicationInitialization(
            performInitialChecks = savedInstanceState == null
        )

        // 设置双击返回退出
        setupBackPressHandler()
    }

    private fun scheduleMainApplicationInitialization(performInitialChecks: Boolean) {
        val mainApplicationInitialization = application as MainApplicationInitialization

        // 完整初始化不能占用 Android 系统 Splash 等待的首帧。先提交一帧，再从后台完成
        // 非首屏职责；否则 PDFBox、编辑器语言和磁盘扫描会延长启动图标停留时间。
        Choreographer.getInstance().postFrameCallback {
            window.decorView.post {
                if (isFinishing || isDestroyed) {
                    return@post
                }
                anrMonitor.markFirstFrameRendered()
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        mainApplicationInitialization.initializeMainApplication()
                    }
                    anrMonitor.markApplicationReady()
                    mainApplicationReady = true
                    KiyoriMainTaskVisibilityCoordinator.restoreIfNeeded(this@MainActivity)
                    processPendingGitHubAuth()
                    if (performInitialChecks) {
                        performInitialChecks()
                    }
                    startPluginLoadingIfReady()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // 重要：更新当前Intent
        AppLogger.d(TAG, "onNewIntent: Received intent with action: ${intent?.action}")
        KiyoriMainTaskVisibilityCoordinator.restoreIfNeeded(this)
        val handlingResult = handleIntent(intent)

        if (handlingResult.handledShortcutIntent) {
            processPendingGitHubAuth()
            setAppContent()
            return
        }

        if (handlingResult.processPendingSharedContent) {
            sharedContentCoordinator.processPendingSharedFiles()
            sharedContentCoordinator.processPendingSharedText()
        }
    }

    private fun handleIntent(intent: Intent?): KiyoriMainIntentHandlingResult {
        val decoding = decodeKiyoriMainIntent(intent)
        val handledShortcutIntent =
            when (val command = decoding.command) {
                KiyoriMainIntentCommand.None -> false

                is KiyoriMainIntentCommand.RestartPlayer -> {
                    val presentation =
                        PlayerSession
                            .getInstance(this)
                            .restartAfterCrash(command.runtimeGeneration)
                    intent?.action = null
                    if (presentation == PlayerPresentation.FULLSCREEN_PLAYER) {
                        startActivity(PlayerActivity.createReuseSessionIntent(this))
                    }
                    presentation != null
                }

                is KiyoriMainIntentCommand.OpenDownloadedTask -> {
                    intent?.action = null
                    if (
                        !BrowserDownloadManager
                            .getInstance(this)
                            .openDownloadedFile(command.taskId)
                    ) {
                        pendingRequests.recordShellDestination(
                            destination = KiyoriShellExternalDestination.DOWNLOADS,
                            requestId = System.currentTimeMillis(),
                        )
                        Toast.makeText(
                            this,
                            R.string.web_session_download_open_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    true
                }

                is KiyoriMainIntentCommand.OpenShellDestination -> {
                    pendingRequests.recordShellDestination(
                        destination = command.destination,
                        requestId = System.currentTimeMillis(),
                    )
                    AppLogger.d(
                        TAG,
                        "Requested opening Kiyori shell destination: ${command.destination}",
                    )
                    true
                }

                KiyoriMainIntentCommand.OpenSettingsShortcut -> {
                    pendingRequests.recordShortcut(
                        navItem = NavItem.Settings,
                        requestId = System.currentTimeMillis(),
                    )
                    AppLogger.d(TAG, "Shortcut requested opening settings")
                    true
                }

                is KiyoriMainIntentCommand.OpenRoute -> {
                    pendingRequests.recordRoute(
                        routeId = command.routeId,
                        routeArgs = parseRouteArgsJson(command.routeArgsJson),
                        requestId = System.currentTimeMillis(),
                    )
                    AppLogger.d(
                        TAG,
                        "Shortcut requested opening route: ${command.routeId}",
                    )
                    true
                }

                is KiyoriMainIntentCommand.CompleteGitHubAuth -> {
                    pendingRequests.recordGitHubAuth(command.uri)
                    AppLogger.d(TAG, "Received GitHub OAuth redirect: ${command.uri}")
                    true
                }

                is KiyoriMainIntentCommand.OpenBrowser -> {
                    pendingRequests.recordBrowser(
                        url = command.url,
                        requestId = System.currentTimeMillis(),
                    )
                    AppLogger.d(TAG, "Received browser navigation request")
                    false
                }

                is KiyoriMainIntentCommand.OpenSharedFile -> {
                    pendingRequests.recordSharedFiles(listOf(command.uri))
                    AppLogger.d(TAG, "Received file to open: ${command.uri}")
                    false
                }

                is KiyoriMainIntentCommand.ShareSingle -> {
                    command.uri?.let { uri ->
                        pendingRequests.recordSharedFiles(listOf(uri))
                        AppLogger.d(TAG, "Received shared file: $uri")
                    }
                    command.text?.let { text ->
                        pendingRequests.recordSharedText(text)
                        AppLogger.d(TAG, "Received shared text")
                    }
                    false
                }

                is KiyoriMainIntentCommand.ShareMultiple -> {
                    if (command.uris.isNotEmpty()) {
                        pendingRequests.recordSharedFiles(command.uris)
                        AppLogger.d(
                            TAG,
                            "Received shared files: ${command.uris.size}",
                        )
                    }
                    command.text?.let { text ->
                        pendingRequests.recordSharedText(text)
                        AppLogger.d(TAG, "Received shared text")
                    }
                    false
                }
            }

        return KiyoriMainIntentHandlingResult(
            handledShortcutIntent = handledShortcutIntent,
            processPendingSharedContent = decoding.processPendingSharedContent,
        )
    }

    private fun processPendingGitHubAuth() {
        val authUri = pendingRequests.takeGitHubAuthUri() ?: return

        lifecycleScope.launch {
            val coordinator = GitHubOAuthCoordinator(this@MainActivity)
            val result = coordinator.completeExternalLogin(authUri)
            result.fold(
                onSuccess = { user ->
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.main_github_login_success, user.login),
                        Toast.LENGTH_LONG
                    ).show()
                },
                onFailure = { error ->
                    val message = error.message.orEmpty()
                    if (authUri.getQueryParameter("error") == "access_denied") {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.github_login_external_cancelled),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.main_github_login_failed, message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    AppLogger.e(TAG, "Failed to complete external GitHub login", error)
                }
            )
        }
    }

    private suspend fun prepareStartupChatIfNeeded() {
        try {
            val displayPreferencesManager =
                DisplayPreferencesManager.getInstance(this@MainActivity)
            val chatHistoryManager = ChatHistoryManager.getInstance(this@MainActivity)
            val currentChatId = chatHistoryManager.readPersistedCurrentChatId()
            val currentChatExists =
                currentChatId != null && chatHistoryManager.chatExists(currentChatId)
            val startWithNewChat = displayPreferencesManager.startWithNewChat.first()
            if (
                !shouldCreateKiyoriStartupChat(
                    currentChatId = currentChatId,
                    currentChatExists = currentChatExists,
                    startWithNewChat = startWithNewChat,
                )
            ) {
                return
            }

            chatHistoryManager.createNewChat()
            AppLogger.d(
                TAG,
                if (currentChatId == null || !currentChatExists) {
                    "启动时已为 AI 首页创建首个空白聊天"
                } else {
                    "已按用户偏好在启动时创建新的空白聊天"
                },
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动时创建空白聊天失败: ${e.javaClass.simpleName}")
        }
    }

    private fun parseRouteArgsJson(raw: String?): Map<String, Any?> {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) {
            return emptyMap()
        }
        return try {
            val json = JSONObject(text)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.opt(key)
                    put(key, if (value == JSONObject.NULL) null else value)
                }
            }
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to parse pending route args json", error)
            emptyMap()
        }
    }

    // ======== 设置初始占位内容 ========

    // ======== 执行初始化检查 ========
    private suspend fun performInitialChecks() {
        prepareStartupChatIfNeeded()
    }

    private fun startPluginLoadingIfReady() {
        if (
            !mainApplicationReady ||
            !startupGateCoordinator.isReadyForContent ||
            pluginLoadingStarted
        ) {
            return
        }
        pluginLoadingStarted = true
        startPluginLoading()
    }

    // ======== 启动插件加载 ========
    private fun startPluginLoading() {
        // 显示插件加载界面
        pluginLoadingState.show()

        // 启动超时检测（30秒）
        pluginLoadingState.startTimeoutCheck(30000L, lifecycleScope)

        // 初始化MCP服务器并启动插件
        // 轻微延迟让首帧 Compose 完成，避免启动阶段后台重任务立刻抢占导致掉帧
        lifecycleScope.launch {
            delay(500)
            pluginLoadingState.initializeMCPServer(this@MainActivity, lifecycleScope)
        }
    }

    // 配置双击返回退出的处理器
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - backPressedTime > backPressedInterval) {
                            // 第一次点击，显示提示
                            backPressedTime = currentTime
                            Toast.makeText(this@MainActivity, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
                        } else {
                            // 第二次点击，退出应用
                            finish()
                        }
                    }
                }
        )
    }


    override fun onStop() {
        super.onStop()
        anrMonitor.markProcessLifecycleState(
            AnrMonitorObservationState.PROCESS_STATE_BACKGROUND
        )
        val playerSession = PlayerSession.getInstance(this)
        if (playerSession.state.value.presentation == PlayerPresentation.FLOATING_PLAYER) {
            playerSession.onHostBackgrounded()
        }
    }

    override fun onStart() {
        super.onStart()
        anrMonitor.markProcessLifecycleState(
            AnrMonitorObservationState.PROCESS_STATE_FOREGROUND
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.d(TAG, "onDestroy called")
        anrMonitor.markDestroyed()

        PluginLoadingStateRegistry.unbind(pluginLoadingState)

        // 确保隐藏加载界面
        pluginLoadingState.hide()

        // 主界面销毁时，确保关闭虚拟屏幕 Overlay 并断开 Shower WebSocket 连接
        try {
            VirtualDisplayOverlay.hideAll()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error hiding VirtualDisplayOverlay in MainActivity.onDestroy", e)
        }

        anrMonitor.stop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 屏幕方向变化时，确保加载界面不可见
        pluginLoadingState.hide()
        orientationCoordinator.onConfigurationChanged(newConfig.orientation)
    }

    // ======== 初始化组件 ========
    private fun initializeComponents() {
        anrMonitor = AnrMonitor(this, lifecycleScope)

        startupGateCoordinator = KiyoriMainStartupGateCoordinator(this)
    }

    // ======== 设置应用内容 ========
    private fun setAppContent() {
        setContent {
            KiyoriTheme {
                Box {
                    KiyoriMainStartupGate(
                        destination = startupGateCoordinator.destination,
                        agreementAccepted = startupGateCoordinator.isAgreementAccepted,
                        onAgreementAccepted = {
                            startupGateCoordinator.acceptCurrentAgreement()
                            startPluginLoadingIfReady()
                        },
                        onAgreementDeclined = {
                            finish()
                        },
                        onOnboardingComplete = {
                            startupGateCoordinator.completeOnboarding()
                            startPluginLoadingIfReady()
                        },
                    ) {
                        KiyoriMainContentHost(
                            pendingRequests = pendingRequests,
                            sharedContentCoordinator = sharedContentCoordinator,
                            pluginLoadingState = pluginLoadingState,
                        )
                    }
                    // 插件加载界面 (带有淡出效果) - 始终在最上层
                    PluginLoadingScreenWithState(
                            loadingState = pluginLoadingState,
                            modifier = Modifier.zIndex(10f) // 确保加载界面在最上层
                    )
                }

                // 方向改变时显示对话框
                if (orientationCoordinator.showChangeDialog) {
                    KiyoriMainOrientationDialog(
                        onConfirm = {
                            orientationCoordinator.dismissChangeDialog()
                            // 重新创建Activity以重新加载页面
                            recreate()
                        },
                        onDismiss = {
                            orientationCoordinator.dismissChangeDialog()
                        }
                    )
                }
            }
        }

}

}
