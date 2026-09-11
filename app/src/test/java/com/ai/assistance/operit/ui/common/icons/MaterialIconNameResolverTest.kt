package com.ai.assistance.operit.ui.common.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Psychology
import org.junit.Assert.assertSame
import org.junit.Test

class MaterialIconNameResolverTest {
    @Test
    fun `outlined and filled resolution keep separate cached glyphs`() {
        assertSame(Icons.Filled.Psychology, MaterialIconNameResolver.resolveOrNull("psychology"))
        assertSame(Icons.Outlined.Psychology, MaterialIconNameResolver.resolveOutlinedOrDefault("psychology", Icons.Outlined.Hub))
        assertSame(Icons.Filled.Psychology, MaterialIconNameResolver.resolveOrNull("psychology"))
        assertSame(Icons.Outlined.Psychology, MaterialIconNameResolver.resolveOutlinedOrDefault("psychology", Icons.Outlined.Hub))
    }

    @Test
    fun `unknown or empty names retain the supplied outlined icon`() {
        listOf(null, "", "  ", "not_a_real_kiyori_icon").forEach { name ->
            assertSame(Icons.Outlined.Hub, MaterialIconNameResolver.resolveOutlinedOrDefault(name, Icons.Outlined.Hub))
        }
    }
}
