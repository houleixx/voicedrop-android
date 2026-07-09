package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONObject;

public final class WechatAuthStore {
    private final AuthStore auth;
    private final HttpClient http;

    public WechatAuthStore(AuthStore auth, HttpClient http) {
        this.auth = auth;
        this.http = http;
    }

    public Result exchangeCode(String code, String nickname, String avatar) throws Exception {
        JSONObject payload = new JSONObject().put("code", code == null ? "" : code);
        if (nickname != null && !nickname.trim().isEmpty()) payload.put("nickname", nickname.trim());
        if (avatar != null && !avatar.trim().isEmpty()) payload.put("avatar", avatar.trim());
        HttpClient.Response response = http.postJson(Api.filesBase() + "/auth/wechat",
                auth.anonymousBearer(), payload.toString().getBytes("UTF-8"));
        JSONObject body = response.text().isEmpty() ? new JSONObject() : new JSONObject(response.text());
        if (!response.ok()) {
            return new Result(false, body.optString("error", "wechat_auth_failed"),
                    body.optString("detail", ""), "", "");
        }
        String session = body.optString("session", "");
        String scope = body.optString("scope", "");
        boolean valid = AuthStore.isSessionToken(session) && !scope.isEmpty();
        return new Result(valid, valid ? null : "bad_session", "", session, scope);
    }

    public static final class Result {
        public final boolean ok;
        public final String error;
        public final String detail;
        public final String session;
        public final String scope;

        Result(boolean ok, String error, String detail, String session, String scope) {
            this.ok = ok;
            this.error = error;
            this.detail = detail;
            this.session = session == null ? "" : session;
            this.scope = scope == null ? "" : scope;
        }

        public boolean requiresAccountSwitch(String anonId) {
            if (!ok || anonId == null || anonId.trim().isEmpty()) return false;
            return !("users/" + anonId.trim() + "/").equals(scope);
        }
    }
}
