package com.baixingai.voicedrop.net;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class Api {
    public static final String HOST = "jianshuo.dev";

    private Api() {}

    public static String filesBase() {
        return "https://" + HOST + "/files/api";
    }

    public static String agentBase() {
        return "https://" + HOST + "/agent";
    }

    public static String recoBase() {
        return "https://" + HOST + "/reco";
    }

    public static String agentWs() {
        return "wss://" + HOST + "/agent";
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
