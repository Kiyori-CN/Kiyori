package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FileManagerPreferences
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.*
import com.ai.assistance.operit.widget.ToolPkgDesktopWidgetHost
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.security.MessageDigest
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.storage.KiyoriPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val FILE_MANAGER_SHORTCUT_ROUTE = "kiyori.file_manager.shortcut"
private fun shortcutId(entry: FileManagerStorageEntry) = MessageDigest.getInstance("SHA-256")
    .digest(entry.storageId.toByteArray()).joinToString("") { "%02x".format(it) }

/** Shell 只交接已有会话；解析、目录准备和失败提示归文件管理器适配层。 */
internal suspend fun openFileManagerShortcut(context: Context, id: String, session: FileManagerViewModel) {
    try {
        check(!session.isWriting) { "文件任务进行中，请结束后再次打开快捷方式" }
        val entry = resolveFileManagerShortcut(context, id)
        check(entry != null) { "此入口已移除，请在文件管理器中重新创建快捷方式" }
        if (entry.category == "工作区" && entry.fileBookmark == null &&
            FileManagerPreferences.getInstance(context).current.defaultWorkspacePath.isBlank()) {
            withContext(Dispatchers.IO) { KiyoriPaths.workspaceDir() }
        }
        // 读取偏好和准备目录会挂起；返回后再次检查，不能切走期间开始的写入任务。
        check(!session.isWriting) { "文件任务进行中，请结束后再次打开快捷方式" }
        val bookmark = entry.fileBookmark
        if (bookmark != null && !bookmark.directory) {
            session.navigateToPath(bookmark.path.substringBeforeLast('/').ifBlank { "/" }, bookmark.environment)
            session.setDirectoryFilter(bookmark.path.substringAfterLast('/'))
        } else session.navigateToPath(entry.path, entry.environment)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) {
        AppLogger.e("FileManagerShortcut", "无法打开存储入口", failure)
        android.widget.Toast.makeText(context, failure.message ?: "无法打开快捷方式", android.widget.Toast.LENGTH_LONG).show()
    }
}

/** 桌面只持有入口 ID。打开时重新解析当前记录，网络凭据、SAF URI 和绝对路径均不写入 Intent。 */
fun requestFileManagerShortcut(context: Context, entry: FileManagerStorageEntry) {
    val manager = context.getSystemService(ShortcutManager::class.java)
    check(manager.isRequestPinShortcutSupported) { "当前桌面不支持创建快捷方式" }
    val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        .setAction(Intent.ACTION_VIEW)
        .putExtra(ToolPkgDesktopWidgetHost.EXTRA_OPEN_ROUTE_ID, FILE_MANAGER_SHORTCUT_ROUTE)
        .putExtra(ToolPkgDesktopWidgetHost.EXTRA_OPEN_ROUTE_ARGS_JSON, buildJsonObject { put("entryId", shortcutId(entry)) }.toString())
    val id = shortcutId(entry)
    val shortcut = ShortcutInfo.Builder(context, "file-$id").setShortLabel(entry.title.take(40))
        .setIntent(intent).setIcon(Icon.createWithResource(context, R.drawable.ic_kiyori_nav_folder)).build()
    check(manager.requestPinShortcut(shortcut, null)) { "桌面未接受快捷方式请求" }
}

suspend fun resolveFileManagerShortcut(context: Context, id: String): FileManagerStorageEntry? {
    val api = ApiPreferences.getInstance(context)
    val settings = FileManagerPreferences.getInstance(context).current
    val workspacePath = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        .resolve("Kiyori/workspace").absolutePath
    val entries = defaultFileManagerStorageEntries(workspacePath) + api.fileBookmarksFlow.first().map {
        FileManagerStorageEntry(it.name, it.path, it.environment, fileBookmark = it, category = "书签")
    } + api.fileWorkspacesFlow.first().map {
        FileManagerStorageEntry(it.name, it.path, it.environment, fileBookmark = it, category = "工作区")
    } + api.fileNetworksFlow.first().map {
        FileManagerStorageEntry(it.name, "/", "network:${it.id}", category = "网络", network = it)
    }
    // 隐藏分类不让既有桌面入口失效；被真正删除的入口必须明确失败。
    return projectFileManagerStorageEntries(entries, settings.copy(drawerHidden = emptySet())).firstOrNull { shortcutId(it) == id }
}
