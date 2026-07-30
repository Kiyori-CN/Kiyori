package com.ai.assistance.operit.integrations.http

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class WebThemeSnapshotSchemaTest {
    @Test
    fun `root theme snapshot exposes mode and AI local appearance without global colors`() {
        val rootFields = WebThemeSnapshot.serializer().descriptor.elementNames()

        assertTrue(rootFields.containsAll(listOf("theme_mode", "use_system_theme", "palette")))
        assertTrue(
            rootFields.containsAll(
                listOf("background", "header", "input", "font", "bubble", "avatars"),
            ),
        )
        assertFalse("use_custom_colors" in rootFields)
        assertFalse("primary_color" in rootFields)
        assertFalse("secondary_color" in rootFields)
    }

    @Test
    fun `fixed resolved colors remain inside the palette object`() {
        val paletteFields = WebThemePalette.serializer().descriptor.elementNames()

        assertTrue("primary_color" in paletteFields)
        assertTrue("secondary_color" in paletteFields)
        assertTrue("background_color" in paletteFields)
    }

    private fun kotlinx.serialization.descriptors.SerialDescriptor.elementNames(): Set<String> =
        (0 until elementsCount).map(::getElementName).toSet()
}
