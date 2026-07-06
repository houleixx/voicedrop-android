package com.baixingai.voicedrop.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubRelease {
    public final String tagName;
    public final String htmlUrl;
    public final String body;
    public final boolean prerelease;
    public final boolean draft;
    public final String apkName;
    public final String apkDownloadUrl;
    public final long apkSize;

    public GitHubRelease(String tagName, String htmlUrl, String body,
                         boolean prerelease, boolean draft,
                         String apkName, String apkDownloadUrl, long apkSize) {
        this.tagName = tagName;
        this.htmlUrl = htmlUrl;
        this.body = body;
        this.prerelease = prerelease;
        this.draft = draft;
        this.apkName = apkName;
        this.apkDownloadUrl = apkDownloadUrl;
        this.apkSize = apkSize;
    }

    public static GitHubRelease parse(String json) throws Exception {
        String text = json == null ? "" : json;
        String apkName = "";
        String apkUrl = "";
        long apkSize = 0L;
        Matcher nameMatcher = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+\\.apk)\"", Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (nameMatcher.find()) {
            apkName = unescape(nameMatcher.group(1));
            int assetStart = text.lastIndexOf('{', nameMatcher.start());
            int nextAsset = text.indexOf("\"name\"", nameMatcher.end());
            int assetEnd = nextAsset > 0 ? nextAsset : text.length();
            String asset = text.substring(Math.max(0, assetStart), assetEnd);
            apkUrl = proxiedDownloadUrl(stringField(asset, "browser_download_url"));
            apkSize = longField(asset, "size");
        }
        return new GitHubRelease(
                stringField(text, "tag_name"),
                stringField(text, "html_url"),
                stringField(text, "body"),
                booleanField(text, "prerelease"),
                booleanField(text, "draft"),
                apkName,
                apkUrl,
                apkSize);
    }

    public boolean hasApk() {
        return apkDownloadUrl != null && !apkDownloadUrl.isEmpty();
    }

    private static String proxiedDownloadUrl(String url) {
        String value = url == null ? "" : url;
        String prefix = "https://github.com/";
        return value.startsWith(prefix)
                ? "https://jianshuo.dev/gh/" + value.substring(prefix.length())
                : value;
    }

    private static String stringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : "";
    }

    private static boolean booleanField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(true|false)")
                .matcher(json);
        return matcher.find() && "true".equals(matcher.group(1));
    }

    private static long longField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)")
                .matcher(json);
        if (!matcher.find()) return 0L;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String unescape(String value) {
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}
