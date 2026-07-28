package com.ai.assistance.operit.ui.main.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.DEFAULT_BROWSER_HOME_URL
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isSupportedBrowserHomeUrl

internal enum class KiyoriBrowserSettingsEntryKind {
    NAVIGATION,
    TOGGLE,
}

internal enum class KiyoriBrowserSettingsAction {
    NONE,
    OPEN_PLACEHOLDER,
    OPEN_HOME_CUSTOMIZATION,
    TOGGLE_AUTOMATIC_FLOATING_PLAYBACK,
    TOGGLE_WEB_PAGE_OPEN_APP,
    TOGGLE_WEB_PAGE_GEOLOCATION,
}

internal const val KIYORI_BROWSER_SETTINGS_PAGE_TITLE = "网页浏览器设置"

internal data class KiyoriBrowserSettingsEntrySpec(
    val title: String,
    val kind: KiyoriBrowserSettingsEntryKind,
    val value: String? = null,
    val staticToggleValue: Boolean = false,
    val action: KiyoriBrowserSettingsAction = KiyoriBrowserSettingsAction.NONE,
)

internal val kiyoriBrowserSettingsGroups =
    listOf(
        listOf(
            navigationSpec("网页插件管理"),
            toggleSpec(
                title = "自动悬浮播放",
                staticToggleValue = true,
                action = KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK,
            ),
            navigationSpec("悬浮嗅探模式"),
            toggleSpec("返回不重载", staticToggleValue = false),
            navigationSpec("启动时恢复标签", value = "不恢复"),
        ),
        listOf(
            navigationSpec(
                title = "网页主页自定义",
                action = KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION,
            ),
            navigationSpec("标签栏样式设置", value = "图文卡片"),
            toggleSpec("手势前进后退", staticToggleValue = true),
            toggleSpec("底部上滑手势", staticToggleValue = true),
            toggleSpec("搜索引擎切换条", staticToggleValue = true),
        ),
        listOf(
            toggleSpec("音视频嗅探提示", staticToggleValue = true),
            navigationSpec("嗅探规则管理"),
        ),
        listOf(
            toggleSpec(
                title = "允许网页打开应用",
                staticToggleValue = true,
                action = KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP,
            ),
            toggleSpec(
                title = "允许网页获取位置",
                staticToggleValue = true,
                action = KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION,
            ),
            navigationSpec("网页翻译接口", value = "百度翻译"),
            navigationSpec("网站配置管理"),
            navigationSpec("网站密码管理"),
        ),
        listOf(
            navigationSpec("网页字体大小"),
            toggleSpec("强制页面缩放", staticToggleValue = false),
            navigationSpec("腾讯X5调试"),
            navigationSpec("自定义UA设置"),
            navigationSpec("浏览器代理替换"),
            toggleSpec("强制新窗口打开", staticToggleValue = false),
        ),
    )

private fun navigationSpec(
    title: String,
    value: String? = null,
    action: KiyoriBrowserSettingsAction = KiyoriBrowserSettingsAction.OPEN_PLACEHOLDER,
): KiyoriBrowserSettingsEntrySpec =
    KiyoriBrowserSettingsEntrySpec(
        title = title,
        kind = KiyoriBrowserSettingsEntryKind.NAVIGATION,
        value = value,
        action = action,
    )

private fun toggleSpec(
    title: String,
    staticToggleValue: Boolean,
    action: KiyoriBrowserSettingsAction = KiyoriBrowserSettingsAction.NONE,
): KiyoriBrowserSettingsEntrySpec =
    KiyoriBrowserSettingsEntrySpec(
        title = title,
        kind = KiyoriBrowserSettingsEntryKind.TOGGLE,
        staticToggleValue = staticToggleValue,
        action = action,
    )

private enum class KiyoriBrowserSettingsSubPage {
    HOME_CUSTOMIZATION,
    PLACEHOLDER,
}

