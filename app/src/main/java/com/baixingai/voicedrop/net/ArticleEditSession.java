package com.baixingai.voicedrop.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.baixingai.voicedrop.data.ArticleDoc;
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

public final class ArticleEditSession {
    public interface Listener {
        void onUpdated(ArticleDoc doc);
        void onQueueChanged(List<EditRequest> queue);
        void onReply(String text, boolean ok);
        void onState(String state);
        void onError(String message);
    }

    private final Context context;
    private final AuthStore auth;
    private final String stem;
    private final Listener listener;
    private final OkHttpClient client = new OkHttpClient();
    private final List<EditRequest> queue = new ArrayList<>();
    private WebSocket socket;
    private boolean closed;

    public ArticleEditSession(Context context, AuthStore auth, String stem, Listener listener) {
        this.context = context.getApplicationContext();
        this.auth = auth;
        this.stem = stem;
        this.listener = listener;
        queue.addAll(loadQueue());
    }

    public String stem() {
        return stem;
    }

    public void connect() {
        closed = false;
        if (socket != null) return;
        listener.onQueueChanged(queueSnapshot());
        listener.onState(queue.isEmpty() ? "连接文章编辑器…" : "正在恢复未完成修改…");
        Request request = new Request.Builder()
                .url(Api.agentWs() + "/edit?stem=" + Api.path(stem))
                .header("Authorization", "Bearer " + auth.bearer())
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                listener.onState("已连接");
                resubmitAll();
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                handle(text);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                socket = null;
                if (!closed) {
                    listener.onState("连接断开，稍后重试");
                    reconnectLater();
                }
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                socket = null;
                if (!closed) reconnectLater();
            }
        });
    }

    public void enqueue(String text, int articleIndex) {
        enqueue(text, articleIndex, new ArrayList<AgentImage>());
    }

    public void enqueue(String text, int articleIndex, List<AgentImage> images) {
        if (text == null || text.trim().isEmpty()) return;
        EditRequest request = new EditRequest(UUID.randomUUID().toString(), text.trim(), Math.max(0, articleIndex));
        request.images.addAll(images);
        queue.add(request);
        persist();
        listener.onQueueChanged(queueSnapshot());
        send(request);
        listener.onState("正在改");
    }

    private void resubmitAll() {
        for (EditRequest request : queue) send(request);
    }

    private void send(EditRequest request) {
        if (socket == null) return;
        socket.send(payloadFor(request));
    }

    private void handle(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String type = obj.optString("type");
            String id = obj.optString("id", "");
            if ("status".equals(type)) {
                listener.onState("working".equals(obj.optString("state")) ? "正在改" : obj.optString("state", "已连接"));
                return;
            }
            if ("reply".equals(type)) {
                listener.onReply(obj.optString("text", ""), obj.optBoolean("ok", true));
                return;
            }
            if ("updated".equals(type)) {
                JSONObject article = obj.optJSONObject("article");
                if (article == null) article = obj.optJSONObject("doc");
                if (article != null) listener.onUpdated(ArticleDoc.fromJson(article.toString()));
                resolve(id);
                return;
            }
            if ("error".equals(type)) {
                String message = obj.optString("message", "修改失败");
                listener.onReply(message, false);
                listener.onError(message);
                resolve(id);
                return;
            }
            if ("snapshot".equals(type)) {
                JSONObject article = obj.optJSONObject("article");
                if (article != null) listener.onUpdated(ArticleDoc.fromJson(article.toString()));
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
        for (EditRequest request : queue) if (!known.contains(request.id)) send(request);
        persist();
        listener.onQueueChanged(queueSnapshot());
        listener.onState(queue.isEmpty() ? "已连接" : "正在改");
    }

    private void resolve(String id) {
        if (id == null || id.isEmpty()) {
            if (!queue.isEmpty()) queue.remove(0);
        } else {
            remove(id);
        }
        persist();
        listener.onQueueChanged(queueSnapshot());
        listener.onState(queue.isEmpty() ? "已完成" : "正在改");
    }

    private void remove(String id) {
        for (Iterator<EditRequest> it = queue.iterator(); it.hasNext();) {
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
        JSONArray arr = new JSONArray();
        for (EditRequest request : queue) {
            if (!request.images.isEmpty()) continue;
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", request.id);
                obj.put("text", request.text);
                obj.put("articleIndex", request.articleIndex);
                arr.put(obj);
            } catch (Exception ignored) {
            }
        }
        SharedPreferences prefs = context.getSharedPreferences("voicedrop.editqueue", Context.MODE_PRIVATE);
        if (arr.length() == 0) prefs.edit().remove(stem).apply();
        else prefs.edit().putString(stem, arr.toString()).apply();
    }

    private List<EditRequest> loadQueue() {
        List<EditRequest> out = new ArrayList<>();
        String raw = context.getSharedPreferences("voicedrop.editqueue", Context.MODE_PRIVATE).getString(stem, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                out.add(new EditRequest(obj.optString("id"), obj.optString("text"), obj.optInt("articleIndex", 0)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private List<EditRequest> queueSnapshot() {
        return new ArrayList<>(queue);
    }

    public void close() {
        closed = true;
        if (socket != null) socket.close(1000, "bye");
        socket = null;
    }

    public static String payloadFor(EditRequest request) {
        StringBuilder body = new StringBuilder();
        body.append('{')
                .append("\"type\":\"instruct\",")
                .append("\"id\":\"").append(jsonEscape(request.id)).append("\",")
                .append("\"text\":\"").append(jsonEscape(request.text)).append("\",")
                .append("\"articleIndex\":").append(request.articleIndex);
        if (!request.images.isEmpty()) {
            body.append(",\"images\":[");
            for (AgentImage image : request.images) {
                if (body.charAt(body.length() - 1) != '[') body.append(',');
                body.append('{')
                        .append("\"key\":\"").append(jsonEscape(image.key)).append("\",")
                        .append("\"data\":\"").append(jsonEscape(image.base64)).append("\",")
                        .append("\"mediaType\":\"image/jpeg\"")
                        .append('}');
            }
            body.append(']');
        }
        body.append('}');
        return body.toString();
    }

    private static String jsonEscape(String value) {
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

    public static final class EditRequest {
        public final String id;
        public final String text;
        public final int articleIndex;
        public final List<AgentImage> images = new ArrayList<>();

        public EditRequest(String id, String text, int articleIndex) {
            this.id = id;
            this.text = text;
            this.articleIndex = articleIndex;
        }
    }

    public static final class AgentImage {
        public final String key;
        public final String base64;

        public AgentImage(String key, String base64) {
            this.key = key;
            this.base64 = base64;
        }
    }
}
