package com.baixingai.voicedrop.net;

import com.baixingai.voicedrop.BuildConfig;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class Api {
    public static final String CN_HOST = "voicedrop.cn";
    public static final String CF_HOST = "jianshuo.dev";
    /** 微信公众号第三方授权已在微信侧登记此固定回调域名，不能随 API 线路切换。 */
    public static final String WECHAT_AUTH_BASE = "https://" + CN_HOST + "/files/api";
    static final String TEST_FILES_HOST = "voicedrop-test.pages.dev";
    static final String TEST_AGENT_HOST = "voicedrop-agent-test.houleixx.workers.dev";
    static final String TEST_RECO_HOST = "voicedrop-reco-test.houleixx.workers.dev";
    /** EdgeOne WebSocket passthrough is not verified; keep the known-good direct host. */
    public static final String WS_HOST = CF_HOST;

    private Api() {}

    public static String filesBase() {
        if (isTestEnvironment()) return "https://" + TEST_FILES_HOST + "/files/api";
        return "https://" + host() + "/files/api";
    }

    public static String photoBase() {
        if (isTestEnvironment()) return "https://" + TEST_FILES_HOST + "/files/api";
        return "https://" + host() + "/files/api";
    }

    public static String agentBase() {
        if (isTestEnvironment()) return "https://" + TEST_AGENT_HOST + "/agent";
        return "https://" + host() + "/agent";
    }

    public static String recoBase() {
        if (isTestEnvironment()) return "https://" + TEST_RECO_HOST + "/reco";
        return "https://" + host() + "/reco";
    }

    public static String agentWs() {
        if (isTestEnvironment()) return "wss://" + TEST_AGENT_HOST + "/agent";
        return "wss://" + WS_HOST + "/agent";
    }

    /** Debug builds use three isolated test hosts; release keeps the CN/CF route race. */
    public static boolean isTestEnvironment() {
        return BuildConfig.VOICEDROP_TEST_ENVIRONMENT;
    }

    /** Pages and Worker hosts that ordinary in-app WebViews may keep inside the app. */
    public static boolean isKnownApiHost(String host) {
        if (host == null) return false;
        return CN_HOST.equalsIgnoreCase(host)
                || CF_HOST.equalsIgnoreCase(host)
                || (isTestEnvironment() && (TEST_FILES_HOST.equalsIgnoreCase(host)
                || TEST_AGENT_HOST.equalsIgnoreCase(host)
                || TEST_RECO_HOST.equalsIgnoreCase(host)));
    }

    /** The Official Account scan/callback chain is registered only on voicedrop.cn. */
    public static boolean isWechatAuthorizationHost(String host) {
        return CN_HOST.equalsIgnoreCase(host);
    }

    public static String sharePage(String id) {
        return "https://voicedrop.cn/" + id;
    }

    public static String publicWebBase() {
        if (isTestEnvironment()) return "https://" + TEST_FILES_HOST + "/voicedrop";
        return publicWebBaseForHost(host());
    }

    /** Fixed public origin for WeChat Official Account authorization and its callback. */
    public static String wechatAuthorizationBase() {
        return WECHAT_AUTH_BASE;
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
