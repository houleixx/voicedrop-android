package com.baixingai.voicedrop.net;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.baixingai.voicedrop.data.CommandQueueStore;
import com.baixingai.voicedrop.data.AuthStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class LibraryCommandSession {
    public interface Listener {
        void onQueueChanged(List<CommandRequest> queue);
        void onReply(String text, boolean ok);
        void onConfirm(String id, String text);
        void onUpdate();
        void onState(String state);
        void onError(String message);
    }

    private final Context context;
    private final AuthStore auth;
    private final Listener listener;
    private final OkHttpClient client = new OkHttpClient();
    private final List<CommandRequest> queue = new ArrayList<>();
    private WebSocket socket;
    private boolean closed;
    private List<CommandRef> refs = new ArrayList<>();

    public LibraryCommandSession(Context context, AuthStore auth, Listener listener) {
        this.context = context.getApplicationContext();
        this.auth = auth;
        this.listener = listener;
        queue.addAll(CommandQueueStore.load(this.context));
    }

    public void setRefs(List<CommandRef> refs) {
        this.refs = refs == null ? new ArrayList<CommandRef>() : new ArrayList<>(refs);
    }

    public void connect() {
        closed = false;
        if (socket != null) return;
        listener.onQueueChanged(queueSnapshot());
        listener.onState(queue.isEmpty() ? "已连接图库指令" : "正在恢复图库指令…");
        Request request = new Request.Builder()
                .url(Api.agentWs() + "/command")
                .header("Authorization", "Bearer " + auth.bearer())
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                listener.onState("已连接图库指令");
                resubmitAll();
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                handle(text);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                socket = null;
                if (!closed) {
                    listener.onState("图库指令连接断开，稍后重试");
                    reconnectLater();
                }
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                socket = null;
                if (!closed) reconnectLater();
            }
        });
    }

    public void enqueue(String text, List<CommandRef> refs) {
        if (text == null || text.trim().isEmpty()) return;
        setRefs(refs);
        CommandRequest request = new CommandRequest(UUID.randomUUID().toString(), text.trim());
        queue.add(request);
        persist();
        listener.onQueueChanged(queueSnapshot());
        send(request);
        listener.onState("正在执行图库指令");
    }

    public void confirm(String id) {
        if (socket != null && id != null && !id.isEmpty()) socket.send(confirmPayload(id));
    }

    public void cancel(String id) {
        if (socket != null && id != null && !id.isEmpty()) socket.send(cancelPayload(id));
    }

    private void resubmitAll() {
        for (CommandRequest request : queue) send(request);
    }

    private void send(CommandRequest request) {
        if (socket == null) return;
        socket.send(payloadFor(request.id, request.text, refs));
    }

    private void handle(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String type = obj.optString("type");
            String id = obj.optString("id", "");
            if ("status".equals(type)) {
                listener.onState("working".equals(obj.optString("state")) ? "正在执行图库指令" : obj.optString("state", "已连接图库指令"));
                return;
            }
            if ("reply".equals(type)) {
                listener.onReply(obj.optString("text", ""), obj.optBoolean("ok", true));
                return;
            }
            if ("confirm".equals(type)) {
                listener.onConfirm(id, obj.optString("text", obj.optString("message", "确认执行这条图库指令？")));
                return;
            }
            if ("updated".equals(type)) {
                listener.onUpdate();
                resolve(id);
                return;
            }
            if ("error".equals(type)) {
                String message = obj.optString("message", "图库指令执行失败");
                listener.onReply(message, false);
                listener.onError(message);
                resolve(id);
                return;
            }
            if ("snapshot".equals(type)) {
                reconcile(obj.optJSONArray("queue"));
            }
        } catch (Exception e) {
            listener.onError(e.getMessage());
        }
    }

    private void reconcile(JSONArray serverQueue) {
        if (serverQueue == null) {
            resubmitAll();
            return;
        }
        List<String> done = new ArrayList<>();
        List<String> known = new ArrayList<>();
        for (int i = 0; i < serverQueue.length(); i++) {
            JSONObject item = serverQueue.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            String status = item.optString("status", "");
            if (!id.isEmpty()) known.add(id);
            if ("done".equals(status) || "error".equals(status)) done.add(id);
        }
        for (String id : done) remove(id);
        for (CommandRequest request : queue) if (!known.contains(request.id)) send(request);
        persist();
        listener.onQueueChanged(queueSnapshot());
        listener.onState(queue.isEmpty() ? "已连接图库指令" : "正在执行图库指令");
    }

    private void resolve(String id) {
        if (id == null || id.isEmpty()) {
            if (!queue.isEmpty()) queue.remove(0);
        } else {
            remove(id);
        }
        persist();
        listener.onQueueChanged(queueSnapshot());
        listener.onState(queue.isEmpty() ? "图库指令已完成" : "正在执行图库指令");
    }

    private void remove(String id) {
        for (Iterator<CommandRequest> it = queue.iterator(); it.hasNext();) {
            if (it.next().id.equals(id)) {
                it.remove();
                return;
            }
        }
    }

    private void reconnectLater() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!closed && socket == null) connect();
        }, 1500);
    }

    private void persist() {
        CommandQueueStore.save(context, queue);
    }

    private List<CommandRequest> queueSnapshot() {
        return new ArrayList<>(queue);
    }

    public void close() {
        closed = true;
        if (socket != null) socket.close(1000, "bye");
        socket = null;
    }

    public static String payloadFor(String id, String text, List<CommandRef> refs) {
        StringBuilder body = new StringBuilder();
        body.append('{')
                .append("\"type\":\"instruct\",")
                .append("\"id\":\"").append(jsonEscape(id)).append("\",")
                .append("\"text\":\"").append(jsonEscape(text)).append("\",")
                .append("\"refs\":[");
        if (refs != null) {
            for (CommandRef ref : refs) {
                if (body.charAt(body.length() - 1) != '[') body.append(',');
                body.append('{')
                        .append("\"n\":").append(ref.n).append(',')
                        .append("\"stem\":\"").append(jsonEscape(ref.stem)).append("\",")
                        .append("\"title\":\"").append(jsonEscape(ref.title)).append("\"")
                        .append('}');
            }
        }
        body.append("]}");
        return body.toString();
    }

    public static String confirmPayload(String id) {
        return "{\"type\":\"confirm\",\"id\":\"" + jsonEscape(id) + "\"}";
    }

    public static String cancelPayload(String id) {
        return "{\"type\":\"cancel\",\"id\":\"" + jsonEscape(id) + "\"}";
    }

    private static String jsonEscape(String value) {
        if (value == null) value = "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        out.append("\\u");
                        for (int j = hex.length(); j < 4; j++) out.append('0');
                        out.append(hex);
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    public static final class CommandRequest {
        public final String id;
        public final String text;

        public CommandRequest(String id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    public static final class CommandRef {
        public final int n;
        public final String stem;
        public final String title;

        public CommandRef(int n, String stem, String title) {
            this.n = n;
            this.stem = stem == null ? "" : stem;
            this.title = title == null ? "" : title;
        }
    }
}
