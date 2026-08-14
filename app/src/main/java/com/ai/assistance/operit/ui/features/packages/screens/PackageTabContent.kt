package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.ui.features.packages.components.EmptyState

private data class PackageListEntry(
    val packageName: String,
    val displayName: String,
    val description: String,
    val categoryKey: String,
    val categoryLabel: String,
)

@Composable
fun PackageTabContent(
    packages: Map<String, ToolPackage>,
    enabledPackageNames: List<String>,
    isLoading: Boolean,
    isSearchActive: Boolean,
    onPackageClick: (String) -> Unit,
    onTogglePackage: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val enabledPackageNameSet = remember(enabledPackageNames) { enabledPackageNames.toSet() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (packages.isEmpty() && isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                shape = MaterialTheme.shapes.medium
            ) {
                val orderedPackages =
                    remember(packages, context) {
                        packages.map { (packageName, toolPackage) ->
                            val categoryLabel =
                                normalizePackageCategoryLabel(toolPackage.category)
                            val packageDisplayName =
                                toolPackage
                                    .displayName
                                    .resolve(context)
                                    .trim()
                                    .takeIf { displayName -> displayName.isNotBlank() }
                            PackageListEntry(
                                packageName = packageName,
                                displayName =
                                    packageDisplayName ?: toolPackage.name.ifBlank { packageName },
                                description = toolPackage.description.resolve(context),
                                categoryKey = packageCategoryKey(categoryLabel),
                                categoryLabel = categoryLabel,
                            )
                        }
                            .sortedWith(
                                packageCategoryAndDisplayNameComparator(
                                    categorySelector = PackageListEntry::categoryLabel,
                                    displayNameSelector = PackageListEntry::displayName,
                                    internalNameSelector = PackageListEntry::packageName,
                                ),
                            )
                    }
                val groupedPackages = orderedPackages.groupBy(PackageListEntry::categoryKey)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                ) {
                    if (packages.isEmpty()) {
                        item(key = "empty_packages_state") {
                            EmptyState(
                                message =
                                    context.getString(
                                        if (isSearchActive) {
                                            R.string.no_matching_packages_found
                                        } else {
                                            R.string.no_script_packages_available
                                        }
                                    )
                            )
                        }
                    }

                    groupedPackages.forEach { (_, packagesInCategory) ->
                        val category = packagesInCategory.first().categoryLabel
                        val categoryVisual = resolvePackageCategoryVisual(category)
                        val firstPackageName = packagesInCategory.first().packageName

                        items(
                            items = packagesInCategory,
                            key = PackageListEntry::packageName,
                        ) { packageEntry ->
                            val isFirstInCategory =
                                packageEntry.packageName == firstPackageName

                            PackageListItemWithTag(
                                packageEntry = packageEntry,
                                isImported =
                                    packageEntry.packageName in enabledPackageNameSet,
                                categoryTag = if (isFirstInCategory) category else null,
                                categoryVisual = categoryVisual,
                                onPackageClick = {
                                    onPackageClick(packageEntry.packageName)
                                },
                                onToggleImport = { isChecked ->
                                    onTogglePackage(packageEntry.packageName, isChecked)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && packages.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PackageListItemWithTag(
    packageEntry: PackageListEntry,
    isImported: Boolean,
    categoryTag: String?,
    categoryVisual: PackageCategoryVisual,
    onPackageClick: () -> Unit,
    onToggleImport: (Boolean) -> Unit
) {
    val categoryColors = categoryVisual.resolveColors()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (categoryTag != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .height(12.dp),
                    color = categoryColors.icon,
                    shape = RoundedCornerShape(1.5.dp)
                ) {}
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = categoryVisual.icon.toImageVector(),
                    contentDescription = null,
                    tint = categoryColors.icon,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = categoryTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColors.icon,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            onClick = onPackageClick,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (categoryTag != null) 4.dp else 8.dp
                        ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PackageCategoryIconBadge(
                    visual = categoryVisual,
                    contentDescription = null,
                    containerSize = 34.dp,
                    iconSize = 19.dp,
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = packageEntry.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (packageEntry.description.isNotBlank()) {
                        Text(
                            text = packageEntry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isImported,
                    onCheckedChange = onToggleImport,
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}
