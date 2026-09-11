package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerScrollThumb
import org.junit.Assert.*
import org.junit.Test

class FileManagerScrollIndicatorTest {
    @Test fun `non scrolling and not yet measured lists have no thumb`() {
        assertNull(fileManagerScrollThumb(100, 200, 0, 500f, 24f, false, false))
        assertNull(fileManagerScrollThumb(0, 200, 0, 500f, 24f, false, true))
        assertNull(fileManagerScrollThumb(1000, 200, -1, 500f, 24f, false, true))
        assertNull(fileManagerScrollThumb(1000, 200, 0, 0f, 24f, false, true))
    }
    @Test fun `middle position uses fixed length and remaining travel`() {
        val thumb = fileManagerScrollThumb(1000, 200, 400, 500f, 24f, true, true)!!
        assertEquals(24f, thumb.height, 0.001f)
        assertEquals(238f, thumb.top, 0.001f)
    }
    @Test fun `estimated content and viewport changes never resize the thumb`() {
        listOf(800 to 200, 1200 to 220, 50000 to 400).forEach { (content, viewport) ->
            assertEquals(24f, fileManagerScrollThumb(content, viewport, 100, 500f, 24f, true, true)!!.height, 0.001f)
        }
    }
    @Test fun `first and last positions pin to track despite estimated offsets`() {
        assertEquals(0f, fileManagerScrollThumb(1000, 200, 50, 500f, 24f, false, true)!!.top, 0.001f)
        val end = fileManagerScrollThumb(1000, 200, 750, 500f, 24f, true, false)!!
        assertEquals(500f, end.top + end.height, 0.001f)
    }
    @Test fun `huge directory keeps minimum thumb within track`() {
        val thumb = fileManagerScrollThumb(Int.MAX_VALUE, 200, Int.MAX_VALUE, 500f, 24f, true, true)!!
        assertEquals(24f, thumb.height, 0.001f)
        assertEquals(500f, thumb.top + thumb.height, 0.001f)
    }
    @Test fun `very short tracks never overflow minimum thumb`() {
        val thumb = fileManagerScrollThumb(1000, 200, 400, 12f, 24f, true, true)!!
        assertEquals(12f, thumb.height, 0.001f)
        assertEquals(0f, thumb.top, 0.001f)
    }
}
