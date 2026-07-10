package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SettingsStore {
    private final AuthStore auth;
    private final HttpClient http;

    public SettingsStore(AuthStore auth, HttpClient http) {
        this.auth = auth;
        this.http = http;
    }

    public Style loadStyle() throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/style", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("style HTTP " + response.code);
        JSONObject obj = new JSONObject(response.text());
        List<Integer> styles = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("styles");
        if (arr != null) for (int i = 0; i < arr.length(); i++) styles.add(arr.optInt(i));
        return new Style(obj.optString("style", ""), obj.optString("name", ""), styles);
    }

    public void saveStyle(String style) throws Exception {
        JSONObject body = new JSONObject().put("style", style.trim());
        HttpClient.Response response = http.putBytes(Api.filesBase() + "/style", auth.bearer(), "application/json", body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("save style HTTP " + response.code);
    }

    public void saveName(String name) throws Exception {
        HttpClient.Response response = http.putBytes(Api.filesBase() + "/style", auth.bearer(), "application/json",
                nameBody(name).toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("save name HTTP " + response.code);
    }

    public static JSONObject nameBody(String name) throws Exception {
        return new JSONObject().put("name", name == null ? "" : name.trim());
    }

    public JSONObject loadStyleHistory() throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/style/history", auth.bearer());
        return response.ok() ? new JSONObject(response.text()) : new JSONObject();
    }

    public void saveStyleHead(int head) throws Exception {
        JSONObject body = new JSONObject().put("head", head);
        HttpClient.Response response = http.patchJson(Api.filesBase() + "/style/head", auth.bearer(), body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("style head HTTP " + response.code);
    }

    public void saveStyleSelection(List<Integer> styles) throws Exception {
        JSONObject body = styleSelectionBody(styles);
        HttpClient.Response response = http.putBytes(Api.filesBase() + "/style", auth.bearer(), "application/json", body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("style selection HTTP " + response.code);
    }

    public static JSONObject styleSelectionBody(List<Integer> styles) throws Exception {
        JSONArray arr = new JSONArray();
        if (styles != null) for (Integer style : styles) arr.put(style);
        return new JSONObject().put("styles", arr);
    }

    public JSONObject loadWechat() throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/download/WECHAT.json", auth.bearer());
        return response.ok() ? new JSONObject(response.text()) : new JSONObject();
    }

    public void saveWechat(String appid, String secret, boolean enabled) throws Exception {
        JSONObject body = new JSONObject()
                .put("appid", appid)
                .put("secret", secret)
                .put("enabled", enabled);
        HttpClient.Response response = http.putBytes(Api.filesBase() + "/upload/WECHAT.json", auth.bearer(), "application/json", body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("wechat HTTP " + response.code);
    }

    public String validateWechatCreds(String appid, String secret) throws Exception {
        String cleanAppid = appid == null ? "" : appid.trim();
        String cleanSecret = secret == null ? "" : secret.trim();
        if (!cleanAppid.startsWith("wx") || cleanAppid.length() < 8) return "AppID 格式不对";
        if (cleanSecret.length() < 16) return "AppSecret 太短";
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + com.baixingai.voicedrop.net.Api.path(cleanAppid)
                + "&secret=" + com.baixingai.voicedrop.net.Api.path(cleanSecret);
        HttpClient.Response response = http.get(url, "");
        if (!response.ok()) return "微信校验失败 HTTP " + response.code;
        JSONObject obj = new JSONObject(response.text());
        int code = obj.optInt("errcode", 0);
        if (code == 0 || code == 40164) return null;
        if (code == 40013) return "AppID 不正确";
        if (code == 40125) return "AppSecret 不正确";
        return obj.optString("errmsg", "微信校验失败");
    }

    public JSONObject loadConfig() throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/download/CONFIG.json", auth.bearer());
        return response.ok() ? new JSONObject(response.text()) : new JSONObject();
    }

    public void saveConfig(boolean autoShareCommunity) throws Exception {
        JSONObject body = appConfigBody(autoShareCommunity);
        HttpClient.Response response = http.putBytes(Api.filesBase() + "/upload/CONFIG.json", auth.bearer(), "application/json", body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("config HTTP " + response.code);
    }

    @Deprecated
    public void saveConfig(boolean autoShareCommunity, boolean followupsEnabled) throws Exception {
        saveConfig(autoShareCommunity);
    }

    public static JSONObject appConfigBody(boolean autoShareCommunity) throws Exception {
        return new JSONObject().put("autoShareCommunity", autoShareCommunity);
    }

    public String articlesPageUrl() throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/token/articles", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("articles token HTTP " + response.code);
        return new JSONObject(response.text()).optString("url", "");
    }

    public static final class Style {
        public final String style;
        public final String name;
        public final List<Integer> selectedStyles;

        Style(String style, String name, List<Integer> selectedStyles) {
            this.style = style;
            this.name = name == null ? "" : name;
            this.selectedStyles = selectedStyles;
        }
    }
}
