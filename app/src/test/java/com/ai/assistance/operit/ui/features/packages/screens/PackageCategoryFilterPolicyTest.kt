package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.ToolPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageCategoryFilterPolicyTest {
    private fun script(name: String, category: String) = ToolPackage(
        name = name, category = category, description = LocalizedText.of(""), tools = emptyList(),
    )

    @Test
    fun categoriesNormalizeCaseWhitespaceAndMissingLabels() {
        val categories = buildScriptPackageCategories(linkedMapOf(
            "draw1" to script("draw1", "draw"),
            "draw2" to script("draw2", " Draw "),
            "other" to script("other", "  "),
            "academic" to script("academic", "Academic"),
        ))
        assertEquals(listOf("Academic", "Draw", "Other"), categories.map { it.label })
        assertEquals(listOf(1, 2, 1), categories.map { it.packageCount })
        assertEquals(3, categories.map { it.key }.distinct().size)
    }

    @Test
    fun categoryIntersectsSearchResultsWithoutReplacingPackageIdentity() {
        val draw = script("draw", "DRAW")
        val searchResults = linkedMapOf("draw" to draw, "network" to script("network", "Network"))
        val filtered = filterScriptPackagesByCategory(searchResults, packageCategoryKey("Draw"))
        assertEquals(setOf("draw"), filtered.keys)
        assertSame(draw, filtered["draw"])
        assertSame(searchResults, filterScriptPackagesByCategory(searchResults, null))
    }

    @Test
    fun emptySearchOrMissingCategoryDoesNotLeakOtherScripts() {
        assertTrue(filterScriptPackagesByCategory(emptyMap(), packageCategoryKey("Draw")).isEmpty())
        assertTrue(filterScriptPackagesByCategory(mapOf("draw" to script("draw", "Draw")), "missing").isEmpty())
        assertTrue(buildScriptPackageCategories(emptyMap()).isEmpty())
    }
}
