package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.core.RecordingName;

public final class Recording {
    public final String audioName;
    public final String uploaded;
    public final boolean hasArticles;
    public final boolean isEmpty;
    public String articleTitle;
    public boolean uploading;
    public String phase;
    public String blockReason;

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

    public static String articleKey(String stem) { return "articles/" + stem + ".json"; }
    public static String emptyKey(String stem) { return "articles/" + stem + ".empty"; }
    public static String srtKey(String stem) { return "articles/" + stem + ".srt"; }
    public static String blockedKey(String stem) { return "articles/" + stem + ".blocked"; }

    public String rowTitle() {
        if (articleTitle != null && !articleTitle.isEmpty()) return articleTitle;
        RecordingName.Parsed parsed = RecordingName.parse(stem());
        return parsed != null && parsed.place != null ? parsed.place : stem();
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
