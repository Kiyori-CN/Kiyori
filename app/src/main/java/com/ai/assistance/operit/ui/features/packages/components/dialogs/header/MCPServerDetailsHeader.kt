package com.ai.assistance.operit.ui.features.packages.components.dialogs.header

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPLocalServer

@Composable
fun MCPServerDetailsHeader(server: MCPLocalServer.PluginMetadata, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(ImageRequest.Builder(context).data(server.logoUrl).size(144).build())
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (painter.state is AsyncImagePainter.State.Success) {
            Image(painter, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (server.author.isNotBlank() && server.author != "Unknown") {
                Text(stringResource(R.string.mcp_plugin_author, server.author), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (server.version.isNotBlank()) Text("v${server.version}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.pkg_close))
        }
    }
}
