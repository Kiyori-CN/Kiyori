package com.ai.assistance.operit.ui.features.packages.screens

internal data class PackageEnvironmentVariableItem(
    val name: String,
    val description: String,
    val required: Boolean,
    val defaultValue: String?,
)

internal data class PackageEnvironmentVariableGroup(
    val packageName: String,
    val displayName: String,
    val categoryKey: String,
    val categoryLabel: String,
    val variables: List<PackageEnvironmentVariableItem>,
)

internal data class PackageEnvironmentVariableCategory(
    val key: String,
    val label: String,
    val packageCount: Int,
)

internal fun normalizePackageEnvironmentCategoryLabel(category: String): String =
    normalizePackageCategoryLabel(category)

internal fun packageEnvironmentCategoryKey(categoryLabel: String): String =
    packageCategoryKey(categoryLabel)

internal fun sortPackageEnvironmentVariableGroups(
    groups: List<PackageEnvironmentVariableGroup>,
): List<PackageEnvironmentVariableGroup> =
    groups.sortedWith(
        packageCategoryAndDisplayNameComparator(
            categorySelector = PackageEnvironmentVariableGroup::categoryLabel,
            displayNameSelector = PackageEnvironmentVariableGroup::displayName,
            internalNameSelector = PackageEnvironmentVariableGroup::packageName,
        ),
    )

internal fun buildPackageEnvironmentCategories(
    groups: List<PackageEnvironmentVariableGroup>,
): List<PackageEnvironmentVariableCategory> {
    val categoryComparator = packageCategoryComparator()
    return groups
        .groupBy { group -> group.categoryKey }
        .map { (key, categoryGroups) ->
            PackageEnvironmentVariableCategory(
                key = key,
                label = categoryGroups.first().categoryLabel,
                packageCount = categoryGroups.size,
            )
        }
        .sortedWith(
            Comparator { left, right ->
                categoryComparator.compare(left.label, right.label)
            },
        )
}

internal fun filterPackageEnvironmentVariableGroups(
    groups: List<PackageEnvironmentVariableGroup>,
    selectedCategoryKey: String?,
    query: String,
): List<PackageEnvironmentVariableGroup> {
    val normalizedQuery = query.trim()
    return groups.mapNotNull { group ->
        if (
            selectedCategoryKey != null &&
                group.categoryKey != selectedCategoryKey
        ) {
            return@mapNotNull null
        }

        if (normalizedQuery.isBlank()) {
            return@mapNotNull group
        }

        val packageMatches =
            group.displayName.contains(normalizedQuery, ignoreCase = true) ||
                group.packageName.contains(normalizedQuery, ignoreCase = true)
        val matchingVariables =
            if (packageMatches) {
                group.variables
            } else {
                group.variables.filter { variable ->
                    variable.name.contains(normalizedQuery, ignoreCase = true) ||
                        variable.description.contains(normalizedQuery, ignoreCase = true)
                }
            }

        group.copy(variables = matchingVariables).takeIf { filtered ->
            filtered.variables.isNotEmpty()
        }
    }
}

internal fun initialExpandedPackageNames(
    groups: List<PackageEnvironmentVariableGroup>,
    values: Map<String, String>,
): Set<String> =
    groups
        .filter { group ->
            group.variables.any { variable ->
                variable.required && values[variable.name].isNullOrBlank()
            }
        }
        .mapTo(linkedSetOf()) { group -> group.packageName }

internal fun distinctPackageEnvironmentVariableNames(
    groups: List<PackageEnvironmentVariableGroup>,
): List<String> =
    groups
        .asSequence()
        .flatMap { group -> group.variables.asSequence() }
        .map { variable -> variable.name }
        .distinct()
        .sorted()
        .toList()

internal fun resolvePackageEnvironmentDrawerPartialVisibleFraction(
    widthDp: Float,
    heightDp: Float,
): Float {
    require(widthDp > 0f) { "widthDp must be positive" }
    require(heightDp > 0f) { "heightDp must be positive" }

    return when {
        widthDp < 600f -> if (widthDp > heightDp) 0.78f else 0.64f
        widthDp < 840f -> 0.68f
        else -> 0.72f
    }
}
