package com.baixingai.voicedrop.net;

import com.baixingai.voicedrop.data.ArticleDoc;
import com.baixingai.voicedrop.data.AuthStore;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Queue;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class ArticleEditSession {
    public interface Listener {
        void onUpdated(ArticleDoc doc);
        void onState(String state);
        void onError(String message);
    }

    private final AuthStore auth;
    private final String stem;
    private final Listener listener;
    private final OkHttpClient client = new OkHttpClient();
    private final Queue<String> pending = new ArrayDeque<>();
    private WebSocket socket;
    private boolean awaiting;

    public ArticleEditSession(AuthStore auth, String stem, Listener listener) {
        this.auth = auth;
        this.stem = stem;
        this.listener = listener;
    }

    public void connect() {
        if (socket != null) return;
        Request request = new Request.Builder()
                .url(Api.agentWs() + "/edit?stem=" + Api.path(stem))
                .header("Authorization", "Bearer " + auth.bearer())
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                listener.onState("已连接");
                flush();
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    AgentMessage.Update update = AgentMessage.update(text);
                    if (update != null) {
                        awaiting = false;
                        listener.onUpdated(ArticleDoc.fromJson(update.docJson));
                        flush();
                    } else {
                        JSONObject obj = new JSONObject(text);
                        String type = obj.optString("type");
                        if ("error".equals(type)) {
                            awaiting = false;
                            listener.onError(obj.optString("message", "修改失败"));
                            flush();
                        }
                    }
                } catch (Exception e) {
                    listener.onError(e.getMessage());
                }
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                socket = null;
                awaiting = false;
                listener.onError(t.getMessage());
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                socket = null;
                listener.onState("已断开");
            }
        });
    }

    public void enqueue(String text) {
        if (text == null || text.trim().isEmpty()) return;
        pending.add(text.trim());
        flush();
    }

    private void flush() {
        if (socket == null || awaiting || pending.isEmpty()) return;
        String text = pending.poll();
        try {
            JSONObject body = new JSONObject().put("type", "edit").put("text", text);
            awaiting = socket.send(body.toString());
            listener.onState("正在改");
        } catch (Exception e) {
            awaiting = false;
            listener.onError(e.getMessage());
        }
    }

    public void close() {
        if (socket != null) socket.close(1000, "bye");
        socket = null;
    }
}
