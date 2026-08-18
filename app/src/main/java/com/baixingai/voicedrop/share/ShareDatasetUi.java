package com.baixingai.voicedrop.share;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class ShareDatasetUi {
    public static final int MIN_EXTRACT_CHARS = 300;

    private ShareDatasetUi() {}

    public static int totalChars(List<DatasetItem> items) {
        int total = 0;
        if (items != null) for (DatasetItem item : items) total += Math.max(0, item.chars);
        return total;
    }

    public static String formatChars(int chars) {
        return String.format(Locale.US, "%,d 字", Math.max(0, chars));
    }

    public static String formatTotalChars(int chars) {
        int safe = Math.max(0, chars);
        if (safe >= 10_000) return String.format(Locale.US, "约 %.1f 万字", safe / 10_000.0);
        return "约 " + formatChars(safe);
    }

    public static String typeLabel(String type) {
        if ("web".equals(type)) return "网页";
        if ("doc".equals(type)) return "文档";
        return "文字";
    }

    public static String itemMeta(DatasetItem item) {
        if (item == null) return "";
        return "web".equals(item.type) && item.source != null && !item.source.trim().isEmpty()
                ? item.source.trim() : formatChars(item.chars);
    }

    public static String chineseDate(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            OffsetDateTime date = OffsetDateTime.parse(value);
            return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
        } catch (DateTimeParseException ignored) {
            return "";
        }
    }
}
