package com.kiyori.design.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriSurfaceTokensTest {
    @Test
    fun `reading surfaces stay opaque and readable in app and browser themes`() {
        listOf(KiyoriLightColorScheme, KiyoriDarkColorScheme, KiyoriBrowserLightColorScheme, KiyoriBrowserDarkColorScheme).forEach { scheme ->
            val surfaces = resolveKiyoriSurfaceColors(scheme)
            listOf(surfaces.card, surfaces.popup, surfaces.sheet).forEach { surface ->
                assertEquals(1f, surface.alpha, 0f)
                val foreground = scheme.onSurface.luminance()
                val background = surface.luminance()
                assertTrue((maxOf(foreground, background) + 0.05f) / (minOf(foreground, background) + 0.05f) >= 4.5f)
            }
        }
    }

    @Test
    fun `modal scrim stays neutral across themes and preserves underlying context`() {
        listOf(KiyoriLightColorScheme, KiyoriDarkColorScheme).forEach { scheme ->
            val scrim = resolveKiyoriSurfaceColors(scheme).modalScrim
            assertTrue(scrim.alpha in 0.25f..0.4f)
            assertEquals(scrim.red, scrim.green, 0f)
            assertEquals(scrim.green, scrim.blue, 0f)
        }
    }
}
