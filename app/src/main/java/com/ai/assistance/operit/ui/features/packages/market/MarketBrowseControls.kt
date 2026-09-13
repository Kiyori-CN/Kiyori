package com.ai.assistance.operit.ui.features.packages.market

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.Check

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

@Composable
fun MarketBrowseControls(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    sortOption: MarketSortOption,
    onSortOptionChanged: (MarketSortOption) -> Unit,
    @StringRes searchPlaceholderRes: Int,
    sortOptions: List<MarketSortOption> = MarketSortOption.entries,
    featuredOnly: Boolean = true,
    onFeaturedOnlyChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sortOptions.forEach { option ->
            FilterChip(
                selected = sortOption == option,
                onClick = { onSortOptionChanged(option) },
                modifier = Modifier.heightIn(min = 32.dp),
                label = { Text(stringResource(option.labelRes), style = MaterialTheme.typography.labelSmall) }
            )
        }
        FilterChip(
            selected = featuredOnly,
            onClick = { onFeaturedOnlyChanged(!featuredOnly) },
            modifier = Modifier.heightIn(min = 32.dp),
            leadingIcon = {
                if (featuredOnly) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            },
            label = { Text(stringResource(R.string.market_filter_featured_only), style = MaterialTheme.typography.labelSmall) }
        )
    }
}

@Composable
fun MarketStatsSummary(
    downloads: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.market_stat_downloads_short, downloads),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
