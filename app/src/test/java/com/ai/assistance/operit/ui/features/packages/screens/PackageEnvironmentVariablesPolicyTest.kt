package com.ai.assistance.operit.ui.features.packages.screens

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
                values = mapOf("DRAW_KEY" to "configured"),
            ),
        )
        assertEquals(
            emptySet<String>(),
            initialExpandedPackageNames(
                groups,
                values = mapOf("DRAW_KEY" to "configured", "SEARCH_KEY" to "configured"),
            ),
        )
    }

    @Test
    fun `duplicate declarations share one sorted variable name`() {
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
            listOf("SEARCH_KEY", "SHARED_KEY"),
            distinctPackageEnvironmentVariableNames(groups),
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
    ): PackageEnvironmentVariableItem =
        PackageEnvironmentVariableItem(
            name = name,
            description = description,
            required = required,
            defaultValue = null,
        )
}
