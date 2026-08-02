package com.kiyori.app.startup

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.ui.common.NavItem
import com.kiyori.app.shell.KiyoriShellExternalDestination

/**
 * MainActivity 外部请求的唯一待处理状态 owner。
 *
 * 这里只保存请求并执行 requestId 匹配后的精确清除。Intent 解析、时间戳生成以及
 * 下载、OAuth、分享等 Android 副作用必须继续由稳定的 MainActivity host 承担；
 * 否则状态所有权和平台生命周期会重新耦合。
 */
internal class KiyoriMainPendingRequests {
    var sharedFileUris: List<Uri>? = null
        private set

    var sharedText: String? = null
        private set

    var browserUrl by mutableStateOf<String?>(null)
        private set

    var browserRequestId by mutableLongStateOf(0L)
        private set

    var gitHubAuthUri: Uri? = null
        private set

    var shortcutNavItem: NavItem? = null
        private set

    var shortcutRequestId: Long = 0L
        private set

    var currentMainNavItem: NavItem = NavItem.AiChat
        private set

    var routeId: String? = null
        private set

    var routeArgs: Map<String, Any?> = emptyMap()
        private set

    var routeRequestId: Long = 0L
        private set

    var shellDestination by mutableStateOf<KiyoriShellExternalDestination?>(null)
        private set

    var shellRequestId by mutableLongStateOf(0L)
        private set

    fun recordShellDestination(
        destination: KiyoriShellExternalDestination,
        requestId: Long,
    ) {
        shellDestination = destination
        shellRequestId = requestId
    }

    fun recordShortcut(
        navItem: NavItem,
        requestId: Long,
    ) {
        shortcutNavItem = navItem
        shortcutRequestId = requestId
        currentMainNavItem = navItem
    }

    fun recordRoute(
        routeId: String,
        routeArgs: Map<String, Any?>,
        requestId: Long,
    ) {
        this.routeId = routeId
        this.routeArgs = routeArgs
        routeRequestId = requestId
    }

    fun recordGitHubAuth(uri: Uri) {
        gitHubAuthUri = uri
    }

    fun recordBrowser(
        url: String,
        requestId: Long,
    ) {
        browserUrl = url
        browserRequestId = requestId
    }

    fun recordSharedFiles(uris: List<Uri>) {
        sharedFileUris = uris
    }

    fun recordSharedText(text: String) {
        sharedText = text
    }

    fun takeGitHubAuthUri(): Uri? =
        gitHubAuthUri.also {
            gitHubAuthUri = null
        }

    fun clearSharedText() {
        sharedText = null
    }

    fun clearSharedFiles() {
        sharedFileUris = null
    }

    fun clearSharedFilesAndText() {
        sharedFileUris = null
        sharedText = null
    }

    fun consumeShortcut(handledRequestId: Long) {
        if (shortcutRequestId != handledRequestId) {
            return
        }
        shortcutNavItem = null
        shortcutRequestId = 0L
    }

    fun updateCurrentMainNavItem(navItem: NavItem) {
        currentMainNavItem = navItem
    }

    fun consumeRoute(handledRequestId: Long) {
        if (routeRequestId != handledRequestId) {
            return
        }
        routeId = null
        routeArgs = emptyMap()
        routeRequestId = 0L
    }

    fun consumeBrowser(handledRequestId: Long) {
        if (browserRequestId != handledRequestId) {
            return
        }
        browserUrl = null
        browserRequestId = 0L
    }

    fun consumeShell(handledRequestId: Long) {
        if (shellRequestId != handledRequestId) {
            return
        }
        shellDestination = null
        shellRequestId = 0L
    }
}
