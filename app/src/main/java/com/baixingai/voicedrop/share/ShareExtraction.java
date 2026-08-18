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

    public static String firstWebUrl(String value) {
        if (value == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)https?://[^\\s<>]+")
                .matcher(value);
        if (!matcher.find()) return "";
        String url = matcher.group();
        while (url.endsWith("。") || url.endsWith("，") || url.endsWith(",")
                || url.endsWith(".") || url.endsWith(")") || url.endsWith("）")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static String htmlTitle(String html, String fallback) {
        if (html != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
            if (matcher.find()) {
                String value = plainHtml(matcher.group(1));
                if (!value.isEmpty()) return cap(value, 80);
            }
        }
        return fallback == null ? "" : fallback;
    }

    public static String readableHtml(String html) {
        if (html == null) return "";
        String body = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<(br|/p|/div|/article|/section|/h[1-6]|/li)[^>]*>", "\n")
                .replaceAll("(?is)<[^>]+>", " ");
        return plainHtml(body).replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static String plainHtml(String value) {
        return value.replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").trim();
    }

    public static String cap(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
