package com.baixingai.voicedrop.net;

import com.baixingai.voicedrop.BuildConfig;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.URI;

public final class Api {
    public static final String CN_HOST = "voicedrop.cn";
    public static final String CF_HOST = "jianshuo.dev";
    /** 微信公众号第三方授权已在微信侧登记此固定回调域名，不能随 API 线路切换。 */
    public static final String WECHAT_AUTH_BASE = "https://" + CN_HOST + "/files/api";
    static final String TEST_HOST = "voicedrop-test.baixing.work";
    /** EdgeOne WebSocket passthrough is not verified; keep the known-good direct host. */
    public static final String WS_HOST = CF_HOST;

    private Api() {}

    public static String filesBase() {
        if (isTestEnvironment()) return "https://" + TEST_HOST + "/files/api";
        return "https://" + host() + "/files/api";
    }

    public static String photoBase() {
        if (isTestEnvironment()) return "https://" + TEST_HOST + "/files/api";
        return "https://" + host() + "/files/api";
    }

    public static String agentBase() {
        if (isTestEnvironment()) return "https://" + TEST_HOST + "/agent";
        return "https://" + host() + "/agent";
    }

    public static String wechatProductUrl() {
        return agentBase() + "/wechat-pay/product";
    }

    public static String recoBase() {
        if (isTestEnvironment()) return "https://" + TEST_HOST + "/reco";
        return "https://" + host() + "/reco";
    }

    public static String agentWs() {
        if (isTestEnvironment()) return "wss://" + TEST_HOST + "/agent";
        return "wss://" + WS_HOST + "/agent";
    }

    /** Debug builds use the isolated test domain; release keeps the CN/CF route race. */
    public static boolean isTestEnvironment() {
        return BuildConfig.VOICEDROP_TEST_ENVIRONMENT;
    }

    /** Pages and Worker hosts that ordinary in-app WebViews may keep inside the app. */
    public static boolean isKnownApiHost(String host) {
        if (host == null) return false;
        return CN_HOST.equalsIgnoreCase(host)
                || CF_HOST.equalsIgnoreCase(host)
                || (isTestEnvironment() && (TEST_HOST.equalsIgnoreCase(host)
                // Older test links and Pages-generated URLs can still use these origins.
                || "voicedrop-test.pages.dev".equalsIgnoreCase(host)
                || "voicedrop-agent-test.baixing.work".equalsIgnoreCase(host)
                || "voicedrop-reco-test.houleixx.workers.dev".equalsIgnoreCase(host)));
    }

    /** The Official Account scan/callback chain is registered only on voicedrop.cn. */
    public static boolean isWechatAuthorizationHost(String host) {
        return CN_HOST.equalsIgnoreCase(host);
    }

    public static String sharePage(String id) {
        return publicShareBase() + "/" + id;
    }

    public static String publicShareBase() {
        return isTestEnvironment() ? "https://" + TEST_HOST + "/voicedrop" : "https://" + CN_HOST;
    }

    public static boolean isTestPublicHost(String host) {
        return isTestEnvironment() && TEST_HOST.equalsIgnoreCase(host);
    }

    /** Test APIs still return canonical production short links; their IDs belong to test storage. */
    public static String generatedShareUrl(String url) {
        if (!isTestEnvironment() || url == null) return url;
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return url;
            String path = uri.getRawPath();
            if (CN_HOST.equalsIgnoreCase(uri.getHost()) || "www.voicedrop.cn".equalsIgnoreCase(uri.getHost())) {
                // Production public paths have no /voicedrop prefix.
            } else if (CF_HOST.equalsIgnoreCase(uri.getHost())
                    || "voicedrop-test.pages.dev".equalsIgnoreCase(uri.getHost())) {
                if (path == null || !path.startsWith("/voicedrop/")) return url;
                path = path.substring("/voicedrop".length());
            } else {
                return url;
            }
            if (path == null || !(path.matches("/[A-Za-z0-9_-]{6,16}")
                    || path.matches("/i/[A-Za-z0-9]{6,16}"))) return url;
            return publicShareBase() + path
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
        } catch (java.net.URISyntaxException e) {
            return url;
        }
    }

    public static String publicWebBase() {
        if (isTestEnvironment()) return "https://" + TEST_HOST + "/voicedrop";
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
