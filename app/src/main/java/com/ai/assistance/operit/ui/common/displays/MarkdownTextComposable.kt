package com.ai.assistance.operit.ui.common.displays

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.stream

/** 新一代流式Markdown+LaTeX渲染器，完全替换原有实现。 兼容原有API，支持所有Markdown和LaTeX混排。 */
@Composable
fun MarkdownTextComposable(
        text: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        fontSize: TextUnit = Unspecified,
        textAlign: TextAlign? = null,
        isSelectable: Boolean = true, // 保留参数，暂不处理
        onLinkClicked: ((String) -> Unit)? = null,
        enableDialogs: Boolean = true
) {
        // 直接使用新的、基于字符串的渲染器，以获得更好的性能
        val context = LocalContext.current
        StreamMarkdownRenderer(
                content = text,
                modifier = modifier,
                textColor = textColor,
                fontSize = fontSize,
                enableDialogs = enableDialogs,
                onLinkClick = onLinkClicked ?: { url -> openMarkdownLink(context, url) }
        )
}

private const val MARKDOWN_LINK_TAG = "MarkdownLink"

// Repository Markdown can contain document-relative references. Only absolute URI targets belong
// to Android's intent resolver; rejected targets remain observable to both logs and the user.
private fun openMarkdownLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme.isNullOrEmpty()) {
        AppLogger.w(MARKDOWN_LINK_TAG, "markdown link has no URI scheme: $url")
        Toast.makeText(context, R.string.markdown_link_not_openable, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (error: ActivityNotFoundException) {
        AppLogger.w(MARKDOWN_LINK_TAG, "no activity can open markdown link: $url", error)
        Toast.makeText(context, R.string.markdown_link_not_openable, Toast.LENGTH_SHORT).show()
    } catch (error: SecurityException) {
        AppLogger.w(MARKDOWN_LINK_TAG, "not allowed to open markdown link: $url", error)
        Toast.makeText(context, R.string.markdown_link_not_openable, Toast.LENGTH_SHORT).show()
    }
}
