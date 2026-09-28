package com.baixingai.voicedrop.core;

/** Scroll just enough to show the complete editor, including its border. */
public final class InlineEditorViewport {
    private InlineEditorViewport() {}

    public static int scrollDelta(int viewportTop, int viewportHeight,
                                  int fieldTop, int fieldHeight, int gap) {
        int bottomOverflow = fieldTop + fieldHeight - (viewportTop + viewportHeight - gap);
        if (bottomOverflow > 0) return bottomOverflow;
        int topOverflow = fieldTop - (viewportTop + gap);
        return Math.min(0, topOverflow);
    }
}
