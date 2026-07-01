package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONObject;

public final class DeviceLinkStore {
    private final AuthStore auth;
    private final HttpClient http;

    public DeviceLinkStore(AuthStore auth, HttpClient http) {
        this.auth = auth;
        this.http = http;
    }

    public JSONObject start(String prefix, String pubkey) throws Exception {
        JSONObject body = new JSONObject().put("prefix", prefix).put("pubkey", pubkey);
        HttpClient.Response response = http.postJson(Api.agentBase() + "/link/start", auth.bearer(), body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("link start HTTP " + response.code);
        return new JSONObject(response.text());
    }

    public JSONObject verify(String pairingId, String code) throws Exception {
        JSONObject body = new JSONObject().put("pairingId", pairingId).put("code", code);
        HttpClient.Response response = http.postJson(Api.agentBase() + "/link/verify", auth.bearer(), body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("link verify HTTP " + response.code);
        return new JSONObject(response.text());
    }

    public void complete(String pairingId, JSONObject blob) throws Exception {
        JSONObject body = new JSONObject().put("pairingId", pairingId).put("blob", blob);
        HttpClient.Response response = http.postJson(Api.agentBase() + "/link/complete", auth.bearer(), body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("link complete HTTP " + response.code);
    }

    public void cancel(String pairingId) throws Exception {
        JSONObject body = new JSONObject().put("pairingId", pairingId);
        http.postJson(Api.agentBase() + "/link/cancel", auth.bearer(), body.toString().getBytes("UTF-8"));
    }
}
