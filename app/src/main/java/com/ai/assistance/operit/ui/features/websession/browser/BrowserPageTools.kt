package com.ai.assistance.operit.ui.features.websession.browser

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.tts.TextToSpeech
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import org.json.JSONObject

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
    if ((selected == BrowserPageTool.READER || selected == BrowserPageTool.SPEAK) && webView != null) {
        BrowserReadingDialog(webView, documentKey, tools, selected == BrowserPageTool.SPEAK, onDismiss = { tools.selected = null })
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

@Composable
private fun BrowserReadingDialog(webView: WebView, documentKey: String, tools: BrowserPageTools, speaking: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember(documentKey) { mutableStateOf("") }
    var status by remember(documentKey) { mutableStateOf("正在提取网页正文") }
    var truncated by remember(documentKey) { mutableStateOf(false) }
    var speechReady by remember { mutableStateOf(false) }
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(webView, documentKey) {
        var active = true
        webView.evaluateJavascript("""(() => { const root = document.querySelector('article') || document.querySelector('main') || document.body; const text = root ? (root.innerText || '') : ''; return {text:text.slice(0,120000), truncated:text.length>120000}; })()""") { raw ->
            if (active && tools.host.isAssignedTo(webView) && tools.documentKey == documentKey) {
                try { val result = JSONObject(raw); text = result.optString("text"); truncated = result.optBoolean("truncated"); status = if (text.isBlank()) "当前网页没有可提取的正文" else "" }
                catch (error: Exception) { status = "网页正文提取失败" }
            }
        }
        onDispose { active = false }
    }
    DisposableEffect(speaking) {
        var active = true
        val engine = if (speaking) TextToSpeech(context) { code ->
            if (active) { speechReady = code == TextToSpeech.SUCCESS; if (!speechReady) status = "系统朗读引擎不可用" }
        } else null
        speech = engine
        onDispose { active = false; engine?.stop(); engine?.shutdown() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (speaking) "网页朗读" else "阅读模式") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (status.isNotEmpty()) Text(status)
            if (truncated) Text("正文超过显示上限，当前展示前 120,000 个字符")
            SelectionContainer { Text(text) }
        }
    }, confirmButton = {
        if (speaking) TextButton(enabled = speechReady && text.isNotBlank(), onClick = {
            val engine = speech ?: return@TextButton
            engine.stop()
            // 系统引擎限制单次长度，完整分段排队；关闭面板或导航会停止朗读并释放引擎。
            text.chunked((TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(1)).forEachIndexed { i, chunk ->
                if (engine.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "page-$i") == TextToSpeech.ERROR) status = "朗读失败，请检查系统语音设置"
            }
        }) { Text("开始朗读") }
        else TextButton(onClick = onDismiss) { Text("关闭") }
    }, dismissButton = { if (speaking) Row {
        TextButton(onClick = { speech?.stop() }) { Text("停止") }
        TextButton(onClick = onDismiss) { Text("关闭") }
    } })
}
