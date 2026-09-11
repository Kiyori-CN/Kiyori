package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxAction
import com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxDrawer
import com.ai.assistance.operit.ui.main.components.LocalKiyoriOpenFileManager
import com.ai.assistance.operit.ui.main.components.LocalKiyoriOpenBrowserPasswords

@Composable
internal fun BrowserToolboxDrawer(onDismiss: () -> Unit, onOpenAiDialogue: () -> Unit, pageTools: BrowserPageTools) {
    val openFiles = LocalKiyoriOpenFileManager.current
    val openPasswords = LocalKiyoriOpenBrowserPasswords.current
    KiyoriToolboxDrawer(onDismiss = onDismiss, actions = listOf(
        KiyoriToolboxAction("AI对话", Icons.AutoMirrored.Outlined.Chat, WebSessionBrowserMenuTone.AI_DIALOGUE, onClick = onOpenAiDialogue),
        KiyoriToolboxAction("文件管理", Icons.Outlined.Folder, WebSessionBrowserMenuTone.BOOKMARKS, openFiles != null, onClick = { openFiles?.invoke() }),
        KiyoriToolboxAction("密码管理", Icons.Outlined.Key, WebSessionBrowserMenuTone.TOOLBOX, openPasswords != null, onClick = { openPasswords?.invoke() }),
        KiyoriToolboxAction("网页翻译\n未接入", Icons.Outlined.Translate, WebSessionBrowserMenuTone.USER_AGENT, false, "未接入翻译服务", {}),
        KiyoriToolboxAction("网页朗读", Icons.Outlined.RecordVoiceOver, WebSessionBrowserMenuTone.READER_MODE, pageTools.hasPage, onClick = { pageTools.open(BrowserPageTool.SPEAK) }),
        KiyoriToolboxAction("页内查找", Icons.Outlined.FindInPage, WebSessionBrowserMenuTone.ADD_BOOKMARK, pageTools.hasPage, onClick = { pageTools.open(BrowserPageTool.FIND) }),
        KiyoriToolboxAction("保存网页", Icons.Outlined.SaveAlt, WebSessionBrowserMenuTone.DOWNLOADS, pageTools.hasPage, onClick = { pageTools.open(BrowserPageTool.SAVE_ARCHIVE) }),
        KiyoriToolboxAction("保存PDF", Icons.Outlined.PictureAsPdf, WebSessionBrowserMenuTone.AD_MARKING, pageTools.hasPage, onClick = { pageTools.open(BrowserPageTool.PDF) }),
        KiyoriToolboxAction("添加桌面", Icons.AutoMirrored.Outlined.AddToHomeScreen, WebSessionBrowserMenuTone.SITE_CONFIG, pageTools.hasPage, onClick = { pageTools.open(BrowserPageTool.SHORTCUT) }),
    ))
}
