package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val BROWSER_DOWNLOAD_SETTINGS_VERSION = 2
internal val BROWSER_DOWNLOAD_MAX_CONCURRENT_TASK_OPTIONS = (1..8).toList()
internal val BROWSER_DOWNLOAD_SEGMENT_THREAD_OPTIONS = listOf(3, 6, 12, 20, 32)
internal val BROWSER_DOWNLOAD_M3U8_THREAD_OPTIONS = listOf(3, 8, 16, 20, 32, 48, 64)
internal val BROWSER_DOWNLOAD_CHUNK_SIZE_KB_OPTIONS =
    listOf(12288, 8192, 4096, 2048, 1024, 512, 256)
internal const val DEFAULT_BROWSER_DOWNLOAD_SEGMENT_THREAD_COUNT = 6
internal const val DEFAULT_BROWSER_DOWNLOAD_M3U8_THREAD_COUNT = 16
internal const val DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB = 2048

internal enum class BrowserDownloadEngine(val persistedId: String) {
    INTERNAL("internal"),
    SYSTEM("system");

    companion object {
        fun fromPersistedId(value: String): BrowserDownloadEngine =
            requireNotNull(entries.singleOrNull { engine -> engine.persistedId == value }) {
                "Unsupported browser download engine: $value"
            }
    }
}

internal enum class BrowserDownloadNetworkPolicy(val persistedId: String) {
    ANY("any"),
    UNMETERED("unmetered");

    companion object {
        fun fromPersistedId(value: String): BrowserDownloadNetworkPolicy =
            requireNotNull(entries.singleOrNull { policy -> policy.persistedId == value }) {
                "Unsupported browser download network policy: $value"
            }
    }
}

internal sealed interface BrowserDownloadDestination {
    data object FollowSettings : BrowserDownloadDestination

    data class DocumentTree(
        val treeUri: String,
        val displayName: String,
    ) : BrowserDownloadDestination {
        init {
            require(treeUri.isNotBlank() && treeUri == treeUri.trim()) {
                "Browser download document-tree URI must be normalized"
            }
            require(displayName.isNotBlank() && displayName == displayName.trim()) {
                "Browser download document-tree name must be normalized"
            }
        }
    }
}

internal data class BrowserDownloadSettings(
    val version: Int = BROWSER_DOWNLOAD_SETTINGS_VERSION,
    val defaultEngine: BrowserDownloadEngine = BrowserDownloadEngine.INTERNAL,
    val customDirectoryUri: String = "",
    val customDirectoryName: String = "",
    val maxConcurrentTasks: Int = 3,
    val segmentThreadCount: Int = DEFAULT_BROWSER_DOWNLOAD_SEGMENT_THREAD_COUNT,
    val m3u8ThreadCount: Int = DEFAULT_BROWSER_DOWNLOAD_M3U8_THREAD_COUNT,
    val packageM3u8Offline: Boolean = true,
    val autoTransferToPublicDirectory: Boolean = false,
    val chunkSizeKb: Int = DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB,
    val autoCleanApk: Boolean = false,
    val enableHttp2: Boolean = true,
    val networkPolicy: BrowserDownloadNetworkPolicy = BrowserDownloadNetworkPolicy.ANY,
    val allowRoaming: Boolean = false,
    val skipConfirmation: Boolean = false,
    val showResultNotifications: Boolean = true,
)

