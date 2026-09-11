package com.ai.assistance.operit.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ai.assistance.operit.R
import com.kiyori.design.theme.rememberKiyoriUiTokens

/** 同一个品牌标题，避免文件抽屉与 AI 抽屉的字重、字号再次漂移。 */
@Composable
fun KiyoriDrawerTitle(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.kiyori_ai_drawer_title),
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        color = rememberKiyoriUiTokens().colors.primaryText,
        fontWeight = FontWeight.Bold,
    )
}
