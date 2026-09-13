package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.core.tools.ToolPackage

internal data class PackageCategoryFilterOption(
    val key: String,
    val label: String,
    val packageCount: Int,
)

internal fun buildScriptPackageCategories(
    packages: Map<String, ToolPackage>,
): List<PackageCategoryFilterOption> =
    packages.values
        .map { normalizePackageCategoryLabel(it.category) }
        .sortedWith(packageCategoryComparator())
        .groupBy(::packageCategoryKey)
        .map { (key, labels) -> PackageCategoryFilterOption(key, labels.first(), labels.size) }

internal fun filterScriptPackagesByCategory(
    packages: Map<String, ToolPackage>,
    categoryKey: String?,
): Map<String, ToolPackage> =
    if (categoryKey == null) packages
    else packages.filterValues { packageCategoryKey(normalizePackageCategoryLabel(it.category)) == categoryKey }
