package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.core.tools.EnvVarConsumer
import com.ai.assistance.operit.core.tools.EnvVarInputType
import com.ai.assistance.operit.core.tools.EnvVarScope
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageEnvironmentVariablesPolicyTest {
    @Test
    fun `categories merge case variants and keep stable labels`() {
        val groups =
            listOf(
                group("draw-a", "Alpha Draw", "Draw"),
                group("chat-a", "Chat", "Chat"),
                group("draw-b", "Beta Draw", "draw"),
                group("other-a", "Other", ""),
            )

        assertEquals(
            listOf(
                PackageEnvironmentVariableCategory("chat", "Chat", 1),
                PackageEnvironmentVariableCategory("draw", "Draw", 2),
                PackageEnvironmentVariableCategory("other", "Other", 1),
            ),
            buildPackageEnvironmentCategories(groups),
        )
    }

    @Test
    fun `environment groups sort by category then english or Chinese initial`() {
        val groups =
            listOf(
                group("tavily", "Tavily Search", "Search"),
                group("byte", "字节工具", "Automatic"),
                group("openai", "OpenAI Draw", "Draw"),
                group("ali", "阿里助手", "Automatic"),
                group("baidu", "百度助手", "Automatic"),
            )

        assertEquals(
            listOf("ali", "baidu", "byte", "openai", "tavily"),
            sortPackageEnvironmentVariableGroups(groups).map { group -> group.packageName },
        )
    }

    @Test
    fun `category and variable search narrow packages without searching values`() {
        val groups =
            listOf(
                group(
                    packageName = "openai_draw",
                    displayName = "OpenAI Draw",
                    category = "Draw",
                    variables =
                        listOf(
                            variable("OPENAI_API_KEY", "API credential"),
                            variable("OPENAI_IMAGE_MODEL", "Image model", required = false),
                        ),
                ),
                group(
                    packageName = "tavily",
                    displayName = "Tavily Search",
                    category = "Search",
                    variables = listOf(variable("TAVILY_API_KEY", "Search credential")),
                ),
            )

        assertEquals(
            listOf("openai_draw"),
            filterPackageEnvironmentVariableGroups(
                groups = groups,
                selectedCategoryKey = packageEnvironmentCategoryKey("Draw"),
                query = "",
            ).map { group -> group.packageName },
        )

        val variableMatches =
            filterPackageEnvironmentVariableGroups(
                groups = groups,
                selectedCategoryKey = null,
                query = "image model",
            )
        assertEquals(listOf("openai_draw"), variableMatches.map { group -> group.packageName })
        assertEquals(
            listOf("OPENAI_IMAGE_MODEL"),
            variableMatches.single().variables.map { variable -> variable.name },
        )
    }

    @Test
    fun `package name search keeps every variable in the matched package`() {
        val groups =
            listOf(
                group(
                    packageName = "openai_draw",
                    displayName = "OpenAI Draw",
                    category = "Draw",
                    variables =
                        listOf(
                            variable("OPENAI_API_KEY", "API credential"),
                            variable("OPENAI_IMAGE_MODEL", "Image model", required = false),
                        ),
                ),
            )

        assertEquals(
            listOf("OPENAI_API_KEY", "OPENAI_IMAGE_MODEL"),
            filterPackageEnvironmentVariableGroups(
                groups = groups,
                selectedCategoryKey = null,
                query = "openai",
            ).single().variables.map { variable -> variable.name },
        )
    }

    @Test
    fun `all and only packages with missing required values expand by default`() {
        val groups =
            listOf(
                group(
                    packageName = "draw",
                    displayName = "Draw",
                    category = "Draw",
                    variables = listOf(variable("DRAW_KEY", "Draw key")),
                ),
                group(
                    packageName = "search",
                    displayName = "Search",
                    category = "Search",
                    variables =
                        listOf(
                            variable("SEARCH_KEY", "Search key"),
                            variable("SEARCH_REGION", "Search region", required = false),
                        ),
                ),
                group(
                    packageName = "chat",
                    displayName = "Chat",
                    category = "Chat",
                    variables =
                        listOf(
                            variable("CHAT_STYLE", "Chat style", required = false),
                        ),
                ),
            )

        assertEquals(
            setOf("draw", "search"),
            initialExpandedPackageNames(groups, values = emptyMap()),
        )
        assertEquals(
            setOf("search"),
            initialExpandedPackageNames(
                groups,
                values =
                    mapOf(
                        PackageEnvironmentVariableKey.global("DRAW_KEY") to "configured",
                    ),
            ),
        )
        assertEquals(
            emptySet<String>(),
            initialExpandedPackageNames(
                groups,
                values =
                    mapOf(
                        PackageEnvironmentVariableKey.global("DRAW_KEY") to "configured",
                        PackageEnvironmentVariableKey.global("SEARCH_KEY") to "configured",
                    ),
            ),
        )
    }

    @Test
    fun `duplicate global declarations share one sorted variable key`() {
        val groups =
            listOf(
                group(
                    packageName = "draw",
                    displayName = "Draw",
                    category = "Draw",
                    variables = listOf(variable("SHARED_KEY", "Shared")),
                ),
                group(
                    packageName = "search",
                    displayName = "Search",
                    category = "Search",
                    variables =
                        listOf(
                            variable("SEARCH_KEY", "Search"),
                            variable("SHARED_KEY", "Shared"),
                        ),
                ),
            )

        assertEquals(
            listOf(
                PackageEnvironmentVariableKey.global("SEARCH_KEY"),
                PackageEnvironmentVariableKey.global("SHARED_KEY"),
            ),
            distinctPackageEnvironmentVariableKeys(groups),
        )
    }

    @Test
    fun `package-scoped declarations keep separate owners`() {
        val groups =
            listOf(
                group(
                    packageName = "draw",
                    displayName = "Draw",
                    category = "Draw",
                    variables =
                        listOf(
                            variable(
                                name = "API_KEY",
                                description = "Draw key",
                                packageName = "draw",
                                scope = EnvVarScope.PACKAGE,
                            ),
                        ),
                ),
                group(
                    packageName = "search",
                    displayName = "Search",
                    category = "Search",
                    variables =
                        listOf(
                            variable(
                                name = "API_KEY",
                                description = "Search key",
                                packageName = "search",
                                scope = EnvVarScope.PACKAGE,
                            ),
                        ),
                ),
            )

        assertEquals(
            listOf(
                PackageEnvironmentVariableKey.packageScoped("draw", "API_KEY"),
                PackageEnvironmentVariableKey.packageScoped("search", "API_KEY"),
            ),
            distinctPackageEnvironmentVariableKeys(groups),
        )
    }

    @Test
    fun `global and package-scoped declarations do not share values`() {
        val groups =
            listOf(
                group(
                    packageName = "legacy",
                    displayName = "Legacy",
                    category = "Other",
                    variables = listOf(variable("API_KEY", "Global key")),
                ),
                group(
                    packageName = "host",
                    displayName = "Host",
                    category = "ToolPkg",
                    variables =
                        listOf(
                            variable(
                                name = "API_KEY",
                                description = "Host key",
                                packageName = "host",
                                scope = EnvVarScope.PACKAGE,
                            ),
                        ),
                ),
            )

        assertEquals(
            listOf(
                PackageEnvironmentVariableKey.global("API_KEY"),
                PackageEnvironmentVariableKey.packageScoped("host", "API_KEY"),
            ),
            distinctPackageEnvironmentVariableKeys(groups),
        )
    }

    @Test
    fun `drawer partial height follows browser width classes`() {
        assertEquals(
            0.64f,
            resolvePackageEnvironmentDrawerPartialVisibleFraction(400f, 800f),
            0f,
        )
        assertEquals(
            0.78f,
            resolvePackageEnvironmentDrawerPartialVisibleFraction(500f, 300f),
            0f,
        )
        assertEquals(
            0.68f,
            resolvePackageEnvironmentDrawerPartialVisibleFraction(700f, 900f),
            0f,
        )
        assertEquals(
            0.72f,
            resolvePackageEnvironmentDrawerPartialVisibleFraction(900f, 900f),
            0f,
        )
    }

    private fun group(
        packageName: String,
        displayName: String,
        category: String,
        variables: List<PackageEnvironmentVariableItem> =
            listOf(variable("${packageName.uppercase()}_KEY", "Credential")),
    ): PackageEnvironmentVariableGroup {
        val categoryLabel = normalizePackageEnvironmentCategoryLabel(category)
        return PackageEnvironmentVariableGroup(
            packageName = packageName,
            displayName = displayName,
            categoryKey = packageEnvironmentCategoryKey(categoryLabel),
            categoryLabel = categoryLabel,
            variables = variables,
        )
    }

    private fun variable(
        name: String,
        description: String,
        required: Boolean = true,
        packageName: String = "",
        scope: EnvVarScope = EnvVarScope.GLOBAL,
    ): PackageEnvironmentVariableItem =
        PackageEnvironmentVariableItem(
            key =
                if (scope == EnvVarScope.PACKAGE) {
                    PackageEnvironmentVariableKey.packageScoped(packageName, name)
                } else {
                    PackageEnvironmentVariableKey.global(name)
                },
            name = name,
            description = description,
            required = required,
            defaultValue = null,
            sensitive = scope == EnvVarScope.PACKAGE,
            consumer =
                if (scope == EnvVarScope.PACKAGE) {
                    EnvVarConsumer.HOST_SERVICE
                } else {
                    EnvVarConsumer.JAVASCRIPT
                },
            inputType =
                if (scope == EnvVarScope.PACKAGE) {
                    EnvVarInputType.PASSWORD
                } else {
                    EnvVarInputType.TEXT
                },
            allowedValues = emptyList(),
        )
}
