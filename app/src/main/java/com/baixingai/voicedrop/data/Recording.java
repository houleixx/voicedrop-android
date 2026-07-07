package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.core.RecordingName;

import java.util.List;

public final class Recording {
    public final String audioName;
    public final String uploaded;
    public final boolean hasArticles;
    public final boolean isEmpty;
    public String articleTitle;
    public boolean uploading;
    public String phase;
    public String blockReason;
    public List<String> tags;

    public Recording(String audioName, String uploaded, boolean hasArticles, boolean isEmpty) {
        this.audioName = audioName;
        this.uploaded = uploaded == null ? "" : uploaded;
        this.hasArticles = hasArticles;
        this.isEmpty = isEmpty;
    }

    public String stem() {
        return audioName.endsWith(".m4a") ? audioName.substring(0, audioName.length() - 4) : audioName;
    }

    public String articleKey() { return articleKey(stem()); }
    public String emptyKey() { return emptyKey(stem()); }
    public String srtKey() { return srtKey(stem()); }
    public String blockedKey() { return blockedKey(stem()); }
    public String tagsKey() { return tagsKey(stem()); }

    public static String articleKey(String stem) { return "articles/" + stem + ".json"; }
    public static String emptyKey(String stem) { return "articles/" + stem + ".empty"; }
    public static String srtKey(String stem) { return "articles/" + stem + ".srt"; }
    public static String blockedKey(String stem) { return "articles/" + stem + ".blocked"; }
    public static String tagsKey(String stem) { return "articles/" + stem + ".tags"; }

    public String rowTitle() {
        if (articleTitle != null && !articleTitle.isEmpty()) return articleTitle;
        RecordingName.Parsed parsed = RecordingName.parse(stem());
        if (parsed == null) return stem();

        // Build human-readable label like iOS: "周三下午" or "浦东新区"
        StringBuilder sb = new StringBuilder();
        // Day of week (Chinese)
        String weekdayZh = weekdayToChinese(parsed.sessionTs);
        if (weekdayZh != null) sb.append(weekdayZh);

        // Period (Chinese)
        String periodZh = periodFromStem(stem());
        if (periodZh != null) {
            if (sb.length() > 0) sb.append("·");
            sb.append(periodZh);
        }

        // Place (if available)
        if (parsed.place != null && !parsed.place.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(parsed.place);
        }

        return sb.length() > 0 ? sb.toString() : stem();
    }

    private static String weekdayToChinese(String sessionTs) {
        // sessionTs format: "yyyy-MM-dd-HHmmss"
        if (sessionTs == null || sessionTs.length() < 10) return null;
        try {
            int month = Integer.parseInt(sessionTs.substring(5, 7));
            int day = Integer.parseInt(sessionTs.substring(8, 10));
            java.time.LocalDate date = java.time.LocalDate.of(
                    Integer.parseInt(sessionTs.substring(0, 4)), month, day);
            String[] days = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
            return days[date.getDayOfWeek().getValue() % 7];
        } catch (Exception e) {
            return null;
        }
    }

    private static String periodFromStem(String stem) {
        String[] parts = stem.split("-");
        // Period is at position 7: Afternoon, Morning, etc.
        // VoiceDrop(0)-2026(1)-06(2)-18(3)-143052(4)-0m33s(5)-Thu(6)-Afternoon(7)
        if (parts.length > 7) {
            String period = parts[7];
            switch (period) {
                case "EarlyMorning": return "清晨";
                case "Morning": return "上午";
                case "Noon": return "中午";
                case "Afternoon": return "下午";
                case "Evening": return "傍晚";
                case "Night": return "晚上";
                case "LateNight": return "深夜";
            }
        }
        return null;
    }

    public String statusLabel() {
        if (uploading) return "正在上传";
        if (hasArticles) return "已成文";
        if (isEmpty) return "无语音";
        if ("asr".equals(phase)) return "听录音";
        if ("mining".equals(phase)) return "挖文章";
        if ("too-long".equals(blockReason)) return "录音过长";
        if ("no-credit".equals(blockReason)) return "余额不足";
        return "待处理";
    }

    public String durationLabel() {
        RecordingName.Parsed parsed = RecordingName.parse(stem());
        return parsed != null && parsed.duration != null ? parsed.duration : "";
    }
}
