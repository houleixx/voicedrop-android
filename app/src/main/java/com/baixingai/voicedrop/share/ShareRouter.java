package com.baixingai.voicedrop.share;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ShareRouter {
    private static final Pattern WEB_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    private ShareRouter() {}

    public static ShareKind classify(String action, String mimeType, int streamCount, boolean hasText, String text) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        if (mime.startsWith("audio/")) return ShareKind.AUDIO;
        if (mime.startsWith("image/")) return ShareKind.IMAGE;
        if (isDocumentMime(mime)) return ShareKind.DOCUMENT;
        if (looksLikeUrl(text)) return ShareKind.WEB;
        if (streamCount > 0 && mime.isEmpty()) return ShareKind.DOCUMENT;
        return hasText ? ShareKind.TEXT : ShareKind.TEXT;
    }

    private static boolean isDocumentMime(String mime) {
        return "application/pdf".equals(mime)
                || "application/rtf".equals(mime)
                || "text/rtf".equals(mime)
                || "application/msword".equals(mime)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime);
    }

    private static boolean looksLikeUrl(String text) {
        return text != null && WEB_URL.matcher(text.trim()).find();
    }
}
