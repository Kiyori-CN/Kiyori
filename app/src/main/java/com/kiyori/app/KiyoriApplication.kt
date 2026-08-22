package com.kiyori.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.system.Os
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.Configuration as WorkConfiguration
import androidx.work.WorkManager
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.api.chat.library.MemoryAutoSaveScheduler
import com.ai.assistance.operit.plugins.PluginRegistry
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleEvent
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookParams
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPluginRegistry
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.core.workflow.WorkflowSchedulerInitializer
import com.ai.assistance.operit.data.backup.RoomDatabaseBackupPreferences
import com.ai.assistance.operit.data.backup.RoomDatabaseBackupScheduler
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.data.preferences.initAndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.initUserPreferencesManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageFactory
import com.ai.assistance.operit.util.GlobalExceptionHandler
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.operit.util.SkillRepoZipPoolManager
import com.ai.assistance.operit.util.SerializationSetup
import com.ai.assistance.operit.util.TextSegmenter
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.util.crash.CrashProcessIdentity
import com.ai.assistance.operit.util.crash.CrashReportStore
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.ai.assistance.operit.core.tools.system.shower.OperitShowerShellRunner
import com.ai.assistance.showerclient.ShowerEnvironment
import com.ai.assistance.showerclient.ShowerLogSink
import com.kiyori.platform.android.ApplicationContextAccess
import com.kiyori.platform.lifecycle.ApplicationStartupTime
import com.kiyori.platform.lifecycle.MainApplicationInitialization
import com.kiyori.platform.logging.KiyoriLogger
import com.kiyori.platform.network.KiyoriNetworkModule
import com.kiyori.platform.network.KiyoriNetworkProxyManager
import com.kiyori.platform.network.applyKiyoriNetworkProxy
import com.kiyori.platform.serialization.ApplicationJson
import com.kiyori.platform.storage.KiyoriPaths
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal const val KIYORI_WORK_MANAGER_JOB_ID_MIN = 0x5000
internal const val KIYORI_WORK_MANAGER_JOB_ID_MAX = 0x53E8

/**
 * WorkManager validates max - min >= 1,000 rather than the inclusive element count.
 */
internal fun WorkConfiguration.Builder.applyKiyoriJobSchedulerIdRange():
    WorkConfiguration.Builder =
    setJobSchedulerJobIdRange(
        KIYORI_WORK_MANAGER_JOB_ID_MIN,
        KIYORI_WORK_MANAGER_JOB_ID_MAX,
    )

