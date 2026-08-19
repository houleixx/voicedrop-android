package com.baixingai.voicedrop.net;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class Api {
    public static final String CN_HOST = "voicedrop.cn";
    public static final String CF_HOST = "jianshuo.dev";
    /** EdgeOne WebSocket passthrough is not verified; keep the known-good direct host. */
    public static final String WS_HOST = CF_HOST;

    private Api() {}

    public static String filesBase() {
        return "https://" + host() + "/files/api";
    }

    public static String photoBase() {
        return "https://" + host() + "/files/api";
    }

    public static String agentBase() {
        return "https://" + host() + "/agent";
    }

    public static String recoBase() {
        return "https://" + host() + "/reco";
    }

    public static String agentWs() {
        return "wss://" + WS_HOST + "/agent";
    }

    public static String sharePage(String id) {
        return "https://voicedrop.cn/" + id;
    }

    public static String publicWebBase() {
        return publicWebBaseForHost(host());
    }

    static String publicWebBaseForHost(String host) {
        return CF_HOST.equals(host)
                ? "https://" + CF_HOST + "/voicedrop"
                : "https://" + CN_HOST;
    }

    private static String host() {
        return ApiRoute.currentHost();
    }

    public static String path(String key) {
        String[] parts = key.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = encodeSegment(parts[i]);
        }
        return String.join("/", parts);
    }

    private static String encodeSegment(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
