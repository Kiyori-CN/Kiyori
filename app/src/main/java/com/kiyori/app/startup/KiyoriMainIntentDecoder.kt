package com.kiyori.app.startup

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.widget.ToolPkgDesktopWidgetHost
import com.kiyori.app.shell.KiyoriShellExternalDestination

/**
 * MainActivity 对外 Intent 常量的单一字面值来源。
 *
 * MainActivity companion 继续暴露原字段，保持现有调用方和 Android userspace 合同不变。
 */
internal object KiyoriMainIntentContract {
    const val ACTION_OPEN_SETTINGS_SHORTCUT =
        "com.ai.assistance.operit.action.OPEN_SETTINGS_SHORTCUT"
    const val ACTION_OPEN_KIYORI_BROWSER = "com.kiyori.action.OPEN_BROWSER"
    const val ACTION_RESTORE_KIYORI_BROWSER_FROM_INDICATOR =
        "com.kiyori.action.RESTORE_BROWSER_FROM_INDICATOR"
    const val ACTION_OPEN_KIYORI_DOWNLOADS = "com.kiyori.action.OPEN_DOWNLOADS"
    const val ACTION_OPEN_KIYORI_DOWNLOAD_TASK =
        "com.kiyori.action.OPEN_DOWNLOAD_TASK"
    const val EXTRA_KIYORI_DOWNLOAD_TASK_ID =
        "com.kiyori.extra.DOWNLOAD_TASK_ID"
    const val ACTION_OPEN_KIYORI_BROWSER_SETTINGS =
        "com.kiyori.action.OPEN_BROWSER_SETTINGS"
    const val ACTION_OPEN_KIYORI_DOWNLOAD_SETTINGS =
        "com.kiyori.action.OPEN_DOWNLOAD_SETTINGS"
    const val ACTION_RESTART_PLAYER_AFTER_CRASH =
        "com.kiyori.action.RESTART_PLAYER_AFTER_CRASH"
    const val EXTRA_PLAYER_RUNTIME_GENERATION =
        "com.kiyori.extra.PLAYER_RUNTIME_GENERATION"
}

internal sealed interface KiyoriMainIntentCommand {
    data object None : KiyoriMainIntentCommand

    data class RestartPlayer(
        val runtimeGeneration: Long,
    ) : KiyoriMainIntentCommand

    data class OpenDownloadedTask(
        val taskId: String,
    ) : KiyoriMainIntentCommand

    data class OpenShellDestination(
        val destination: KiyoriShellExternalDestination,
    ) : KiyoriMainIntentCommand

    data object OpenSettingsShortcut : KiyoriMainIntentCommand

    data class OpenRoute(
        val routeId: String,
        val routeArgsJson: String?,
    ) : KiyoriMainIntentCommand

    data class CompleteGitHubAuth(
        val uri: Uri,
    ) : KiyoriMainIntentCommand

    data class OpenBrowser(
        val url: String,
    ) : KiyoriMainIntentCommand

    data class OpenSharedFile(
        val uri: Uri,
    ) : KiyoriMainIntentCommand

    data class ShareSingle(
        val uri: Uri?,
        val text: String?,
    ) : KiyoriMainIntentCommand

    data class ShareMultiple(
        val uris: List<Uri>,
        val text: String?,
    ) : KiyoriMainIntentCommand
}

internal data class KiyoriMainIntentDecoding(
    val command: KiyoriMainIntentCommand,
    val processPendingSharedContent: Boolean,
)

internal fun resolveKiyoriShellExternalDestination(
    action: String?,
): KiyoriShellExternalDestination? =
    when (action) {
        KiyoriMainIntentContract.ACTION_OPEN_KIYORI_BROWSER ->
            KiyoriShellExternalDestination.BROWSER_HOME
        KiyoriMainIntentContract.ACTION_RESTORE_KIYORI_BROWSER_FROM_INDICATOR ->
            KiyoriShellExternalDestination.BROWSER_HOME_FROM_MINIMIZED_INDICATOR
        KiyoriMainIntentContract.ACTION_OPEN_KIYORI_DOWNLOADS ->
            KiyoriShellExternalDestination.DOWNLOADS
        KiyoriMainIntentContract.ACTION_OPEN_KIYORI_BROWSER_SETTINGS ->
            KiyoriShellExternalDestination.BROWSER_SETTINGS
        KiyoriMainIntentContract.ACTION_OPEN_KIYORI_DOWNLOAD_SETTINGS ->
            KiyoriShellExternalDestination.DOWNLOAD_SETTINGS
        else -> null
    }

internal fun resolveKiyoriDownloadTaskId(
    action: String?,
    taskId: String?,
): String? =
    taskId
        ?.trim()
        ?.takeIf { value ->
            action == KiyoriMainIntentContract.ACTION_OPEN_KIYORI_DOWNLOAD_TASK &&
                value.isNotBlank()
        }

/**
 * 只读取 Intent 并生成命令，不修改 Intent，也不执行 Activity、runtime 或 UI 副作用。
 */