internal class BrowserDownloadSettingsStore private constructor(
    private val application: Application,
) {
    private val preferences =
        application.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
    private val _state = MutableStateFlow(readSettings())

    val state: StateFlow<BrowserDownloadSettings> = _state.asStateFlow()
    val current: BrowserDownloadSettings
        get() = _state.value

    fun setDefaultEngine(value: BrowserDownloadEngine) {
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putString(KEY_DEFAULT_ENGINE, value.persistedId)
        }
        _state.value = _state.value.copy(defaultEngine = value)
    }

    fun setCustomDirectory(uri: String, displayName: String) {
        val normalizedUri = uri.trim()
        val normalizedName = displayName.trim()
        val previousUri = _state.value.customDirectoryUri
        require(normalizedUri.isNotBlank()) { "Custom browser download directory URI is blank" }
        require(normalizedName.isNotBlank()) { "Custom browser download directory name is blank" }
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putString(KEY_CUSTOM_DIRECTORY_URI, normalizedUri)
            putString(KEY_CUSTOM_DIRECTORY_NAME, normalizedName)
            putBoolean(KEY_AUTO_TRANSFER_TO_PUBLIC_DIRECTORY, false)
        }
        _state.value =
            _state.value.copy(
                customDirectoryUri = normalizedUri,
                customDirectoryName = normalizedName,
                autoTransferToPublicDirectory = false,
            )
        if (previousUri.isNotBlank() && previousUri != normalizedUri) {
            BrowserDownloadManager.getInstance(application)
                .releasePersistedDirectoryPermissionIfUnused(previousUri)
        }
    }

    fun clearCustomDirectory() {
        val previousUri = _state.value.customDirectoryUri
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            remove(KEY_CUSTOM_DIRECTORY_URI)
            remove(KEY_CUSTOM_DIRECTORY_NAME)
        }
        _state.value = _state.value.copy(customDirectoryUri = "", customDirectoryName = "")
        if (previousUri.isNotBlank()) {
            BrowserDownloadManager.getInstance(application)
                .releasePersistedDirectoryPermissionIfUnused(previousUri)
        }
    }

    fun setMaxConcurrentTasks(value: Int) {
        require(isSupportedBrowserDownloadConcurrency(value)) {
            "Unsupported browser download concurrency: $value"
        }
        require(
            value <=
                resolveBrowserDownloadMaxConcurrentTasksLimit(
                    normalThreadCount = _state.value.segmentThreadCount,
                    m3u8ThreadCount = _state.value.m3u8ThreadCount,
                ),
        ) {
            "Browser download concurrency exceeds the active thread limit: $value"
        }
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putInt(KEY_MAX_CONCURRENT_TASKS, value)
        }
        _state.value = _state.value.copy(maxConcurrentTasks = value)
    }

    fun setSegmentThreadCount(value: Int) {
        require(isSupportedBrowserDownloadSegmentThreadCount(value)) {
            "Unsupported browser download segment thread count: $value"
        }
        // Persist the matching task limit with the per-task thread count so the store remains
        // valid after restart and the transport never exceeds the shared 128-request budget.
        val nextMaxConcurrentTasks =
            minOf(
                _state.value.maxConcurrentTasks,
                resolveBrowserDownloadMaxConcurrentTasksLimit(
                    normalThreadCount = value,
                    m3u8ThreadCount = _state.value.m3u8ThreadCount,
                ),
            )
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putInt(KEY_SEGMENT_THREAD_COUNT, value)
            putInt(KEY_MAX_CONCURRENT_TASKS, nextMaxConcurrentTasks)
        }
        _state.value =
            _state.value.copy(
                segmentThreadCount = value,
                maxConcurrentTasks = nextMaxConcurrentTasks,
            )
    }

    fun setM3u8ThreadCount(value: Int) {
        require(isSupportedBrowserDownloadM3u8ThreadCount(value)) {
            "Unsupported browser download M3U8 thread count: $value"
        }
        val nextMaxConcurrentTasks =
            minOf(
                _state.value.maxConcurrentTasks,
                resolveBrowserDownloadMaxConcurrentTasksLimit(
                    normalThreadCount = _state.value.segmentThreadCount,
                    m3u8ThreadCount = value,
                ),
            )
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putInt(KEY_M3U8_THREAD_COUNT, value)
            putInt(KEY_MAX_CONCURRENT_TASKS, nextMaxConcurrentTasks)
        }
        _state.value =
            _state.value.copy(
                m3u8ThreadCount = value,
                maxConcurrentTasks = nextMaxConcurrentTasks,
            )
    }

    fun setPackageM3u8Offline(enabled: Boolean) {
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putBoolean(KEY_PACKAGE_M3U8_OFFLINE, enabled)
        }
        _state.value = _state.value.copy(packageM3u8Offline = enabled)
    }

    fun setAutoTransferToPublicDirectory(enabled: Boolean) {
        val previousUri = _state.value.customDirectoryUri
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putBoolean(KEY_AUTO_TRANSFER_TO_PUBLIC_DIRECTORY, enabled)
            if (enabled) {
                remove(KEY_CUSTOM_DIRECTORY_URI)
                remove(KEY_CUSTOM_DIRECTORY_NAME)
            }
        }
        _state.value =
            _state.value.copy(
                customDirectoryUri = if (enabled) "" else _state.value.customDirectoryUri,
                customDirectoryName = if (enabled) "" else _state.value.customDirectoryName,
                autoTransferToPublicDirectory = enabled,
            )
        if (enabled && previousUri.isNotBlank()) {
            BrowserDownloadManager.getInstance(application)
                .releasePersistedDirectoryPermissionIfUnused(previousUri)
        }
    }

    fun setChunkSizeKb(value: Int) {
        require(isSupportedBrowserDownloadChunkSizeKb(value)) {
            "Unsupported browser download chunk size: $value"
        }
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putInt(KEY_CHUNK_SIZE_KB, value)
        }
        _state.value = _state.value.copy(chunkSizeKb = value)
    }

    fun setAutoCleanApk(enabled: Boolean) {
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putBoolean(KEY_AUTO_CLEAN_APK, enabled)
        }
        _state.value = _state.value.copy(autoCleanApk = enabled)
    }

    fun setEnableHttp2(enabled: Boolean) {
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putBoolean(KEY_ENABLE_HTTP2, enabled)
        }
        _state.value = _state.value.copy(enableHttp2 = enabled)
    }

    fun setNetworkPolicy(value: BrowserDownloadNetworkPolicy) {
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putString(KEY_NETWORK_POLICY, value.persistedId)
        }
        _state.value = _state.value.copy(networkPolicy = value)
    }

    fun setAllowRoaming(enabled: Boolean) {
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putBoolean(KEY_ALLOW_ROAMING, enabled)
        }
        _state.value = _state.value.copy(allowRoaming = enabled)
    }

    fun setSkipConfirmation(enabled: Boolean) {
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putBoolean(KEY_SKIP_CONFIRMATION, enabled)
        }
        _state.value = _state.value.copy(skipConfirmation = enabled)
    }

    fun setShowResultNotifications(enabled: Boolean) {
        preferences.edit {
            putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
            putBoolean(KEY_SHOW_RESULT_NOTIFICATIONS, enabled)
        }
        _state.value = _state.value.copy(showResultNotifications = enabled)
    }

    private fun readSettings(): BrowserDownloadSettings {
        val storedVersion =
            preferences.getInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
        require(storedVersion in 1..BROWSER_DOWNLOAD_SETTINGS_VERSION) {
            "Unsupported browser download settings version: $storedVersion"
        }
        val settings =
            BrowserDownloadSettings(
                version = BROWSER_DOWNLOAD_SETTINGS_VERSION,
                defaultEngine =
                    BrowserDownloadEngine.fromPersistedId(
                        requireNotNull(
                            preferences.getString(
                                KEY_DEFAULT_ENGINE,
                                BrowserDownloadEngine.INTERNAL.persistedId,
                            ),
                        ) { "Browser download engine preference is null" },
                    ),
                customDirectoryUri =
                    requireNotNull(preferences.getString(KEY_CUSTOM_DIRECTORY_URI, "")) {
                        "Browser download custom directory URI preference is null"
                    }.trim(),
                customDirectoryName =
                    requireNotNull(preferences.getString(KEY_CUSTOM_DIRECTORY_NAME, "")) {
                        "Browser download custom directory name preference is null"
                    }.trim(),
                maxConcurrentTasks = preferences.getInt(KEY_MAX_CONCURRENT_TASKS, 3),
                segmentThreadCount =
                    preferences.getInt(
                        KEY_SEGMENT_THREAD_COUNT,
                        DEFAULT_BROWSER_DOWNLOAD_SEGMENT_THREAD_COUNT,
                    ),
                m3u8ThreadCount =
                    preferences.getInt(
                        KEY_M3U8_THREAD_COUNT,
                        DEFAULT_BROWSER_DOWNLOAD_M3U8_THREAD_COUNT,
                    ),
                packageM3u8Offline =
                    preferences.getBoolean(KEY_PACKAGE_M3U8_OFFLINE, true),
                autoTransferToPublicDirectory =
                    preferences.getBoolean(KEY_AUTO_TRANSFER_TO_PUBLIC_DIRECTORY, false),
                chunkSizeKb =
                    preferences.getInt(
                        KEY_CHUNK_SIZE_KB,
                        DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB,
                    ),
                autoCleanApk = preferences.getBoolean(KEY_AUTO_CLEAN_APK, false),
                enableHttp2 = preferences.getBoolean(KEY_ENABLE_HTTP2, true),
                networkPolicy =
                    BrowserDownloadNetworkPolicy.fromPersistedId(
                        requireNotNull(
                            preferences.getString(
                                KEY_NETWORK_POLICY,
                                BrowserDownloadNetworkPolicy.ANY.persistedId,
                            ),
                        ) { "Browser download network policy preference is null" },
                    ),
                allowRoaming = preferences.getBoolean(KEY_ALLOW_ROAMING, false),
                skipConfirmation = preferences.getBoolean(KEY_SKIP_CONFIRMATION, false),
                showResultNotifications =
                    preferences.getBoolean(KEY_SHOW_RESULT_NOTIFICATIONS, true),
            )
        if (storedVersion < BROWSER_DOWNLOAD_SETTINGS_VERSION) {
            preferences.edit {
                putInt(KEY_VERSION, BROWSER_DOWNLOAD_SETTINGS_VERSION)
                putString(KEY_NETWORK_POLICY, settings.networkPolicy.persistedId)
                putBoolean(KEY_ALLOW_ROAMING, settings.allowRoaming)
            }
        }
        require(isSupportedBrowserDownloadConcurrency(settings.maxConcurrentTasks)) {
            "Invalid persisted browser download concurrency: ${settings.maxConcurrentTasks}"
        }
        require(isSupportedBrowserDownloadSegmentThreadCount(settings.segmentThreadCount)) {
            "Invalid persisted browser download segment thread count: ${settings.segmentThreadCount}"
        }
        require(isSupportedBrowserDownloadM3u8ThreadCount(settings.m3u8ThreadCount)) {
            "Invalid persisted browser download M3U8 thread count: ${settings.m3u8ThreadCount}"
        }
        require(isSupportedBrowserDownloadChunkSizeKb(settings.chunkSizeKb)) {
            "Invalid persisted browser download chunk size: ${settings.chunkSizeKb}"
        }
        require(
            settings.maxConcurrentTasks <=
                resolveBrowserDownloadMaxConcurrentTasksLimit(
                    normalThreadCount = settings.segmentThreadCount,
                    m3u8ThreadCount = settings.m3u8ThreadCount,
                ),
        ) {
            "Invalid persisted browser download concurrency: ${settings.maxConcurrentTasks}"
        }
        require(settings.customDirectoryUri.isBlank() == settings.customDirectoryName.isBlank()) {
            "Browser download custom directory URI and name must be stored together"
        }
        require(
            !settings.autoTransferToPublicDirectory || settings.customDirectoryUri.isBlank(),
        ) {
            "Browser download public transfer and custom directory are mutually exclusive"
        }
        return settings
    }

    companion object {
        private const val PREFERENCES_NAME = "browser_download_settings"
        private const val KEY_VERSION = "version"
        private const val KEY_DEFAULT_ENGINE = "default_engine"
        private const val KEY_CUSTOM_DIRECTORY_URI = "custom_directory_uri"
        private const val KEY_CUSTOM_DIRECTORY_NAME = "custom_directory_name"
        private const val KEY_MAX_CONCURRENT_TASKS = "max_concurrent_tasks"
        private const val KEY_SEGMENT_THREAD_COUNT = "segment_thread_count"
        private const val KEY_M3U8_THREAD_COUNT = "m3u8_thread_count"
        // The stored key predates the offline-package wording; retain it to migrate existing
        // development data without keeping a second setting field.
        private const val KEY_PACKAGE_M3U8_OFFLINE = "auto_merge_m3u8"
        private const val KEY_AUTO_TRANSFER_TO_PUBLIC_DIRECTORY =
            "auto_transfer_to_public_directory"
        private const val KEY_CHUNK_SIZE_KB = "chunk_size_kb"
        private const val KEY_AUTO_CLEAN_APK = "auto_clean_apk"
        private const val KEY_ENABLE_HTTP2 = "enable_http2"
        private const val KEY_NETWORK_POLICY = "network_policy"
        private const val KEY_ALLOW_ROAMING = "allow_roaming"
        private const val KEY_SKIP_CONFIRMATION = "skip_confirmation"
        // Keep the persisted key stable so existing local development data migrates without a
        // second setting source; only the Kotlin name changes to match completion and failure.
        private const val KEY_SHOW_RESULT_NOTIFICATIONS = "show_completion_tip"

        @Volatile private var instance: BrowserDownloadSettingsStore? = null

        fun getInstance(context: Context): BrowserDownloadSettingsStore =
            instance ?: synchronized(this) {
                instance
                    ?: BrowserDownloadSettingsStore(
                        context.applicationContext as Application,
                    ).also { store ->
                        instance = store
                    }
            }
    }
}

