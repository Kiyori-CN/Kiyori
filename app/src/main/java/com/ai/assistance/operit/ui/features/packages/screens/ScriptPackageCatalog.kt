package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import com.ai.assistance.operit.core.tools.ToolPackage

internal data class ScriptPackageCatalogEntry(
    val packageName: String,
    val displayName: String,
    val description: String,
    val categoryKey: String,
    val categoryLabel: String,
    val enabled: Boolean,
)

internal fun buildScriptPackageCatalog(
    packages: Map<String, ToolPackage>,
    enabledPackageNames: Set<String>,
    context: Context,
): List<ScriptPackageCatalogEntry> =
    packages.map { (packageName, toolPackage) ->
        val categoryLabel = normalizePackageCategoryLabel(toolPackage.category)
        val localizedDisplayName =
            toolPackage.displayName.resolve(context).trim().takeIf(String::isNotBlank)
        ScriptPackageCatalogEntry(
            packageName = packageName,
            displayName = localizedDisplayName ?: toolPackage.name.ifBlank { packageName },
            description = toolPackage.description.resolve(context).trim(),
            categoryKey = packageCategoryKey(categoryLabel),
            categoryLabel = categoryLabel,
            enabled = packageName in enabledPackageNames,
        )
    }.sortedWith(
        packageCategoryAndDisplayNameComparator(
            categorySelector = ScriptPackageCatalogEntry::categoryLabel,
            displayNameSelector = ScriptPackageCatalogEntry::displayName,
            internalNameSelector = ScriptPackageCatalogEntry::packageName,
        ),
    )
