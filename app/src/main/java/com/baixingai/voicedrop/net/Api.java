package com.baixingai.voicedrop.net;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class Api {
    /** HTTP APIs and public photos use the EdgeOne-backed domestic entry. */
    public static final String HOST = "voicedrop.cn";
    public static final String PHOTO_HOST = HOST;
    /** EdgeOne WebSocket passthrough is not verified; keep the known-good direct host. */
    public static final String WS_HOST = "jianshuo.dev";

    private Api() {}

    public static String filesBase() {
        return "https://" + HOST + "/files/api";
    }

    public static String photoBase() {
        return "https://" + PHOTO_HOST + "/files/api";
    }

    public static String agentBase() {
        return "https://" + HOST + "/agent";
    }

    public static String recoBase() {
        return "https://" + HOST + "/reco";
    }

    public static String agentWs() {
        return "wss://" + WS_HOST + "/agent";
    }

    public static String sharePage(String id) {
        return "https://voicedrop.cn/" + id;
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
