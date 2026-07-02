package com.baixingai.voicedrop;

import com.baixingai.voicedrop.ui.PopupMenuPosition;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PopupMenuPositionTest {
    @Test
    public void dropdownOffsetRightAlignsMenuToAnchor() {
        assertEquals(-212, PopupMenuPosition.rightAlignedXOffset(48, 260));
        assertEquals(-70, PopupMenuPosition.upwardYOffset(70));
    }
}