internal fun isSupportedBrowserDownloadConcurrency(value: Int): Boolean =
    value in BROWSER_DOWNLOAD_MAX_CONCURRENT_TASK_OPTIONS

internal fun isSupportedBrowserDownloadSegmentThreadCount(value: Int): Boolean =
    value in BROWSER_DOWNLOAD_SEGMENT_THREAD_OPTIONS

internal fun isSupportedBrowserDownloadM3u8ThreadCount(value: Int): Boolean =
    value in BROWSER_DOWNLOAD_M3U8_THREAD_OPTIONS

internal fun isSupportedBrowserDownloadChunkSizeKb(value: Int): Boolean =
    value in BROWSER_DOWNLOAD_CHUNK_SIZE_KB_OPTIONS

internal fun resolveBrowserDownloadMaxConcurrentTasksLimit(
    normalThreadCount: Int,
    m3u8ThreadCount: Int,
): Int {
    require(isSupportedBrowserDownloadSegmentThreadCount(normalThreadCount)) {
        "Unsupported browser download segment thread count: $normalThreadCount"
    }
    require(isSupportedBrowserDownloadM3u8ThreadCount(m3u8ThreadCount)) {
        "Unsupported browser download M3U8 thread count: $m3u8ThreadCount"
    }
    return minOf(
        BROWSER_DOWNLOAD_MAX_CONCURRENT_TASK_OPTIONS.last(),
        128 / maxOf(normalThreadCount, m3u8ThreadCount),
    )
}

internal fun shouldReleaseBrowserDownloadDirectoryPermission(
    candidateUri: String,
    settingsDirectoryUris: Collection<String>,
    taskDirectoryUris: Collection<String>,
): Boolean =
    candidateUri.isNotBlank() &&
        candidateUri !in settingsDirectoryUris &&
        candidateUri !in taskDirectoryUris
