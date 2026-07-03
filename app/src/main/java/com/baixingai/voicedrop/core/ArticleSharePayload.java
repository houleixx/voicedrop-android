package com.baixingai.voicedrop.core;

public final class ArticleSharePayload {
    private ArticleSharePayload() {}

    public static String textWithLink(String articleText, String url) {
        if (url == null || url.trim().isEmpty()) return articleText;
        if (articleText == null || articleText.trim().isEmpty()) return url;
        return articleText.trim() + "\n\n" + url;
    }

    public static boolean wantsLinkCard(String packageName) {
        return false;
    }

    public static String textForTarget(String articleText, String url, String packageName) {
        return wantsLinkCard(packageName) ? url : textWithLink(articleText, url);
    }
}
