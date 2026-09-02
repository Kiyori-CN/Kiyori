package com.ai.assistance.operit.ui.features.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserCookieUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isSupportedBrowserCookieUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.parseBrowserCookieHeader

@Composable
internal fun WebSessionBrowserCookieSheet(
    state: BrowserCookieUiState,
    currentPageUrl: String,
    cookieReaderEnabled: Boolean,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val header = state.header
    val cookies = remember(header) { header?.let(::parseBrowserCookieHeader).orEmpty() }
    val targetUrl = state.targetUrl.ifBlank { currentPageUrl }
    val supported = isSupportedBrowserCookieUrl(targetUrl)

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    ) {
        WebSessionDrawerHeader(
            title = stringResource(R.string.web_session_cookie_reader_title),
            leadingIcon = Icons.Filled.Lock,
            tone = WebSessionBrowserMenuTone.PLUGINS,
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
            countText = targetUrl.takeIf { supported }?.let {
                pluralStringResource(
                    R.plurals.web_session_cookie_reader_count,
                    cookies.size,
                    cookies.size,
                )
            },
            actions = {
                IconButton(
                    onClick = onRefresh,
                    enabled = cookieReaderEnabled && supported,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.web_session_cookie_reader_refresh),
                    )
                }
            },
        )

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = WebSessionBrowserPopupShape,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text =
                            if (supported) {
                                stringResource(R.string.web_session_cookie_reader_current_page)
                            } else {
                                stringResource(R.string.web_session_cookie_reader_unsupported_title)
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            targetUrl.ifBlank {
                                stringResource(R.string.web_session_cookie_reader_no_active_page)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when {
                !cookieReaderEnabled ->
                    WebSessionEmptyState(
                        icon = Icons.Filled.Lock,
                        title = stringResource(R.string.web_session_cookie_reader_disabled_title),
                        message = stringResource(R.string.web_session_cookie_reader_disabled_message),
                        tone = WebSessionBrowserMenuTone.PLUGINS,
                    )
                !supported ->
                    WebSessionEmptyState(
                        icon = Icons.Filled.Lock,
                        title = stringResource(R.string.web_session_cookie_reader_unsupported_title),
                        message = stringResource(R.string.web_session_cookie_reader_unsupported_message),
                        tone = WebSessionBrowserMenuTone.PLUGINS,
                    )
                state.errorMessage != null ->
                    WebSessionEmptyState(
                        icon = Icons.Filled.Lock,
                        title = stringResource(R.string.web_session_cookie_reader_error_title),
                        message = state.errorMessage,
                        tone = WebSessionBrowserMenuTone.PLUGINS,
                    )
                header == null ->
                    WebSessionEmptyState(
                        icon = Icons.Filled.Refresh,
                        title = stringResource(R.string.web_session_cookie_reader_not_loaded_title),
                        message = stringResource(R.string.web_session_cookie_reader_not_loaded_message),
                        tone = WebSessionBrowserMenuTone.PLUGINS,
                    )
                cookies.isEmpty() ->
                    WebSessionEmptyState(
                        icon = Icons.Filled.Lock,
                        title = stringResource(R.string.web_session_cookie_reader_empty_title),
                        message = stringResource(R.string.web_session_cookie_reader_empty_message),
                        tone = WebSessionBrowserMenuTone.PLUGINS,
                    )
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.web_session_cookie_reader_count,
                                cookies.size,
                                cookies.size,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("web_cookie", header))
                                Toast.makeText(
                                    context,
                                    R.string.web_session_cookie_reader_copied,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.web_session_cookie_reader_copy_all),
                            )
                        }
                    }

                    cookies.forEach { cookie ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = WebSessionBrowserPopupShape,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = cookie.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                SelectionContainer {
                                    Text(
                                        text = cookie.value,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.web_session_cookie_reader_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
