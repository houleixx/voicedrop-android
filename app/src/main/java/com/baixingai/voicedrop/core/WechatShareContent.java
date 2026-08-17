package com.baixingai.voicedrop.core;

import java.net.URI;

/** Pure cross-platform rules for article WeChat cards. */
public final class WechatShareContent {
    private WechatShareContent() {}

    public static String excerpt(String body, String fallback) {
        String plain = ArticleBody.stripMarkers(body == null ? "" : body)
                .replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) return fallback;
        int end = plain.offsetByCodePoints(0, Math.min(44, plain.codePointCount(0, plain.length())));
        return end == plain.length() ? plain : plain.substring(0, end) + "……";
    }

    public static String publicShareId(String url) {
        try {
            String path = new URI(url == null ? "" : url).getPath();
            if (path == null) return "";
            int slash = path.lastIndexOf('/');
            String id = path.substring(slash + 1);
            return id.matches("[A-Za-z0-9_-]{6,16}") ? id : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String sharedArticlePath(String shareId, int section) {
        String id = shareId == null ? "" : shareId.trim();
        if (!id.matches("[A-Za-z0-9_-]{6,16}")) return "";
        return "pages/shared-article/index?shareId=" + id
                + "&section=" + Math.max(0, section) + "&fromShare=1";
    }

    public static String communityUrl(String baseUrl, int section) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) return "";
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "s=" + Math.max(0, section);
    }
}
