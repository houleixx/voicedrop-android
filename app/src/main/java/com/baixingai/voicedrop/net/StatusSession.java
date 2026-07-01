package com.baixingai.voicedrop.net;

import com.baixingai.voicedrop.data.AuthStore;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class StatusSession {
    public interface Listener {
        void onPhase(String stem, String status);
        void onDone(String stem, String status);
        void onLinkRequest(String pairingId, String code, String pubkey);
        void onLinkRelease(String pairingId);
        void onError(String message);
    }

    private final AuthStore auth;
    private final Listener listener;
    private final OkHttpClient client = new OkHttpClient();
    private WebSocket socket;

    public StatusSession(AuthStore auth, Listener listener) {
        this.auth = auth;
        this.listener = listener;
    }

    public void connect() {
        if (socket != null) return;
        Request request = new Request.Builder()
                .url(Api.agentWs() + "/status")
                .header("Authorization", "Bearer " + auth.bearer())
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    AgentMessage.LinkRequest request = AgentMessage.linkRequest(text);
                    if (request != null) {
                        listener.onLinkRequest(request.pairingId, request.code, request.pubkey);
                        return;
                    }
                    AgentMessage.LinkRelease release = AgentMessage.linkRelease(text);
                    if (release != null) {
                        listener.onLinkRelease(release.pairingId);
                        return;
                    }
                    AgentMessage.Status status = AgentMessage.status(text);
                    if (status == null) return;
                    if ("ready".equals(status.status) || "empty".equals(status.status)) {
                        listener.onDone(status.stem, status.status);
                    } else {
                        listener.onPhase(status.stem, status.status);
                    }
                } catch (Exception ignored) {
                }
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                socket = null;
                listener.onError(t.getMessage());
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                socket = null;
            }
        });
    }

    public void close() {
        if (socket != null) socket.close(1000, "bye");
        socket = null;
    }
}
