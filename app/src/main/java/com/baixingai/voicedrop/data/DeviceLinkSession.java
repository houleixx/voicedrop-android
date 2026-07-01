package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.net.Api;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class DeviceLinkSession {
    private final AuthStore auth;
    private final DeviceLinkStore store;
    private final Listener listener;
    private final OkHttpClient client = new OkHttpClient();
    private DeviceLinkCrypto.Keypair keypair;
    private String pairingId;
    private WebSocket socket;

    public DeviceLinkSession(AuthStore auth, DeviceLinkStore store, Listener listener) {
        this.auth = auth;
        this.store = store;
        this.listener = listener;
    }

    public void start(String prefix) {
        try {
            keypair = DeviceLinkCrypto.newKeypair();
            JSONObject response = store.start(prefix, keypair.publicKeyB64);
            if (!response.optBoolean("ok", true)) {
                listener.onError(response.optString("reason", "未找到匹配账号"));
                return;
            }
            pairingId = response.optString("pairingId", "");
            if (pairingId.isEmpty()) {
                listener.onError("服务端没有返回 pairingId");
                return;
            }
            openSocket(pairingId);
            listener.onCodeNeeded(pairingId);
        } catch (Exception e) {
            listener.onError(e.getMessage());
        }
    }

    public void verify(String code) {
        try {
            if (pairingId == null || pairingId.isEmpty()) throw new IllegalStateException("请先发起登录");
            JSONObject response = store.verify(pairingId, code);
            if (response.optBoolean("ok", true)) {
                listener.onState("验证码已发送，正在接收账号…");
            } else {
                listener.onError(response.optString("reason", "验证码不正确"));
            }
        } catch (Exception e) {
            listener.onError(e.getMessage());
        }
    }

    public void cancel() {
        try {
            if (pairingId != null && !pairingId.isEmpty()) store.cancel(pairingId);
        } catch (Exception ignored) {
        }
        if (socket != null) socket.close(1000, "cancel");
        socket = null;
    }

    private void openSocket(String pairingId) {
        Request request = new Request.Builder()
                .url(Api.agentWs() + "/link/socket?pairingId=" + Api.path(pairingId))
                .header("Authorization", "Bearer " + auth.bearer())
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                listener.onError(t.getMessage());
            }
        });
    }

    private void handleMessage(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String type = obj.optString("type");
            if ("link_ready".equals(type)) {
                JSONObject blob = obj.optJSONObject("blob");
                if (blob == null) blob = obj;
                String token = DeviceLinkCrypto.decrypt(
                        blob.optString("epk"),
                        blob.optString("sealed"),
                        keypair.privateKeyB64);
                if (auth.adoptToken(token)) {
                    listener.onDone();
                } else {
                    listener.onError("收到的 token 格式不正确");
                }
            } else if ("link_cancelled".equals(type)) {
                listener.onError("对方已拒绝");
            } else if ("link_expired".equals(type)) {
                listener.onError("已超时，请重新发起");
            }
        } catch (Exception e) {
            listener.onError(e.getMessage());
        }
    }

    public interface Listener {
        void onCodeNeeded(String pairingId);
        void onState(String state);
        void onDone();
        void onError(String message);
    }
}
