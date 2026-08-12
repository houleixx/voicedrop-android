package com.baixingai.voicedrop.core;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds a trusted same-origin handoff when the server scan page suppresses its Referer. */
public final class WechatAuthorizationHandoff {
    private static final Pattern LOCATION_REPLACE = Pattern.compile(
            "window\\.location\\.replace\\s*\\(\\s*(\"(?:\\\\.|[^\"\\\\])*\")\\s*\\)");
    private static final String WECHAT_HOST = "mp.weixin.qq.com";
    private static final String WECHAT_PATH = "/cgi-bin/componentloginpage";
    private static final String SCAN_HOST = "voicedrop.cn";
    private static final String SCAN_PATH = "/files/api/wechat/scan";

    private WechatAuthorizationHandoff() {}

    public static String authorizationUrl(String scanHtml) {
        Matcher matcher = LOCATION_REPLACE.matcher(scanHtml == null ? "" : scanHtml);
        if (!matcher.find()) throw new IllegalArgumentException("missing WeChat authorization URL");
        String value;
        try {
            Object decoded = new JSONTokener(matcher.group(1)).nextValue();
            value = decoded instanceof String ? (String) decoded : "";
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid WeChat authorization URL", e);
        }
        URI uri = parse(value, "invalid WeChat authorization URL");
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !WECHAT_HOST.equalsIgnoreCase(uri.getHost())
                || !WECHAT_PATH.equals(uri.getPath())
                || !hasValue(uri, "component_appid")
                || !hasValue(uri, "pre_auth_code")
                || !hasValue(uri, "redirect_uri")) {
            throw new IllegalArgumentException("untrusted WeChat authorization URL");
        }
        return value;
    }

    public static String handoffHtml(String scanUrl, String scanHtml) {
        URI scan = parse(scanUrl, "invalid scan URL");
        if (!"https".equalsIgnoreCase(scan.getScheme())
                || !SCAN_HOST.equalsIgnoreCase(scan.getHost())
                || !SCAN_PATH.equals(scan.getPath())) {
            throw new IllegalArgumentException("untrusted scan URL");
        }
        String authorizationUrl = authorizationUrl(scanHtml);
        return "<!doctype html><meta charset=\"utf-8\">"
                + "<meta name=\"referrer\" content=\"origin\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>正在打开微信授权</title><p>正在打开微信授权二维码页…</p>"
                + "<script>window.location.replace(" + JSONObject.quote(authorizationUrl) + ");</script>";
    }

    private static URI parse(String value, String message) {
        try { return URI.create(value == null ? "" : value); }
        catch (Exception e) { throw new IllegalArgumentException(message, e); }
    }

    private static boolean hasValue(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) return false;
        String prefix = name + "=";
        for (String field : query.split("&")) {
            if (field.startsWith(prefix) && field.length() > prefix.length()) return true;
        }
        return false;
    }

}
