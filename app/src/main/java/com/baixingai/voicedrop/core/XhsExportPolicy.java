package com.baixingai.voicedrop.core;

/** Shared limits for a Xiaohongshu post's album payload. */
public final class XhsExportPolicy {
    public static final int MAX_IMAGES = 9;

    private XhsExportPolicy() {}

    /** Number of generated text cards that may fill the remaining image slots. */
    public static int generatedCardSlots(int originalImageCount) {
        int originals = Math.max(0, Math.min(MAX_IMAGES, originalImageCount));
        return MAX_IMAGES - originals;
    }
}
