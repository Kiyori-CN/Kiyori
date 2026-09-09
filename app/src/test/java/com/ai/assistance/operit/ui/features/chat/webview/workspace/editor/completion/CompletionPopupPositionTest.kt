package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionPopupPositionTest {
    @Test fun keepsWindowCoordinatesWithoutAddingParentOffset() {
        assertEquals(IntOffset(70, 180), completionPopupPosition(IntOffset(70, 180), IntSize(400, 800), IntSize(260, 220), 700))
    }
    @Test fun movesAboveKeyboardAndClampsRightEdge() {
        assertEquals(IntOffset(140, 280), completionPopupPosition(IntOffset(350, 500), IntSize(400, 800), IntSize(260, 220), 550))
    }
    @Test fun narrowOrShortWindowDoesNotProduceNegativePosition() {
        assertEquals(IntOffset.Zero, completionPopupPosition(IntOffset(-10, -20), IntSize(100, 120), IntSize(260, 220), 100))
    }
}
