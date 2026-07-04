package com.baixingai.voicedrop.share;

public final class ShareExtraction {
    private ShareExtraction() {}

    public static String firstLineTitle(String text, String fallback) {
        String value = text == null ? "" : text;
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) return cap(trimmed, 40);
        }
        return cap(fallback == null || fallback.trim().isEmpty() ? "分享内容" : fallback.trim(), 40);
    }

    public static String cap(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
