package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.InlineEditorViewport;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class InlineEditorViewportTest {
    @Test public void revealsEntireBorderRatherThanOnlyTheCursor() {
        assertEquals(54, InlineEditorViewport.scrollDelta(80, 400, 350, 180, 4));
    }
    @Test public void visibleEditorDoesNotMove() {
        assertEquals(0, InlineEditorViewport.scrollDelta(80, 400, 100, 180, 4));
    }
    @Test public void restoresTopAfterKeyboardResize() {
        assertEquals(-34, InlineEditorViewport.scrollDelta(80, 400, 50, 180, 4));
    }
    @Test public void cappedLongEditorFitsBetweenBothGaps() {
        assertEquals(0, InlineEditorViewport.scrollDelta(80, 400, 84, 392, 4));
    }
}