/** Application class for Kiyori */
class KiyoriApplication :
    Application(),
    ImageLoaderFactory,
    WorkConfiguration.Provider,
    MainApplicationInitialization {

    companion object {
        private const val TAG = "KiyoriApplication"
    }

    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var memoryAutoSaveScheduler: MemoryAutoSaveScheduler? = null
    private val mainInitializationLock = Any()
    private var mainUiPrerequisitesInitialized = false
    @Volatile
    private var mainApplicationInitialized = false

    private val imageLoader: ImageLoader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createImageLoader()
    }

    // 懒加载数据库实例
    private val database by lazy { AppDatabase.getDatabase(this) }

    private fun configureOpenMpEnvironment() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Os.setenv("KMP_AFFINITY", "disabled", true)
                Os.setenv("OMP_PROC_BIND", "false", true)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        val startTime = System.currentTimeMillis()
        ApplicationStartupTime.recordForProcess(startTime)
        ApplicationContextAccess.installForProcess(this)
        val networkProxyManager = KiyoriNetworkProxyManager.getInstance(this)
        networkProxyManager.scheduleStartupReconciliation()
        KiyoriLogger.bindContext(this, KiyoriPaths::kiyoriRootDir)

        configureOpenMpEnvironment()
        if (!CrashProcessIdentity.isCrashProcess(this)) {
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this))
        }

        ApplicationJson.installForProcess(
            Json {
                serializersModule = SerializationSetup.module
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                encodeDefaults = true
            }
        )

        if (CrashProcessIdentity.currentProcessName(this) == packageName) {
            applicationScope.launch {
                try {
                    networkProxyManager.reconcileEnabledState()
                } catch (error: Exception) {
                    KiyoriLogger.e(TAG, "应用级网络代理启动协调失败", error)
                }
            }
        }

    }

    /** Initializes only state that the first Compose frame can access synchronously. */
    override fun initializeMainUiPrerequisites() {
        synchronized(mainInitializationLock) {
            initializeMainUiPrerequisitesLocked()
        }
    }

    override fun initializeMainApplication() {
        synchronized(mainInitializationLock) {
            if (mainApplicationInitialized) {
                return
            }
            initializeMainUiPrerequisitesLocked()
            initializeMainApplicationLocked()
            mainApplicationInitialized = true
        }
    }

    private fun initializeMainUiPrerequisitesLocked() {
        if (mainUiPrerequisitesInitialized) {
            return
        }

        val startTime = System.currentTimeMillis()

        configureOpenMpEnvironment()

        // 每次应用冷启动时重置上一轮日志，避免日志无限增长
        val preserveLogsForCrashReport =
            try {
                CrashReportStore.hasUnresolvedReports(this)
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to inspect crash report state; preserving current app log", error)
                true
            }
        if (!preserveLogsForCrashReport) {
            KiyoriLogger.resetLogFile()
        }

        if (preserveLogsForCrashReport) {
            KiyoriLogger.w(TAG, "检测到未处理的崩溃报告，保留上一轮日志供崩溃页导出")
        }

        KiyoriLogger.d(TAG, "【启动计时】首屏必需初始化开始")
        KiyoriLogger.d(TAG, "【启动计时】实例初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        val defaultProfileName = applicationContext.getString(R.string.default_profile)
        initUserPreferencesManager(applicationContext, applicationScope, defaultProfileName)
        KiyoriLogger.d(TAG, "【启动计时】用户偏好管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        initAndroidPermissionPreferences(applicationContext)
        KiyoriLogger.d(TAG, "【启动计时】Android权限偏好管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 在最早时机初始化并应用语言设置（必须在获取字符串资源之前）
        initializeAppLanguage()
        KiyoriLogger.d(TAG, "【启动计时】语言设置初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize ActivityLifecycleManager to track the current activity
        ActivityLifecycleManager.initialize(this)
        KiyoriLogger.d(TAG, "【启动计时】ActivityLifecycleManager初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize AIMessageManager
        AIMessageManager.initialize(this)
        PluginRegistry.initializeBuiltins()
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.APPLICATION_CREATE,
            params =
                AppLifecycleHookParams(
                    context = applicationContext,
                    extras =
                        mapOf(
                            "startupTimeMs" to startTime
                        )
                )
        )
        KiyoriLogger.d(TAG, "【启动计时】AIMessageManager初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化AndroidShellExecutor上下文
        AndroidShellExecutor.setContext(applicationContext)
        KiyoriLogger.d(TAG, "【启动计时】AndroidShellExecutor初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        configureShowerEnvironment()
        KiyoriLogger.d(TAG, "【启动计时】Shower 运行时引用配置完成 - ${System.currentTimeMillis() - startTime}ms")

        // 首帧中的 AI 输入组件可能立即访问该处理器，因此仅建立轻量单例引用。
        WaifuMessageProcessor.initialize(applicationContext)
        KiyoriLogger.d(TAG, "【启动计时】WaifuMessageProcessor初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        mainUiPrerequisitesInitialized = true
        KiyoriLogger.d(TAG, "【启动计时】首屏必需初始化完成 - 总耗时: ${System.currentTimeMillis() - startTime}ms")
    }

    private fun configureShowerEnvironment() {
        ShowerEnvironment.shellRunner = OperitShowerShellRunner
        ShowerEnvironment.stagingDirectoryProvider = KiyoriPaths::kiyoriRootDir
        ShowerEnvironment.logSink =
            ShowerLogSink { priority, tag, message, throwable ->
                when (priority) {
                    KiyoriLogger.VERBOSE ->
                        if (throwable != null) KiyoriLogger.v(tag, message, throwable) else KiyoriLogger.v(tag, message)
                    KiyoriLogger.DEBUG ->
                        if (throwable != null) KiyoriLogger.d(tag, message, throwable) else KiyoriLogger.d(tag, message)
                    KiyoriLogger.INFO ->
                        if (throwable != null) KiyoriLogger.i(tag, message, throwable) else KiyoriLogger.i(tag, message)
                    KiyoriLogger.WARN ->
                        if (throwable != null) KiyoriLogger.w(tag, message, throwable) else KiyoriLogger.w(tag, message)
                    KiyoriLogger.ERROR ->
                        if (throwable != null) KiyoriLogger.e(tag, message, throwable) else KiyoriLogger.e(tag, message)
                    KiyoriLogger.ASSERT ->
                        if (throwable != null) KiyoriLogger.wtf(tag, message, throwable) else KiyoriLogger.wtf(tag, message)
                    else ->
                        if (throwable != null) {
                            KiyoriLogger.println(priority, tag, "$message\n${KiyoriLogger.getStackTraceString(throwable)}")
                        } else {
                            KiyoriLogger.println(priority, tag, message)
                        }
                }
            }
        // Shower logs are already mirrored to KiyoriLogger; avoid duplicate system log entries.
        ShowerEnvironment.emitToSystemLog = false
    }

    private fun initializeMainApplicationLocked() {
        val startTime = System.currentTimeMillis()
        KiyoriLogger.d(TAG, "【启动计时】首帧后完整初始化开始")

        ensureWorkManagerInitialized()
        KiyoriLogger.d(TAG, "【启动计时】WorkManager初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        launchCleanOnExitCleanup()
        KiyoriLogger.d(TAG, "【启动计时】cleanOnExit 清理任务已提交（异步IO） - ${System.currentTimeMillis() - startTime}ms")

        startGlobalAIForegroundServiceIfNeeded()
        KiyoriLogger.d(TAG, "【启动计时】AIForegroundService 持久后台职责检查完成 - ${System.currentTimeMillis() - startTime}ms")

        memoryAutoSaveScheduler = MemoryAutoSaveScheduler(applicationContext, applicationScope)
            .also { it.start() }
        KiyoriLogger.d(TAG, "【启动计时】长期记忆自动保存轮询器启动完成 - ${System.currentTimeMillis() - startTime}ms")

        applicationScope.launch {
            val characterStartTime = System.currentTimeMillis()
            CharacterCardManager.getInstance(applicationContext).initializeIfNeeded()
            KiyoriLogger.d(TAG, "【启动计时】功能提示词管理器初始化完成（异步） - ${System.currentTimeMillis() - characterStartTime}ms")
        }

        applicationScope.launch {
            val emojiStartTime = System.currentTimeMillis()
            CustomEmojiRepository.getInstance(applicationContext).initializeBuiltinEmojis()
            KiyoriLogger.d(TAG, "【启动计时】当前角色自定义表情初始化完成（异步） - ${System.currentTimeMillis() - emojiStartTime}ms")
        }

        // 初始化PDFBox资源加载器
        PDFBoxResourceLoader.init(applicationContext)
        KiyoriLogger.d(TAG, "【启动计时】PDFBox资源加载器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化语言支持
        LanguageFactory.init()
        KiyoriLogger.d(TAG, "【启动计时】语言工厂初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 完整初始化由 MainActivity 放到首帧后执行，预热继续留在后台线程。
        applicationScope.launch {
            val segmenterStartTime = System.currentTimeMillis()
            TextSegmenter.initialize(applicationContext)
            KiyoriLogger.d(TAG, "【启动计时】TextSegmenter预热完成（异步） - ${System.currentTimeMillis() - segmenterStartTime}ms")
        }

        // 预加载数据库
        applicationScope.launch {
            val dbStartTime = System.currentTimeMillis()
            // 简单访问数据库以触发初始化
            database.openHelper.writableDatabase
            KiyoriLogger.d(TAG, "【启动计时】数据库预加载完成（异步） - ${System.currentTimeMillis() - dbStartTime}ms")
        }

        // 初始化图片池管理器，支持本地持久化缓存
        ImagePoolManager.initialize(filesDir, preloadNow = false)
        KiyoriLogger.d(TAG, "【启动计时】图片池管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化媒体池管理器（音频/视频），支持本地持久化缓存
        MediaPoolManager.initialize(filesDir, preloadNow = false)
        KiyoriLogger.d(TAG, "【启动计时】媒体池管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        SkillRepoZipPoolManager.initialize(filesDir)

        // 启动后重任务统一后台串行执行，避免多个大任务同时跑导致首屏掉帧
        applicationScope.launch(Dispatchers.Default) {
            // 给 UI 一个缓冲时间先完成首屏渲染
            kotlinx.coroutines.delay(800)

            val imageLoaderStartTime = System.currentTimeMillis()
            withContext(Dispatchers.IO) { imageLoader }
            KiyoriLogger.d(
                TAG,
                "【启动计时】全局图片加载器初始化完成（异步/串行） - ${System.currentTimeMillis() - imageLoaderStartTime}ms",
            )

            val imagePreloadStartTime = System.currentTimeMillis()
            withContext(Dispatchers.IO) { ImagePoolManager.preloadFromDisk() }
            KiyoriLogger.d(TAG, "【启动计时】图片池磁盘预加载完成（异步/串行） - ${System.currentTimeMillis() - imagePreloadStartTime}ms")

            val mediaPreloadStartTime = System.currentTimeMillis()
            withContext(Dispatchers.IO) { MediaPoolManager.preloadFromDisk() }
            KiyoriLogger.d(TAG, "【启动计时】媒体池磁盘预加载完成（异步/串行） - ${System.currentTimeMillis() - mediaPreloadStartTime}ms")

            val toolStartTime = System.currentTimeMillis()
            val toolHandler = AIToolHandler.getInstance(this@KiyoriApplication)
            toolHandler.registerDefaultTools()
            KiyoriLogger.d(TAG, "【启动计时】AIToolHandler初始化并注册工具完成（异步/串行） - ${System.currentTimeMillis() - toolStartTime}ms")
        }
        
        // 初始化工作流调度器（异步）
        applicationScope.launch {
            val schedulerStartTime = System.currentTimeMillis()
            WorkflowSchedulerInitializer.initialize(applicationContext)
            KiyoriLogger.d(TAG, "【启动计时】WorkflowScheduler初始化完成（异步） - ${System.currentTimeMillis() - schedulerStartTime}ms")
        }

        applicationScope.launch {
            try {
                val prefs = RoomDatabaseBackupPreferences.getInstance(applicationContext)
                if (prefs.isDailyBackupEnabled()) {
                    RoomDatabaseBackupScheduler.ensureScheduled(applicationContext)
                } else {
                    RoomDatabaseBackupScheduler.cancelScheduled(applicationContext)
                }
            } catch (e: Exception) {
                KiyoriLogger.e(TAG, "Room DB backup schedule init failed", e)
            }
        }

        // 在应用启动时尝试绑定无障碍服务提供者（解决后台绑定限制问题）
        applicationScope.launch {
            KiyoriLogger.d(TAG, "【启动计时】开始预绑定无障碍服务提供者...")
            val bindStartTime = System.currentTimeMillis()
            try {
                val bound = com.ai.assistance.operit.data.repository.UIHierarchyManager.bindToService(this@KiyoriApplication)
                KiyoriLogger.d(TAG, "【启动计时】无障碍服务预绑定完成（异步） - 结果: $bound, 耗时: ${System.currentTimeMillis() - bindStartTime}ms")
            } catch (e: Exception) {
                KiyoriLogger.e(TAG, "无障碍服务预绑定失败", e)
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        KiyoriLogger.d(TAG, "【启动计时】首帧后完整初始化已提交 - 总耗时: ${totalTime}ms")
    }

    private fun createImageLoader(): ImageLoader {
        val imageOkHttpClient = OkHttpClient.Builder()
            .applyKiyoriNetworkProxy(KiyoriNetworkModule.APP_SERVICES)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .components {
                // 所有 Coil 消费者共用这一进程级 loader；缺少 SVG decoder 会让网络日志缩略图、
                // 全屏查看和二维码识别同时失败，因此必须在唯一组件表中一次注册。
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .respectCacheHeaders(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(filesDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024)
                    .build()
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this).maxSizePercent(0.15).build()
            }
            .build()
    }

    /**
     * 实现 ImageLoaderFactory 接口
     * 让 Coil 使用我们配置的全局 ImageLoader（带有自定义超时设置）
     */
    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }

    /**
     * 实现 WorkConfiguration.Provider 接口
     * 提供 WorkManager 的配置，确保 WorkManager 被正确初始化
     */
    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) KiyoriLogger.DEBUG else KiyoriLogger.INFO)
            // BrowserDownloadRuntime owns JobScheduler ID 0x4B10. Reserve a separate block of
            // 1,001 inclusive IDs: WorkManager validates max - min >= 1,000.
            .applyKiyoriJobSchedulerIdRange()
            .build()

    private fun ensureWorkManagerInitialized() {
        try {
            WorkManager.getInstance(applicationContext)
        } catch (_: IllegalStateException) {
            try {
                WorkManager.initialize(applicationContext, workManagerConfiguration)
            } catch (_: IllegalStateException) {

            }
        }
    }

    private fun launchCleanOnExitCleanup() {
        applicationScope.launch {
            val cleanupStartTime = System.currentTimeMillis()
            try {
                val deletedFiles =
                    cleanDirectory(File(KiyoriPaths.cleanOnExitPathSdcard()), preserveRootNoMedia = true) +
                        cleanDirectory(
                            KiyoriPaths.cleanOnExitInternalDir(applicationContext),
                            preserveRootNoMedia = false
                        )
                KiyoriLogger.d(
                    TAG,
                    "cleanOnExit 清理完成，总计删除${deletedFiles}个文件，耗时${System.currentTimeMillis() - cleanupStartTime}ms"
                )
            } catch (e: Exception) {
                KiyoriLogger.e(TAG, "清理临时文件失败", e)
            }
        }
    }

    private fun cleanDirectory(tempDir: File, preserveRootNoMedia: Boolean): Int {
        if (!tempDir.exists() || !tempDir.isDirectory) {
            return 0
        }
        if (preserveRootNoMedia) {
            val noMediaFile = File(tempDir, ".nomedia")
            if (!noMediaFile.exists()) {
                noMediaFile.createNewFile()
            }
        }
        KiyoriLogger.d(TAG, "开始清理临时文件目录: ${tempDir.absolutePath}")
        val totalDeleted =
            deleteRecursively(
                rootDir = tempDir,
                file = tempDir,
                preserveRootNoMedia = preserveRootNoMedia,
                isRoot = true
            )
        KiyoriLogger.d(TAG, "已删除${totalDeleted}个临时文件: ${tempDir.absolutePath}")
        return totalDeleted
    }

    private fun deleteRecursively(
        rootDir: File,
        file: File,
        preserveRootNoMedia: Boolean,
        isRoot: Boolean = false
    ): Int {
        var deletedCount = 0
        if (file.isDirectory) {
            val children = file.listFiles()
            children?.forEach { child ->
                deletedCount += deleteRecursively(
                    rootDir = rootDir,
                    file = child,
                    preserveRootNoMedia = preserveRootNoMedia,
                    isRoot = false
                )
            }
            if (!isRoot && file.exists()) {
                file.delete()
            }
        } else if (file.isFile) {
            val isRootNoMedia =
                preserveRootNoMedia &&
                    file.parentFile?.absolutePath == rootDir.absolutePath &&
                    file.name == ".nomedia"
            if (!isRootNoMedia && file.delete()) {
                deletedCount++
            }
        }
        return deletedCount
    }

    private fun startGlobalAIForegroundServiceIfNeeded() {
        try {
            val alwaysListeningEnabled = runBlocking {
                WakeWordPreferences(applicationContext).alwaysListeningEnabledFlow.first()
            }
            val externalHttpEnabled = runBlocking {
                ExternalHttpApiPreferences.getInstance(applicationContext).enabledFlow.first()
            }
            if ((!alwaysListeningEnabled && !externalHttpEnabled) || AIForegroundService.isRunning.get()) {
                return
            }
            val intent = Intent(this, AIForegroundService::class.java).apply {
                putExtra(AIForegroundService.EXTRA_STATE, AIForegroundService.STATE_IDLE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            KiyoriLogger.e(TAG, "按持久后台职责状态启动 AIForegroundService 失败: ${e.message}", e)
        }
    }

    /** 初始化应用语言设置 */
    // Android 8-12 的 Application 资源刷新仍依赖 updateConfiguration；
    // attachBaseContext 随后用 createConfigurationContext 建立完整配置。
    @Suppress("DEPRECATION")
    private fun initializeAppLanguage() {
        try {
            // 同步获取已保存的语言设置
            val languageCode = runBlocking {
                try {
                    // 使用更安全的方式检查preferencesManager
                    val manager = runCatching { preferencesManager }.getOrNull()
                    if (manager != null) {
                        manager.appLanguage.first()
                    } else {
                        UserPreferencesManager.DEFAULT_LANGUAGE
                    }
                } catch (e: Exception) {
                    KiyoriLogger.e(TAG, "获取语言设置失败", e)
                    UserPreferencesManager.DEFAULT_LANGUAGE
                }
            }

            KiyoriLogger.d(TAG, "获取语言设置: $languageCode")

            // 立即应用语言设置
            val locale = LocaleUtils.getLocaleForLanguageCode(languageCode, this)
            // 设置默认语言
            Locale.setDefault(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用AppCompatDelegate API
                val localeList = LocaleListCompat.create(locale)
                AppCompatDelegate.setApplicationLocales(localeList)
                KiyoriLogger.d(TAG, "使用AppCompatDelegate设置语言: $languageCode")
            } else {
                // 较旧版本Android - 此处使用的部分更新将在attachBaseContext中完成更完整更新
                val config = Configuration()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = LocaleList(locale)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                } else {
                    config.locale = locale
                }

                resources.updateConfiguration(config, resources.displayMetrics)
                KiyoriLogger.d(TAG, "使用Configuration设置语言: $languageCode")
            }
        } catch (e: Exception) {
            KiyoriLogger.e(TAG, "初始化语言设置失败", e)
        }
    }

    override fun attachBaseContext(base: Context) {
        configureOpenMpEnvironment()
        // 在基础上下文附加前应用语言设置
        try {
            val code = LocaleUtils.getCurrentLanguage(base)
            val locale = LocaleUtils.getLocaleForLanguageCode(code, base)
            val config = Configuration(base.resources.configuration)

            // 设置语言配置
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)

            // 使用createConfigurationContext创建新的上下文
            val context = base.createConfigurationContext(config)
            super.attachBaseContext(context)
            KiyoriLogger.d(TAG, "成功应用基础上下文语言: $code")
        } catch (e: Exception) {
            KiyoriLogger.e(TAG, "应用基础上下文语言失败", e)
            super.attachBaseContext(base)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        if (!mainApplicationInitialized) {
            return
        }

        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.APPLICATION_TERMINATE,
            params = AppLifecycleHookParams(applicationContext)
        )
        
        try {
            if (AIForegroundService.isRunning.get()) {
                val intent = Intent(applicationContext, AIForegroundService::class.java)
                stopService(intent)
                KiyoriLogger.d(TAG, "应用终止，已停止 AIForegroundService")
            }
        } catch (e: Exception) {
            KiyoriLogger.e(TAG, "终止时停止 AIForegroundService 失败: ${e.message}", e)
        }

        // 清理终端管理器和SSH连接
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Terminal.getInstance(applicationContext).destroy()
                KiyoriLogger.d(TAG, "应用终止，已清理所有终端会话和SSH连接")
            }
        } catch (e: Exception) {
            KiyoriLogger.e(TAG, "清理终端管理器失败: ${e.message}", e)
        }
        
        // 在应用终止时关闭LocalWebServer服务器
        try {
            val webServer = LocalWebServer.getInstance(applicationContext, LocalWebServer.ServerType.WORKSPACE)
            if (webServer.isRunning()) {
                webServer.stop()
                KiyoriLogger.d(TAG, "应用终止，已关闭本地Web服务器")
            }
        } catch (e: Exception) {
            KiyoriLogger.e(TAG, "关闭本地Web服务器失败: ${e.message}", e)
        }

        // 在应用终止时，关闭虚拟屏幕 Overlay 并断开 Shower WebSocket 连接
        try {
            VirtualDisplayOverlay.hideAll()
        } catch (e: Exception) {
            KiyoriLogger.e(TAG, "终止时隐藏 VirtualDisplayOverlay 失败: ${e.message}", e)
        }
        try {
            ShowerController.shutdown()
        } catch (e: Exception) {
            KiyoriLogger.e(TAG, "终止时关闭 ShowerController 失败: ${e.message}", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (!mainApplicationInitialized) {
            return
        }
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.APPLICATION_LOW_MEMORY,
            params = AppLifecycleHookParams(applicationContext)
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!mainApplicationInitialized) {
            return
        }
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.APPLICATION_TRIM_MEMORY,
            params =
                AppLifecycleHookParams(
                    context = applicationContext,
                    extras =
                        mapOf(
                            "level" to level
                        )
                )
        )
    }
}
