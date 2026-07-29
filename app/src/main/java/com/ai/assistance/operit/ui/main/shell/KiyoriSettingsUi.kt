package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val KIYORI_SETTINGS_ROW_VERTICAL_PADDING_DP = 16
internal const val KIYORI_SETTINGS_SELECTION_CORNER_RADIUS_DP = 26
internal const val KIYORI_SETTINGS_SELECTION_OPTION_MIN_HEIGHT_DP = 56
internal const val KIYORI_SETTINGS_SELECTION_OPTION_VERTICAL_PADDING_DP = 13
internal const val KIYORI_SETTINGS_SELECTION_CHECK_ICON_SIZE_DP = 21
internal const val KIYORI_SETTINGS_SELECTION_CANCEL_VERTICAL_PADDING_DP = 17

internal enum class KiyoriSettingsRowKind {
    NAVIGATION,
    TOGGLE,
}

internal data class KiyoriSettingsSelectionOption(
    val label: String,
    val description: String? = null,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

internal data class KiyoriSettingsSelection(
    val title: String,
    val currentValue: String,
    val options: List<KiyoriSettingsSelectionOption>,
)

@Composable
internal fun KiyoriSettingsGroupSection(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
        Text(
            text = title,
            color = Color(0xFF35332F),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            text = description,
            color = Color(0xFF8B8882),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
        )
        KiyoriSettingsGroupCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
            content = content,
        )
    }
}

@Composable
internal fun KiyoriSettingsRow(
    title: String,
    description: String,
    kind: KiyoriSettingsRowKind,
    value: String? = null,
    checked: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.42f)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(
                    start = 18.dp,
                    end = 14.dp,
                    top = KIYORI_SETTINGS_ROW_VERTICAL_PADDING_DP.dp,
                    bottom = KIYORI_SETTINGS_ROW_VERTICAL_PADDING_DP.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF292825),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Color(0xFF8C8984),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        when (kind) {
            KiyoriSettingsRowKind.NAVIGATION -> {
                value?.let { currentValue ->
                    Text(
                        text = currentValue,
                        fontSize = 12.sp,
                        color = Color(0xFF77736E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFFB4B0AA),
                    modifier = Modifier.size(18.dp),
                )
            }
            KiyoriSettingsRowKind.TOGGLE ->
                Switch(
                    checked = checked,
                    onCheckedChange = { if (enabled) onClick() },
                    enabled = enabled,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF667EEA),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFD5D2CC),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    modifier = Modifier.padding(start = 12.dp),
                )
        }
    }
}

@Composable
internal fun KiyoriSettingsDivider() {
    HorizontalDivider(color = Color(0xFFF0EFEB), thickness = 0.6.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KiyoriSettingsSelectionSheet(
    selection: KiyoriSettingsSelection,
    onDismiss: () -> Unit,
    onSelect: (KiyoriSettingsSelectionOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape =
            RoundedCornerShape(
                topStart = KIYORI_SETTINGS_SELECTION_CORNER_RADIUS_DP.dp,
                topEnd = KIYORI_SETTINGS_SELECTION_CORNER_RADIUS_DP.dp,
            ),
        containerColor = Color.White,
        scrimColor = Color(0x73000000),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = selection.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
            )
            Text(
                text = "当前：${selection.currentValue}",
                color = Color(0xFF85817B),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            )
            HorizontalDivider(color = Color(0xFFEFEDE9))
            selection.options.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = KIYORI_SETTINGS_SELECTION_OPTION_MIN_HEIGHT_DP.dp)
                            .clickable { onSelect(option) }
                            .padding(
                                horizontal = 22.dp,
                                vertical =
                                    KIYORI_SETTINGS_SELECTION_OPTION_VERTICAL_PADDING_DP.dp,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 14.dp)) {
                        Text(
                            text = option.label,
                            fontSize = 14.sp,
                            fontWeight =
                                if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        option.description?.let { description ->
                            Text(
                                text = description,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = Color(0xFF7E7A74),
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                    if (option.selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color(0xFF667EEA),
                            modifier =
                                Modifier.size(
                                    KIYORI_SETTINGS_SELECTION_CHECK_ICON_SIZE_DP.dp,
                                ),
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFF1EFEC))
            }
            Text(
                text = "取消",
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(
                            vertical =
                                KIYORI_SETTINGS_SELECTION_CANCEL_VERTICAL_PADDING_DP.dp,
                        ),
            )
        }
    }
}
