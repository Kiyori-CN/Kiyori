package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.core.tools.EnvVarConsumer
import com.ai.assistance.operit.core.tools.EnvVarInputType
import com.ai.assistance.operit.core.tools.EnvVarScope

internal data class PackageEnvironmentVariableKey(
    val packageName: String,
    val variableName: String,
    val scope: EnvVarScope,
) {
    init {
        require(variableName.isNotBlank()) { "Environment variable name must not be blank" }
        if (scope == EnvVarScope.PACKAGE) {
            require(packageName.isNotBlank()) {
                "Package-scoped environment variables require a package name"
            }
        }
    }

    val ownerPackageName: String?
        get() = packageName.takeIf { scope == EnvVarScope.PACKAGE }

    companion object {
        fun global(variableName: String): PackageEnvironmentVariableKey =
            PackageEnvironmentVariableKey(
                packageName = "",
                variableName = variableName,
                scope = EnvVarScope.GLOBAL,
            )

        fun packageScoped(
            packageName: String,
            variableName: String,
        ): PackageEnvironmentVariableKey =
            PackageEnvironmentVariableKey(
                packageName = packageName,
                variableName = variableName,
                scope = EnvVarScope.PACKAGE,
            )
    }
}

internal data class PackageEnvironmentVariableItem(
    val key: PackageEnvironmentVariableKey,
    val name: String,
    val description: String,
    val required: Boolean,
    val defaultValue: String?,
    val sensitive: Boolean,
    val consumer: EnvVarConsumer,
    val inputType: EnvVarInputType,
    val allowedValues: List<String>,
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
    values: Map<PackageEnvironmentVariableKey, String>,
): Set<String> =
    groups
        .filter { group ->
            group.variables.any { variable ->
                variable.required && values[variable.key].isNullOrBlank()
            }
        }
        .mapTo(linkedSetOf()) { group -> group.packageName }

internal fun distinctPackageEnvironmentVariableKeys(
    groups: List<PackageEnvironmentVariableGroup>,
): List<PackageEnvironmentVariableKey> =
    groups
        .asSequence()
        .flatMap { group -> group.variables.asSequence() }
        .map { variable -> variable.key }
        .distinct()
        .sortedWith(
            compareBy<PackageEnvironmentVariableKey>(
                { key -> key.scope.ordinal },
                { key -> key.packageName },
                { key -> key.variableName },
            ),
        )
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
