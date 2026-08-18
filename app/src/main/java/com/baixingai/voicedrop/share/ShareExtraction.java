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
            String value = firstGroup(html,
                    "(?is)<meta[^>]+property=[\\\"']og:title[\\\"'][^>]+content=[\\\"']([^\\\"']+)");
            if (value.isEmpty()) value = firstGroup(html,
                    "(?is)<meta[^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]+property=[\\\"']og:title[\\\"']");
            if (value.isEmpty()) value = firstGroup(html, "(?is)<title[^>]*>(.*?)</title>");
            value = plainHtml(value);
            if (!value.isEmpty()) return cap(value, 80);
        }
        return fallback == null ? "" : fallback;
    }

    public static String readableHtml(String html) {
        if (html == null) return "";
        String body = firstGroup(html, "(?is)<div[^>]+id=[\\\"']js_content[\\\"'][^>]*>(.*)</div>");
        if (body.isEmpty()) body = firstGroup(html, "(?is)<article[^>]*>(.*)</article>");
        if (body.isEmpty()) body = firstGroup(html, "(?is)<body[^>]*>(.*)</body>");
        if (body.isEmpty()) body = html;
        body = body.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
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

    private static String firstGroup(String value, String pattern) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(value);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    public static String cap(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
