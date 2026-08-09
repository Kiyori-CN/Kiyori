package com.ai.assistance.operit.ui.features.websession.browser.chrome

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.KiyoriDraggableBottomDrawer
import com.ai.assistance.operit.ui.components.resolveKiyoriBottomDrawerContentViewportHeight

@Composable
internal fun WebSessionBrowserBottomDrawer(
    isVisible: Boolean,
    layout: WebSessionBrowserChromeLayout,
    onDismissRequest: () -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    KiyoriDraggableBottomDrawer(
        isVisible = isVisible,
        partialVisibleFraction = layout.drawerPartialFraction,
        onDismissRequest = onDismissRequest,
        onHidden = onHidden,
        modifier = modifier,
        content = content,
    )
}

internal fun resolveWebSessionBrowserDrawerContentViewportHeight(
    drawerHeightDp: Float,
    offsetFraction: Float,
): Float {
    return resolveKiyoriBottomDrawerContentViewportHeight(
        drawerHeightDp = drawerHeightDp,
        offsetFraction = offsetFraction,
    )
}