internal fun decodeKiyoriMainIntent(
    intent: Intent?,
): KiyoriMainIntentDecoding {
    val action = intent?.action
    val processPendingSharedContent =
        action == Intent.ACTION_VIEW ||
            action == Intent.ACTION_SEND ||
            action == Intent.ACTION_SEND_MULTIPLE

    if (intent == null) {
        return KiyoriMainIntentDecoding(
            command = KiyoriMainIntentCommand.None,
            processPendingSharedContent = false,
        )
    }

    if (action == KiyoriMainIntentContract.ACTION_RESTART_PLAYER_AFTER_CRASH) {
        return KiyoriMainIntentDecoding(
            command =
                KiyoriMainIntentCommand.RestartPlayer(
                    runtimeGeneration =
                        intent.getLongExtra(
                            KiyoriMainIntentContract.EXTRA_PLAYER_RUNTIME_GENERATION,
                            0L,
                        ),
                ),
            processPendingSharedContent = processPendingSharedContent,
        )
    }

    resolveKiyoriDownloadTaskId(
        action = action,
        taskId =
            intent.getStringExtra(
                KiyoriMainIntentContract.EXTRA_KIYORI_DOWNLOAD_TASK_ID
            ),
    )?.let { taskId ->
        return KiyoriMainIntentDecoding(
            command = KiyoriMainIntentCommand.OpenDownloadedTask(taskId),
            processPendingSharedContent = processPendingSharedContent,
        )
    }

    resolveKiyoriShellExternalDestination(action)?.let { destination ->
        return KiyoriMainIntentDecoding(
            command = KiyoriMainIntentCommand.OpenShellDestination(destination),
            processPendingSharedContent = processPendingSharedContent,
        )
    }

    if (action == KiyoriMainIntentContract.ACTION_OPEN_SETTINGS_SHORTCUT) {
        return KiyoriMainIntentDecoding(
            command = KiyoriMainIntentCommand.OpenSettingsShortcut,
            processPendingSharedContent = processPendingSharedContent,
        )
    }

    val routeId =
        intent
            .getStringExtra(ToolPkgDesktopWidgetHost.EXTRA_OPEN_ROUTE_ID)
            ?.trim()
            .orEmpty()
    if (routeId.isNotBlank()) {
        return KiyoriMainIntentDecoding(
            command =
                KiyoriMainIntentCommand.OpenRoute(
                    routeId = routeId,
                    routeArgsJson =
                        intent.getStringExtra(
                            ToolPkgDesktopWidgetHost.EXTRA_OPEN_ROUTE_ARGS_JSON
                        ),
                ),
            processPendingSharedContent = processPendingSharedContent,
        )
    }

    val intentUri = intent.data
    if (intentUri != null && GitHubAuthPreferences.isOAuthRedirectUri(intentUri)) {
        return KiyoriMainIntentDecoding(
            command = KiyoriMainIntentCommand.CompleteGitHubAuth(intentUri),
            processPendingSharedContent = processPendingSharedContent,
        )
    }

    val command =
        when (action) {
            Intent.ACTION_VIEW ->
                when {
                    intentUri == null -> KiyoriMainIntentCommand.None
                    intentUri.scheme.equals("http", ignoreCase = true) ||
                        intentUri.scheme.equals("https", ignoreCase = true) ||
                        isKiyoriLocalHtmlView(intentUri.scheme, intent.type, intentUri.path) ->
                        KiyoriMainIntentCommand.OpenBrowser(intentUri.toString())
                    else -> KiyoriMainIntentCommand.OpenSharedFile(intentUri)
                }

            Intent.ACTION_SEND ->
                KiyoriMainIntentCommand.ShareSingle(
                    uri = intent.readSingleSharedUri(),
                    text =
                        intent
                            .getStringExtra(Intent.EXTRA_TEXT)
                            ?.takeIf { value -> value.isNotBlank() },
                )

            Intent.ACTION_SEND_MULTIPLE ->
                KiyoriMainIntentCommand.ShareMultiple(
                    uris = intent.readMultipleSharedUris(),
                    text =
                        intent
                            .getStringExtra(Intent.EXTRA_TEXT)
                            ?.takeIf { value -> value.isNotBlank() },
                )

            else -> KiyoriMainIntentCommand.None
        }

    return KiyoriMainIntentDecoding(
        command = command,
        processPendingSharedContent = processPendingSharedContent,
    )
}

/** 只把显式 VIEW 的本地 HTML 交给浏览器；分享、源码文本及其余附件保持原入口。 */
internal fun isKiyoriLocalHtmlView(scheme: String?, mimeType: String?, path: String?): Boolean {
    if (!scheme.equals("file", true) && !scheme.equals("content", true)) return false
    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)
    if (mime == "text/html" || mime == "application/xhtml+xml") return true
    if (!mime.isNullOrEmpty() && mime != "application/octet-stream") return false
    val extension = path?.substringAfterLast('.', "")?.lowercase(java.util.Locale.ROOT)
    return extension == "html" || extension == "htm" || extension == "xhtml"
}

private fun Intent.readSingleSharedUri(): Uri? {
    @Suppress("DEPRECATION")
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
}

private fun Intent.readMultipleSharedUris(): List<Uri> {
    @Suppress("DEPRECATION")
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
}
