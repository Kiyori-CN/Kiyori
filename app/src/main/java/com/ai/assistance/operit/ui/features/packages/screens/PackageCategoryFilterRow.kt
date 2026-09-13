package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors

@Composable
internal fun PackageCategoryFilterRow(
    allPackageCount: Int,
    categories: List<PackageCategoryFilterOption>,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PackageCategoryFilterChip(
            label = stringResource(R.string.pkg_env_category_all),
            packageCount = allPackageCount,
            visual = null,
            selected = selectedCategoryKey == null,
            onClick = { onCategorySelected(null) },
        )
        categories.forEach { category ->
            PackageCategoryFilterChip(
                label = category.label,
                packageCount = category.packageCount,
                visual = resolvePackageCategoryVisual(category.label),
                selected = selectedCategoryKey == category.key,
                onClick = { onCategorySelected(category.key) },
            )
        }
    }
}

@Composable
private fun PackageCategoryFilterChip(
    label: String,
    packageCount: Int,
    visual: PackageCategoryVisual?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val allColors = KiyoriSemanticTone.CYAN.resolveColors()
    val categoryColors = visual?.resolveColors()
    val accentColor = categoryColors?.icon ?: allColors.icon
    val selectedContainer = categoryColors?.container ?: allColors.container
    Surface(
        modifier = Modifier.heightIn(min = 34.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) selectedContainer else MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (selected) {
                        accentColor
                    } else {
                        accentColor.copy(alpha = 0.34f)
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = visual?.icon?.toImageVector() ?: Icons.Filled.Apps,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.pkg_env_category_count, label, packageCount),
                color = accentColor,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
