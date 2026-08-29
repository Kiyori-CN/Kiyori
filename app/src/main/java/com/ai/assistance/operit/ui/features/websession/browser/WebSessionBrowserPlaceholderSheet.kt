package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserPlaceholderPage
import com.kiyori.design.theme.KiyoriUiShapes

@Composable
internal fun WebSessionBrowserPlaceholderSheet(
    page: WebSessionBrowserPlaceholderPage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPage = page ?: return
    val model = currentPage.placeholderModel()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WebSessionDrawerHeader(
                title = model.title,
                leadingIcon = model.icon,
                tone = model.tone,
                navigationIcon = {
                    BrowserPlaceholderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.web_session_back),
                        onClick = onDismiss,
                    )
                },
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                WebSessionBrowserMenuIconBadge(
                    imageVector = model.icon,
                    tone = model.tone,
                    contentDescription = null,
                    containerSize = 70.dp,
                    iconSize = 34.dp,
                    shape = KiyoriUiShapes.card,
                )
                Text(
                    text = stringResource(R.string.web_session_placeholder_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.web_session_placeholder_runtime_unchanged),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private data class PlaceholderModel(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tone: WebSessionBrowserMenuTone,
)

@Composable
private fun WebSessionBrowserPlaceholderPage.placeholderModel(): PlaceholderModel =
    when (this) {
        WebSessionBrowserPlaceholderPage.TOOLBOX ->
            PlaceholderModel(
                title = stringResource(R.string.web_session_browser_toolbox),
                description = stringResource(R.string.web_session_placeholder_toolbox),
                icon = Icons.Filled.Build,
                tone = WebSessionBrowserMenuTone.TOOLBOX,
            )
    }

@Composable
private fun BrowserPlaceholderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}
