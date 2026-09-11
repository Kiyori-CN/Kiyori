package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginCenterFacade

@Composable
internal fun BrowserPluginAddMenu(
    onOpenNewUserscript: () -> Unit,
    onRequestInstallFromUrl: () -> Unit,
    onImportLocal: () -> Unit,
    onOpenLibrarySource: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.web_session_plugins_add),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = WebSessionBrowserPopupShape,
            containerColor = MaterialTheme.colorScheme.surface,
            shadowElevation = WebSessionBrowserPopupElevation,
        ) {
            WebSessionBrowserDropdownItem(
                title = stringResource(R.string.web_session_userscript_new),
                onClick = {
                    expanded = false
                    onOpenNewUserscript()
                },
            )
            WebSessionBrowserDropdownItem(
                title = stringResource(R.string.web_session_userscript_install_from_url),
                onClick = {
                    expanded = false
                    onRequestInstallFromUrl()
                },
            )
            WebSessionBrowserDropdownItem(
                title = stringResource(R.string.web_session_userscript_import_local),
                onClick = {
                    expanded = false
                    onImportLocal()
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.web_session_plugins_library),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            BrowserPluginCenterFacade.librarySources.forEach { source ->
                WebSessionBrowserDropdownItem(
                    title = source.title,
                    onClick = {
                        expanded = false
                        onOpenLibrarySource(source.url)
                    },
                )
            }
        }
    }
}