@Composable
internal fun KiyoriBrowserSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coordinator =
        remember(context) {
            BrowserPresentationCoordinator.getInstance(context.applicationContext)
        }
    val historyStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val settings by coordinator.browserSettings.collectAsState()
    val searchEngine by
        historyStore.searchEngineFlow.collectAsState(initial = WebSessionSearchEngine.DEFAULT)
    var subPageName by rememberSaveable { mutableStateOf<String?>(null) }
    var placeholderTitle by rememberSaveable { mutableStateOf("") }
    val subPage = subPageName?.let(KiyoriBrowserSettingsSubPage::valueOf)

    fun closeCurrentPage() {
        if (subPage == null) {
            onBack()
        } else {
            subPageName = null
            placeholderTitle = ""
        }
    }

    BackHandler(onBack = ::closeCurrentPage)

    when (subPage) {
        null ->
            KiyoriBrowserSettingsDetailPage(
                settings = settings,
                onBack = ::closeCurrentPage,
                onOpenHomeCustomization = {
                    subPageName = KiyoriBrowserSettingsSubPage.HOME_CUSTOMIZATION.name
                },
                onOpenPlaceholder = { title ->
                    placeholderTitle = title
                    subPageName = KiyoriBrowserSettingsSubPage.PLACEHOLDER.name
                },
                onSetAutomaticFloatingPlaybackEnabled =
                    coordinator::setAutomaticFloatingPlaybackEnabled,
                onSetAllowWebPageOpenApp = coordinator::setAllowWebPageOpenApp,
                onSetAllowWebPageGeolocation = coordinator::setAllowWebPageGeolocation,
                modifier = modifier,
            )
        KiyoriBrowserSettingsSubPage.HOME_CUSTOMIZATION ->
            KiyoriBrowserSettingsSubPageScaffold(
                title = "网页主页自定义",
                onBack = ::closeCurrentPage,
                modifier = modifier,
            ) {
                KiyoriBrowserHomepageCustomizationPage(
                    currentHomeUrl = settings.homeUrl,
                    onSave = { value ->
                        val resolvedUrl = BrowserAddressResolver.resolve(value, searchEngine)
                        if (!isSupportedBrowserHomeUrl(resolvedUrl)) {
                            Toast.makeText(
                                context,
                                "自定义主页入口格式无效",
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@KiyoriBrowserHomepageCustomizationPage false
                        }
                        coordinator.setBrowserHomeUrl(resolvedUrl)
                        Toast.makeText(
                            context,
                            "自定义主页入口已保存",
                            Toast.LENGTH_SHORT,
                        ).show()
                        true
                    },
                    onReset = {
                        coordinator.setBrowserHomeUrl(DEFAULT_BROWSER_HOME_URL)
                        Toast.makeText(
                            context,
                            "已恢复为空白页",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        KiyoriBrowserSettingsSubPage.PLACEHOLDER ->
            KiyoriBrowserSettingsSubPageScaffold(
                title = placeholderTitle,
                onBack = ::closeCurrentPage,
                modifier = modifier,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(KIYORI_SETTINGS_PAGE_BACKGROUND),
                )
            }
    }
}

@Composable
private fun KiyoriBrowserSettingsSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = KIYORI_SETTINGS_PAGE_BACKGROUND,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            KiyoriBrowserSettingsHeader(
                title = title,
                onBack = onBack,
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(KIYORI_SETTINGS_PAGE_BACKGROUND)
                    .padding(paddingValues),
        ) {
            content()
        }
    }
}

@Composable
private fun KiyoriBrowserSettingsHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KIYORI_SETTINGS_PAGE_BACKGROUND)
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color(0xFF2B2B2B),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF202020),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KiyoriBrowserSettingsDetailPage(
    settings: WebSessionBrowserSettings,
    onBack: () -> Unit,
    onOpenHomeCustomization: () -> Unit,
    onOpenPlaceholder: (String) -> Unit,
    onSetAutomaticFloatingPlaybackEnabled: (Boolean) -> Unit,
    onSetAllowWebPageOpenApp: (Boolean) -> Unit,
    onSetAllowWebPageGeolocation: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    KiyoriCollapsingSettingsPage(
        title = KIYORI_BROWSER_SETTINGS_PAGE_TITLE,
        onBack = onBack,
        modifier = modifier,
    ) {
        itemsIndexed(kiyoriBrowserSettingsGroups) { _, group ->
            KiyoriSettingsGroupCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
            ) {
                group.forEachIndexed { index, entry ->
                    KiyoriBrowserSettingsRow(
                        entry = entry,
                        settings = settings,
                        onOpenHomeCustomization = onOpenHomeCustomization,
                        onOpenPlaceholder = onOpenPlaceholder,
                        onSetAutomaticFloatingPlaybackEnabled =
                            onSetAutomaticFloatingPlaybackEnabled,
                        onSetAllowWebPageOpenApp = onSetAllowWebPageOpenApp,
                        onSetAllowWebPageGeolocation = onSetAllowWebPageGeolocation,
                    )
                    if (index != group.lastIndex) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(0.6.dp)
                                    .background(Color(0xFFF2F2EE)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KiyoriBrowserSettingsRow(
    entry: KiyoriBrowserSettingsEntrySpec,
    settings: WebSessionBrowserSettings,
    onOpenHomeCustomization: () -> Unit,
    onOpenPlaceholder: (String) -> Unit,
    onSetAutomaticFloatingPlaybackEnabled: (Boolean) -> Unit,
    onSetAllowWebPageOpenApp: (Boolean) -> Unit,
    onSetAllowWebPageGeolocation: (Boolean) -> Unit,
) {
    val toggleValue =
        when (entry.action) {
            KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK ->
                settings.automaticFloatingPlaybackEnabled
            KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP ->
                settings.allowWebPageOpenApp
            KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION ->
                settings.allowWebPageGeolocation
            else -> entry.staticToggleValue
        }
    val onClick: (() -> Unit)? =
        when (entry.action) {
            KiyoriBrowserSettingsAction.OPEN_PLACEHOLDER ->
                ({ onOpenPlaceholder(entry.title) })
            KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION -> onOpenHomeCustomization
            KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK ->
                ({ onSetAutomaticFloatingPlaybackEnabled(!toggleValue) })
            KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP ->
                ({ onSetAllowWebPageOpenApp(!toggleValue) })
            KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION ->
                ({ onSetAllowWebPageGeolocation(!toggleValue) })
            KiyoriBrowserSettingsAction.NONE -> null
        }
    val rowModifier =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(onClick = onClick)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(rowModifier)
                .padding(start = 18.dp, end = 14.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2B2B2B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        when (entry.kind) {
            KiyoriBrowserSettingsEntryKind.NAVIGATION -> {
                entry.value?.let { value ->
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        color = Color(0xFF9A9895),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Spacer(modifier = Modifier.width(7.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFFBDBDB8),
                    modifier = Modifier.size(18.dp),
                )
            }
            KiyoriBrowserSettingsEntryKind.TOGGLE ->
                KiyoriBrowserSettingsCheckIndicator(enabled = toggleValue)
        }
    }
}

@Composable
private fun KiyoriBrowserSettingsCheckIndicator(enabled: Boolean) {
    Box(
        modifier =
            Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (enabled) Color(0xFFCBBEFF) else Color.White)
                .border(
                    width = if (enabled) 0.dp else 1.dp,
                    color = if (enabled) Color.Transparent else Color(0xFFBAB4AE),
                    shape = RoundedCornerShape(3.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun KiyoriBrowserHomepageCustomizationPage(
    currentHomeUrl: String,
    onSave: (String) -> Boolean,
    onReset: () -> Unit,
) {
    var showEditDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }
        item {
            KiyoriSettingsGroupCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
            ) {
                KiyoriBrowserSettingsValueRow(
                    title = "自定义主页入口",
                    value = currentHomeUrl,
                    onClick = { showEditDialog = true },
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(Color(0xFFF2F2EE)),
                )
                KiyoriBrowserSettingsValueRow(
                    title = "恢复为空白页",
                    value = DEFAULT_BROWSER_HOME_URL,
                    onClick = onReset,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    if (showEditDialog) {
        KiyoriBrowserHomepageEditDialog(
            initialValue = currentHomeUrl,
            onDismiss = { showEditDialog = false },
            onConfirm = { value ->
                if (onSave(value)) {
                    showEditDialog = false
                }
            },
        )
    }
}

@Composable
private fun KiyoriBrowserSettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 18.dp, end = 15.dp, top = 17.dp, bottom = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2B2B2B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            color = Color(0xFF94948F),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFBDBDB8),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun KiyoriBrowserHomepageEditDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var inputValue by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "自定义主页入口",
                    color = Color(0xFF111111),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    text = "设置后，浏览器点击主页会直接打开这里配置的地址。",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "入口地址",
                    color = Color(0xFF111111),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    label = { Text("自定义主页入口") },
                    placeholder = { Text("输入网址或 about:blank") },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF111111),
                            unfocusedBorderColor = Color(0xFFD6D6D6),
                            focusedLabelColor = Color(0xFF111111),
                            unfocusedLabelColor = Color(0xFF666666),
                            cursorColor = Color(0xFF111111),
                            focusedTextColor = Color(0xFF111111),
                            unfocusedTextColor = Color(0xFF111111),
                            focusedPlaceholderColor = Color(0xFF9B9B9B),
                            unfocusedPlaceholderColor = Color(0xFF9B9B9B),
                        ),
                )
                Text(
                    text = "支持完整网址，也可以直接填写 about:blank 作为空白页。",
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(inputValue.trim()) }) {
                Text(
                    text = "保存",
                    color = Color(0xFF111111),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    color = Color(0xFF777777),
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
    )
}
