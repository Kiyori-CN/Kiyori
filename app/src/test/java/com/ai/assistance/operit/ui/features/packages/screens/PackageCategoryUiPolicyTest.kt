package com.ai.assistance.operit.ui.features.packages.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PackageCategoryUiPolicyTest {
    @Test
    fun `preset categories have unique icons and light dark palettes`() {
        val visuals = PACKAGE_CATEGORY_PRESET_LABELS.map(::resolvePackageCategoryVisual)

        assertEquals(visuals.size, visuals.map { visual -> visual.icon }.toSet().size)
        assertEquals(
            visuals.size,
            visuals.map { visual -> visual.palette.lightIcon }.toSet().size,
        )
        assertEquals(
            visuals.size,
            visuals.map { visual -> visual.palette.lightContainer }.toSet().size,
        )
        assertEquals(
            visuals.size,
            visuals.map { visual -> visual.palette.darkIcon }.toSet().size,
        )
        assertEquals(
            visuals.size,
            visuals.map { visual -> visual.palette.darkContainer }.toSet().size,
        )
        assertEquals(visuals.size, visuals.map { visual -> visual.palette }.toSet().size)
    }

    @Test
    fun `preset category icons match their semantic type`() {
        val expectedIcons =
            mapOf(
                "Automatic" to PackageCategoryIcon.AUTO_MODE,
                "Chat" to PackageCategoryIcon.FORUM,
                "Development" to PackageCategoryIcon.CODE,
                "Draw" to PackageCategoryIcon.PALETTE,
                "Experimental" to PackageCategoryIcon.SCIENCE,
                "File" to PackageCategoryIcon.FOLDER,
                "Life" to PackageCategoryIcon.FAVORITE,
                "Media" to PackageCategoryIcon.PLAY_CIRCLE,
                "Memory" to PackageCategoryIcon.MEMORY,
                "Network" to PackageCategoryIcon.LANGUAGE,
                "Other" to PackageCategoryIcon.WIDGETS,
                "Search" to PackageCategoryIcon.SEARCH,
                "System" to PackageCategoryIcon.SETTINGS,
                "ToolPkg" to PackageCategoryIcon.EXTENSION,
                "UI_AUTOMATION" to PackageCategoryIcon.SMART_TOY,
                "Utility" to PackageCategoryIcon.TUNE,
                "Workflow" to PackageCategoryIcon.ACCOUNT_TREE,
            )

        assertEquals(
            expectedIcons,
            PACKAGE_CATEGORY_PRESET_LABELS.associateWith { category ->
                resolvePackageCategoryVisual(category).icon
            },
        )
    }

    @Test
    fun `known category case variants use one canonical identity`() {
        val canonical = resolvePackageCategoryVisual("Draw")
        val caseVariant = resolvePackageCategoryVisual("  draw ")

        assertEquals("Draw", caseVariant.categoryLabel)
        assertEquals(canonical.icon, caseVariant.icon)
        assertEquals(canonical.palette, caseVariant.palette)
    }

    @Test
    fun `custom category identity is stable and distinct by category key`() {
        val research = resolvePackageCategoryVisual("Research")
        val researchCaseVariant = resolvePackageCategoryVisual(" research ")
        val translation = resolvePackageCategoryVisual("Translation")

        assertEquals(PackageCategoryIcon.APPS, research.icon)
        assertEquals(research.palette, researchCaseVariant.palette)
        assertNotEquals(research.palette, translation.palette)
    }

    @Test
    fun `category labels sort from A to Z without manual priority`() {
        val categories =
            listOf("workflow", "Draw", "automatic", "Search", "chat", "Other")
                .sortedWith(packageCategoryComparator())

        assertEquals(
            listOf("automatic", "chat", "Draw", "Other", "Search", "workflow"),
            categories,
        )
    }

    @Test
    fun `english and Chinese names expose one A to Z initial contract`() {
        assertEquals('A', packageNameInitialLetter("Apple"))
        assertEquals('E', packageNameInitialLetter("Éclair"))
        assertEquals('A', packageNameInitialLetter("阿里搜索"))
        assertEquals('B', packageNameInitialLetter("百度绘图"))
        assertEquals('C', packageNameInitialLetter("长安工具"))
        assertEquals('Z', packageNameInitialLetter("字节工具"))
        assertEquals(null, packageNameInitialLetter("12306"))
    }

    @Test
    fun `package names mix english and Chinese initials in one A to Z sequence`() {
        val packages =
            listOf(
                orderingItem("Tavily Search", "tavily"),
                orderingItem("字节工具", "byte_tools"),
                orderingItem("OpenAI Draw", "openai_draw"),
                orderingItem("阿里搜索", "ali_search"),
                orderingItem("长安工具", "changan"),
                orderingItem("百度绘图", "baidu_draw"),
                orderingItem("12306", "railway"),
            )
        val sorted =
            packages.sortedWith(
                packageDisplayNameComparator(
                    displayNameSelector = OrderingItem::displayName,
                    internalNameSelector = OrderingItem::internalName,
                ),
            )

        assertEquals(
            listOf(
                "阿里搜索",
                "百度绘图",
                "长安工具",
                "OpenAI Draw",
                "Tavily Search",
                "字节工具",
                "12306",
            ),
            sorted.map(OrderingItem::displayName),
        )
    }

    @Test
    fun `internal package name makes equal display names stable`() {
        val packages =
            listOf(
                orderingItem("工具", "z_package"),
                orderingItem("工具", "a_package"),
            )
        val sorted =
            packages.sortedWith(
                packageDisplayNameComparator(
                    displayNameSelector = OrderingItem::displayName,
                    internalNameSelector = OrderingItem::internalName,
                ),
            )

        assertEquals(
            listOf("a_package", "z_package"),
            sorted.map(OrderingItem::internalName),
        )
    }

    private fun orderingItem(
        displayName: String,
        internalName: String,
    ): OrderingItem =
        OrderingItem(
            displayName = displayName,
            internalName = internalName,
        )

    private data class OrderingItem(
        val displayName: String,
        val internalName: String,
    )
}
