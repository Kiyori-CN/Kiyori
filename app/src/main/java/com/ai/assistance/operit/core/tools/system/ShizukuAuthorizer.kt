package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import rikka.shizuku.Shizuku

internal data class ShizukuConnectionInfo(val uid: Int, val binder: IBinder)

internal data class ShizukuStatusSnapshot(
    val serviceRunning: Boolean,
    val binderAlive: Boolean,
    val uid: Int?,
    val permissionGranted: Boolean,
    val permissionResult: Int?,
    val serviceError: String,
    val permissionError: String,
)

/** Shizuku授权工具类 提供Shizuku权限检查和管理功能 */
class ShizukuAuthorizer {
    companion object {
        private const val TAG = "ShizukuAuthorizer"
        private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 100
        private val mainHandler = Handler(Looper.getMainLooper())
        private val stateLock = Any()

        // 注册Shizuku权限请求监听器
        @Volatile private var binderReceivedListenerRegistered = false
        @Volatile private var permissionRequestListenerRegistered = false
        private var permissionRequestListener: Shizuku.OnRequestPermissionResultListener? = null
        private val pendingPermissionCallbacks = mutableListOf<(Boolean) -> Unit>()

        // 服务状态
        @Volatile private var isServiceAvailable = false
        private var cachedConnection: ShizukuConnectionInfo? = null
        
        // 错误消息缓存
        @Volatile private var lastServiceErrorMessage = ""
        @Volatile private var lastPermissionErrorMessage = ""

        // 状态变更回调
        private val stateChangeListeners = mutableListOf<() -> Unit>()
        private val binderReceivedListener =
            Shizuku.OnBinderReceivedListener {
                AppLogger.d(TAG, "Shizuku binder received")
                clearConnection("")
                clearExecutionCaches()
                notifyStateChanged()

                // 当收到 binder 时主动检查权限状态。
                mainHandler.post {
                    try {
                        val hasPermission = hasShizukuPermission()
                        AppLogger.d(TAG, "Checking permission after binder received: $hasPermission")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error checking permission after binder received", e)
                    }
                }
            }
        private val binderDeadListener =
            Shizuku.OnBinderDeadListener {
                AppLogger.d(TAG, "Shizuku binder dead")
                clearConnection("Shizuku binder is not alive")
                clearExecutionCaches()
                cancelPendingPermissionRequest("Shizuku binder is not alive")
                notifyStateChanged()
            }

        /**
         * 添加状态变更监听器
         * @param listener 监听器回调
         */
        fun addStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) {
                if (!stateChangeListeners.contains(listener)) {
                    stateChangeListeners.add(listener)
                }
            }
        }

        /**
         * 移除状态变更监听器
         * @param listener 要移除的监听器
         */
        fun removeStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) { stateChangeListeners.remove(listener) }
        }

        /** 触发状态变更通知 */
        private fun notifyStateChanged() {
            // 确保在主线程中执行UI相关回调
            mainHandler.post {
                val listeners = synchronized(stateChangeListeners) { stateChangeListeners.toList() }
                AppLogger.d(TAG, "Notifying ${listeners.size} listeners about state change")
                listeners.forEach { listener ->
                    try {
                        listener.invoke()
                    } catch (error: Exception) {
                        AppLogger.e(TAG, "Shizuku state listener failed", error)
                    }
                }
            }
        }

        private fun clearExecutionCaches() {
            AndroidShellExecutor.clearPreferredPermissionLevelCache()
        }

        private fun isSuiBackendAvailable(): Boolean {
            return try {
                if (Shizuku.pingBinder()) {
                    AppLogger.i(TAG, "检测到Sui/Shizuku后端可用（pingBinder）")
                    true
                } else {
                    val binder = Shizuku.getBinder()
                    val binderAlive = binder != null && binder.isBinderAlive
                    if (binderAlive) {
                        AppLogger.i(TAG, "检测到Sui/Shizuku后端可用（binder alive）")
                    }
                    binderAlive
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Sui后端检测失败: ${e.message}")
                false
            }
        }

        /**
         * 检查Shizuku是否已安装（兼容Sui后端）
         * @param context Android上下文
         * @return 是否已安装Shizuku或可用Sui后端
         */
        fun isShizukuInstalled(context: Context): Boolean {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
                val versionName = packageInfo.versionName
                AppLogger.i(TAG, "检测到已安装Shizuku，版本: $versionName")
                true
            } catch (e: PackageManager.NameNotFoundException) {
                val suiBackendAvailable = isSuiBackendAvailable()
                if (suiBackendAvailable) {
                    AppLogger.i(TAG, "未检测到Shizuku应用，但检测到Sui后端可用")
                } else {
                    AppLogger.i(TAG, "未检测到已安装的Shizuku，也未检测到可用的Sui后端")
                }
                suiBackendAvailable
            } catch (e: Exception) {
                AppLogger.e(TAG, "检查Shizuku/Sui可用性时出错", e)
                false
            }
        }

        /**
         * 获取最后一次服务检查的错误信息
         * @return 错误信息
         */
        fun getServiceErrorMessage(): String {
            return lastServiceErrorMessage
        }
        
        /**
         * 获取最后一次权限检查的错误信息
         * @return 错误信息
         */
        fun getPermissionErrorMessage(): String {
            return lastPermissionErrorMessage
        }

        private fun cacheConnection(uid: Int, binder: IBinder): ShizukuConnectionInfo {
            val connection = ShizukuConnectionInfo(uid, binder)
            synchronized(stateLock) {
                cachedConnection = connection
                isServiceAvailable = true
                lastServiceErrorMessage = ""
            }
            return connection
        }

        private fun clearConnection(errorMessage: String) {
            synchronized(stateLock) {
                cachedConnection = null
                isServiceAvailable = false
                lastServiceErrorMessage = errorMessage
            }
        }

        private fun getCachedConnection(): ShizukuConnectionInfo? {
            val connection = synchronized(stateLock) { cachedConnection } ?: return null
            if (!connection.binder.isBinderAlive) {
                clearConnection("Shizuku binder is not alive")
                return null
            }
            synchronized(stateLock) { lastServiceErrorMessage = "" }
            return connection
        }

        private fun isAllowedShizukuUid(uid: Int): Boolean {
            return uid == 0 || uid == 2000
        }

        internal fun getOrResolveShizukuConnection(): ShizukuConnectionInfo? {
            getCachedConnection()?.let { return it }

            try {
                val pingSucceeded =
                        try {
                            Shizuku.pingBinder()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Shizuku pingBinder check failed", e)
                            clearConnection("Shizuku ping failed: ${e.message}")
                            return null
                        }

                if (pingSucceeded) {
                    AppLogger.d(TAG, "Shizuku pingBinder succeeded")
                }

                val binder =
                        try {
                            Shizuku.getBinder()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Binder check failed", e)
                            clearConnection("Failed to get binder: ${e.message}")
                            return null
                        }

                if (binder == null) {
                    clearConnection("Shizuku binder is null")
                    return null
                }

                if (!binder.isBinderAlive) {
                    clearConnection("Shizuku binder is not alive")
                    return null
                }

                if (!pingSucceeded) {
                    AppLogger.d(TAG, "Shizuku binder is alive")
                }

                val uid =
                        try {
                            Shizuku.getUid()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "UID check failed", e)
                            clearConnection("Failed to get UID: ${e.message}")
                            return null
                        }

                if (!isAllowedShizukuUid(uid)) {
                    clearConnection("Invalid Shizuku UID: $uid, expected 0 or 2000")
                    return null
                }

                return cacheConnection(uid, binder)
            } catch (e: Throwable) {
                AppLogger.e(TAG, "Critical error checking Shizuku service", e)
                clearConnection("Critical error: ${e.message}")
                return null
            }
        }

        /**
         * 检查Shizuku服务是否正在运行
         * @return 服务是否运行
         */
        fun isShizukuServiceRunning(): Boolean {
            return getOrResolveShizukuConnection() != null
        }

        /** Returns one immutable service/permission snapshot for routing and diagnostics. */
        internal fun getStatusSnapshot(): ShizukuStatusSnapshot {
            val connection = getOrResolveShizukuConnection()
            if (connection == null) {
                return ShizukuStatusSnapshot(
                    serviceRunning = false,
                    binderAlive = false,
                    uid = null,
                    permissionGranted = false,
                    permissionResult = null,
                    serviceError = getServiceErrorMessage(),
                    permissionError = getPermissionErrorMessage(),
                )
            }

            val permissionResult =
                try {
                    Shizuku.checkSelfPermission()
                } catch (error: Exception) {
                    AppLogger.e(TAG, "Error reading Shizuku permission snapshot", error)
                    null
                }
            val permissionGranted = permissionResult == PackageManager.PERMISSION_GRANTED
            val permissionError =
                if (permissionGranted) {
                    ""
                } else {
                    "Shizuku permission not granted (code: ${permissionResult ?: "unknown"})"
                }
            synchronized(stateLock) {
                lastPermissionErrorMessage = permissionError
            }
            return ShizukuStatusSnapshot(
                serviceRunning = true,
                binderAlive = connection.binder.isBinderAlive,
                uid = connection.uid,
                permissionGranted = permissionGranted,
                permissionResult = permissionResult,
                serviceError = getServiceErrorMessage(),
                permissionError = permissionError,
            )
        }

        /**
         * 检查应用是否有Shizuku权限
         * @return 是否有权限
         */
        fun hasShizukuPermission(): Boolean {
            try {
                if (getOrResolveShizukuConnection() == null) {
                    synchronized(stateLock) {
                        lastPermissionErrorMessage =
                            "Shizuku service not running: $lastServiceErrorMessage"
                    }
                    return false
                }

                // 适用于Shizuku 13.x版本的权限检查
                val result = Shizuku.checkSelfPermission()
                val granted = result == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    synchronized(stateLock) { lastPermissionErrorMessage = "" }
                    return true
                } else {
                    synchronized(stateLock) {
                        lastPermissionErrorMessage =
                            "Shizuku permission not granted (code: $result)"
                    }
                    return false
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error checking Shizuku permission", e)
                synchronized(stateLock) {
                    lastPermissionErrorMessage = "Error checking permission: ${e.message}"
                }
                return false
            }
        }

        /**
         * 请求Shizuku权限
         * @param onResult 权限请求结果回调，仅返回是否授予权限
         */
        fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
            val serviceRunning = isShizukuServiceRunning()
            if (!serviceRunning) {
                AppLogger.e(TAG, "Cannot request permission: $lastServiceErrorMessage")
                deliverPermissionResult(listOf(onResult), granted = false)
                return
            }

            val hasPermission = hasShizukuPermission()
            if (hasPermission) {
                AppLogger.d(TAG, "Permission already granted")
                clearExecutionCaches()
                deliverPermissionResult(listOf(onResult), granted = true)
                notifyStateChanged()
                return
            }

            AppLogger.d(TAG, "Requesting Shizuku permission")

            // Install the listener while holding the same lock used for the pending callbacks.
            // Binder-dead can arrive on another thread between enqueue and registration; doing
            // these operations separately leaves a listener with no callbacks and the next
            // request permanently joins a request that can never complete.
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener =
                Shizuku.OnRequestPermissionResultListener { code, grantResult ->
                    if (code != SHIZUKU_PERMISSION_REQUEST_CODE) {
                        return@OnRequestPermissionResultListener
                    }

                    AppLogger.d(
                        TAG,
                        "Permission result received: code=$code, result=$grantResult",
                    )
                    val (registeredListener, callbacks) =
                        takePermissionRequestState(expectedListener = listener)
                    if (registeredListener !== listener) {
                        AppLogger.w(TAG, "Ignoring a stale Shizuku permission result listener")
                        return@OnRequestPermissionResultListener
                    }
                    try {
                        Shizuku.removeRequestPermissionResultListener(registeredListener)
                    } catch (error: Exception) {
                        AppLogger.e(TAG, "Error removing permission listener", error)
                    }
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    AppLogger.d(TAG, "Shizuku permission request result: $granted")
                    // Permission state and executor caches must change at the same event edge,
                    // including an explicit denial after a previous grant was revoked.
                    clearExecutionCaches()
                    deliverPermissionResult(callbacks, granted)
                    notifyStateChanged()
                }

            var registrationError: Exception? = null
            var callbacksToFail: List<(Boolean) -> Unit> = emptyList()
            synchronized(stateLock) {
                pendingPermissionCallbacks += onResult
                if (permissionRequestListenerRegistered) {
                    AppLogger.d(TAG, "Shizuku permission request already pending; joining it")
                } else {
                    permissionRequestListener = listener
                    permissionRequestListenerRegistered = true
                    try {
                        AppLogger.d(TAG, "Setting up permission result listener")
                        Shizuku.addRequestPermissionResultListener(listener)
                    } catch (error: Exception) {
                        registrationError = error
                        permissionRequestListener = null
                        permissionRequestListenerRegistered = false
                        callbacksToFail = pendingPermissionCallbacks.toList()
                        pendingPermissionCallbacks.clear()
                    }
                }
            }

            registrationError?.let { error ->
                AppLogger.e(TAG, "Error registering Shizuku permission listener", error)
                clearExecutionCaches()
                deliverPermissionResult(callbacksToFail, granted = false)
                notifyStateChanged()
                return
            }

            val requestIsStillActive =
                synchronized(stateLock) {
                    permissionRequestListenerRegistered && permissionRequestListener === listener
                }
            if (!requestIsStillActive) {
                // Binder-dead already consumed the request and notified every waiter.
                return
            }

            try {
                AppLogger.d(
                    TAG,
                    "Calling Shizuku.requestPermission($SHIZUKU_PERMISSION_REQUEST_CODE)",
                )
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            } catch (error: Exception) {
                AppLogger.e(TAG, "Error requesting Shizuku permission", error)
                val (registeredListener, callbacks) =
                    takePermissionRequestState(expectedListener = listener)
                if (registeredListener !== listener) {
                    // Binder-dead or a completed callback already consumed this request. A stale
                    // failure must not remove callbacks that belong to a later permission request.
                    return
                }
                try {
                    Shizuku.removeRequestPermissionResultListener(registeredListener)
                } catch (cleanupError: Exception) {
                    AppLogger.e(TAG, "Error cleaning failed permission request listener", cleanupError)
                }
                clearExecutionCaches()
                deliverPermissionResult(callbacks, granted = false)
                notifyStateChanged()
            }
        }

        private fun takePermissionRequestState(
            expectedListener: Shizuku.OnRequestPermissionResultListener? = null,
        ):
            Pair<Shizuku.OnRequestPermissionResultListener?, List<(Boolean) -> Unit>> =
            synchronized(stateLock) {
                val listener = permissionRequestListener
                if (expectedListener != null && listener !== expectedListener) {
                    return@synchronized null to emptyList()
                }
                permissionRequestListener = null
                permissionRequestListenerRegistered = false
                val callbacks = pendingPermissionCallbacks.toList()
                pendingPermissionCallbacks.clear()
                listener to callbacks
            }

        private fun deliverPermissionResult(
            callbacks: List<(Boolean) -> Unit>,
            granted: Boolean,
        ) {
            callbacks.forEach { callback ->
                try {
                    callback(granted)
                } catch (error: Exception) {
                    AppLogger.e(TAG, "Shizuku permission callback failed", error)
                }
            }
        }

        private fun cancelPendingPermissionRequest(reason: String) {
            val (registeredListener, callbacks) = takePermissionRequestState()
            try {
                registeredListener?.let(Shizuku::removeRequestPermissionResultListener)
            } catch (error: Exception) {
                AppLogger.e(TAG, "Error removing cancelled permission listener", error)
            }
            if (callbacks.isEmpty()) {
                return
            }
            AppLogger.w(TAG, "Cancelling pending Shizuku permission request: $reason")
            deliverPermissionResult(callbacks, granted = false)
        }

        /** 初始化Shizuku绑定 */
        fun initialize() {
            synchronized(stateLock) {
                if (binderReceivedListenerRegistered) {
                    AppLogger.d(TAG, "Shizuku listeners already initialized")
                    return
                }
                try {
                    Shizuku.addBinderReceivedListener(binderReceivedListener)
                    Shizuku.addBinderDeadListener(binderDeadListener)
                    binderReceivedListenerRegistered = true
                } catch (error: Exception) {
                    AppLogger.e(TAG, "Error initializing Shizuku listeners", error)
                    return
                }
            }

            AppLogger.d(TAG, "Shizuku listeners initialized")
            val isRunning = isShizukuServiceRunning()
            AppLogger.d(TAG, "Initial Shizuku service status check: $isRunning")
            if (isRunning) {
                mainHandler.post {
                    try {
                        val snapshot = getStatusSnapshot()
                        AppLogger.d(
                            TAG,
                            "Initial Shizuku status: service=${snapshot.serviceRunning}, " +
                                "binder=${snapshot.binderAlive}, uid=${snapshot.uid}, " +
                                "permission=${snapshot.permissionGranted}",
                        )
                        notifyStateChanged()
                    } catch (error: Exception) {
                        AppLogger.e(TAG, "Error during initial permission check", error)
                    }
                }
            }
        }

        /**
         * 获取Shizuku启动说明
         * @param context Android上下文
         * @return Shizuku启动指南
         */
        fun getShizukuStartupInstructions(context: Context): String {
            return context.getString(R.string.shizuku_start_service_intro) +
                    context.getString(R.string.shizuku_step1_ensure_installed) +
                    context.getString(R.string.shizuku_step2_adb_command) +
                    "   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh\n" +
                    context.getString(R.string.shizuku_or) +
                    context.getString(R.string.shizuku_step2_follow_instructions)
        }
    }
}
