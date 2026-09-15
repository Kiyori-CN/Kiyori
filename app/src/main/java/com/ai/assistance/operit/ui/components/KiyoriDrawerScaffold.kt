package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/**
 * 底部抽屉的统一骨架：标题行与操作区固定，只有正文滚动。
 * 共享抽屉宿主已经消费过系统栏和键盘 inset 并限定了可见视口，这里不再重复避让，
 * 所以操作按钮在初始高度、半展开、完全展开和任意滚动位置都留在屏内可点。
 *
 * @param scrollableContent 正文自带滚动容器（如 LazyColumn）时传 false，并在其上加 Modifier.weight(1f)。
 */
@Composable
internal fun ColumnScope.KiyoriDrawerScaffold(
    title: String,
    onClose: () -> Unit,
    footer: @Composable () -> Unit = {},
    scrollableContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
    }
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth()
            .then(if (scrollableContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .then(if (scrollableContent) Modifier.padding(horizontal = 20.dp, vertical = 12.dp) else Modifier),
        verticalArrangement = if (scrollableContent) Arrangement.spacedBy(12.dp) else Arrangement.Top,
        content = content,
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) { footer() }
}
