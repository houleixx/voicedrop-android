package com.baixingai.voicedrop.core;

/** Local reading preference shared by community and public articles. */
public final class ReadingFontScale {
    private static final float[] VALUES = {0.9f, 1f, 1.15f, 1.3f, 1.5f};
    private static final String[] LABELS = {"小", "标准", "大", "特大", "超大"};
    private ReadingFontScale() {}
    public static float normalize(float value) {
        for (float level : VALUES) if (level == value) return level;
        return 1f;
    }
    public static int index(float value) {
        float normalized = normalize(value);
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i] == normalized) return i;
        return 1;
    }
    public static int percent(float value) { return Math.round(normalize(value) * 100); }
    public static int count() { return VALUES.length; }
    public static float value(int index) { return VALUES[index]; }
    public static String label(int index) { return LABELS[index]; }
}
