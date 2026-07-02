package com.baixingai.voicedrop.ui;

public final class PopupMenuPosition {
    private PopupMenuPosition() {}

    public static int rightAlignedXOffset(int anchorWidth, int popupWidth) {
        return anchorWidth - popupWidth;
    }

    public static int upwardYOffset(int distance) {
        return -distance;
    }
}
