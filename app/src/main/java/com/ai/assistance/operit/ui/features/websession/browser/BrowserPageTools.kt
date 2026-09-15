package com.ai.assistance.operit.ui.features.websession.browser

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebViewHost
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal enum class BrowserPageTool { READER, SPEAK, FIND, SAVE_ARCHIVE, PDF, SHORTCUT }

/** 工具只操作宿主已持有的活动 WebView；不建立第二个页面、会话或页面导航状态。 */
@Stable
internal class BrowserPageTools(val host: WebSessionWebViewHost) {
    var selected by mutableStateOf<BrowserPageTool?>(null)
    var documentKey by mutableStateOf("")
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    val hasPage: Boolean get() = host.currentWebView() != null
    fun open(tool: BrowserPageTool) { if (hasPage) selected = tool }
}

@Composable
internal fun rememberBrowserPageTools(host: WebSessionWebViewHost, token: String?, url: String, title: String): BrowserPageTools {
    val tools = remember(host) { BrowserPageTools(host) }
    // 地址/文档变化立即使已捕获操作过期；异步结果不得落到后来导航的新网页上。
    SideEffect {
        val key = "$token\n$url"
        if (tools.documentKey != key) tools.selected = null
        tools.documentKey = key; tools.url = url; tools.title = title
    }
    return tools
}

@Composable
internal fun BrowserPageToolsUi(tools: BrowserPageTools) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingArchive by remember { mutableStateOf<Pair<WebView, String>?>(null) }
    var saving by remember { mutableStateOf(false) }
    fun notify(message: String) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    val saveArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("multipart/related")) { target ->
        val pending = pendingArchive
        pendingArchive = null
        if (target != null && pending != null) {
            if (!tools.host.isAssignedTo(pending.first) || tools.documentKey != pending.second) {
                notify("网页已变化，请重新选择保存网页")
            } else {
                saving = true
                scope.launch {
                    var temporary: File? = null
                    try {
                        val archive = File.createTempFile("browser-archive-", ".mht", context.cacheDir)
                        temporary = archive
                        val saved = suspendCancellableCoroutine<String?> { continuation ->
                            pending.first.saveWebArchive(archive.absolutePath, false) { path ->
                                // 离页取消后 WebView 仍可能完成写入；晚到回调也负责清理，避免缓存残留。
                                if (continuation.isActive) continuation.resume(path) else archive.delete()
                            }
                        }
                        check(saved != null && tools.host.isAssignedTo(pending.first) && tools.documentKey == pending.second) { "网页已变化或保存失败" }
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(target, "wt")?.use { output -> archive.inputStream().use { it.copyTo(output) } }
                                ?: error("无法写入所选位置")
                        }
                        notify("网页已保存为 MHTML")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        com.ai.assistance.operit.util.AppLogger.w("BrowserPageTools", "Archive export failed", error)
                        notify("网页保存失败，请检查保存位置")
                    } finally {
                        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { temporary?.delete() }
                        saving = false
                    }
                }
            }
        }
    }
    val selected = tools.selected
    val documentKey = tools.documentKey
    val webView = tools.host.currentWebView()
    LaunchedEffect(selected, documentKey) {
        if (webView == null) return@LaunchedEffect
        try {
            when (selected) {
                BrowserPageTool.SAVE_ARCHIVE -> {
                    tools.selected = null
                    if (saving || pendingArchive != null) notify("已有网页保存任务")
                    else { pendingArchive = webView to documentKey; saveArchive.launch("网页-${System.currentTimeMillis()}.mht") }
                }
                BrowserPageTool.PDF -> {
                    tools.selected = null
                    val name = tools.title.ifBlank { "网页" }.take(100)
                    val printer = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    printer.print(name, webView.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
                }
                BrowserPageTool.SHORTCUT -> {
                    tools.selected = null
                    val uri = Uri.parse(tools.url)
                    check(uri.scheme in listOf("http", "https")) { "当前页面不能添加桌面" }
                    val manager = context.getSystemService(ShortcutManager::class.java)
                    check(manager.isRequestPinShortcutSupported) { "当前桌面不支持添加快捷方式" }
                    val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
                        .setAction(Intent.ACTION_VIEW).setData(uri)
                    val id = MessageDigest.getInstance("SHA-256").digest(tools.url.toByteArray()).joinToString("") { "%02x".format(it) }
                    val shortcut = ShortcutInfo.Builder(context, "web-$id").setShortLabel(tools.title.ifBlank { uri.host ?: "网页" }.take(40))
                        .setIntent(intent).setIcon(AndroidIcon.createWithResource(context, R.drawable.ic_kiyori_browser_bottom_home)).build()
                    if (!manager.requestPinShortcut(shortcut, null)) notify("桌面未接受快捷方式请求")
                }
                else -> Unit
            }
        } catch (error: Exception) {
            tools.selected = null
            com.ai.assistance.operit.util.AppLogger.w("BrowserPageTools", "Page action failed", error)
            notify(error.message ?: "当前网页操作无法完成")
        }
    }
    if (selected == BrowserPageTool.FIND && webView != null) BrowserFindDialog(webView, documentKey, onDismiss = { tools.selected = null })
    // 阅读与朗读共用同一份正文提取与同一个整页版式；朗读只是进入时直接开始播报。
    if ((selected == BrowserPageTool.READER || selected == BrowserPageTool.SPEAK) && webView != null) {
        BrowserReaderScreen(
            webView = webView,
            documentKey = documentKey,
            tools = tools,
            startSpeaking = selected == BrowserPageTool.SPEAK,
            onDismiss = { tools.selected = null },
        )
    }
}

@Composable
private fun BrowserFindDialog(webView: WebView, documentKey: String, onDismiss: () -> Unit) {
    var query by remember(documentKey) { mutableStateOf("") }
    var matches by remember(documentKey) { mutableStateOf("输入要查找的文字") }
    DisposableEffect(webView, documentKey) {
        webView.setFindListener { active, count, done -> if (done) matches = if (count == 0) "没有匹配项" else "${active + 1} / $count" }
        onDispose { webView.setFindListener(null); webView.clearMatches() }
    }
    LaunchedEffect(query) { if (query.isBlank()) { webView.clearMatches(); matches = "输入要查找的文字" } else webView.findAllAsync(query) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("页内查找") }, text = {
        Column { OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("查找文字") }); Text(matches) }
    }, confirmButton = { Row {
        TextButton(onClick = { webView.findNext(false) }, enabled = query.isNotBlank()) { Text("上一项") }
        TextButton(onClick = { webView.findNext(true) }, enabled = query.isNotBlank()) { Text("下一项") }
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
