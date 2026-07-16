package com.baixingai.voicedrop.net;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.baixingai.voicedrop.data.CommandQueueStore;
import com.baixingai.voicedrop.data.CommandStateStore;
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
    private static final String TAG = "LibraryCommandSession";
    public interface Listener {
        void onQueueChanged(List<CommandRequest> queue);
        void onReply(String text, boolean ok);
        void onConfirm(String id, String text);
        void onUpdate(List<String> stems);
        void onState(String state);
        void onError(String message);
    }

    private final Context context;
    private final AuthStore auth;
    private final Listener listener;
    private final OkHttpClient client = new OkHttpClient();
    private final List<CommandRequest> queue = new ArrayList<>();
    private final List<CommandStateStore.Control> controls = new ArrayList<>();
    private final List<CommandStateStore.Confirmation> confirmations = new ArrayList<>();
    private WebSocket socket;
    private boolean closed;
    private boolean opened;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable reconnectRunnable = () -> {
        if (!closed && socket == null) connect();
    };
    private List<CommandRef> refs = new ArrayList<>();

    public LibraryCommandSession(Context context, AuthStore auth, Listener listener) {
        this.context = context.getApplicationContext();
        this.auth = auth;
        this.listener = listener;
        queue.addAll(CommandQueueStore.load(this.context));
        controls.addAll(CommandStateStore.loadControls(this.context));
        confirmations.addAll(CommandStateStore.loadConfirmations(this.context));
    }

    public void setRefs(List<CommandRef> refs) {
        this.refs = refs == null ? new ArrayList<CommandRef>() : new ArrayList<>(refs);
    }

    public void connect() {
        closed = false;
        if (socket != null) return;
        main.removeCallbacks(reconnectRunnable);
        listener.onQueueChanged(queueSnapshot());
        listener.onState(queue.isEmpty() ? "已连接图库指令" : "正在恢复图库指令…");
        notifyConfirmations();
        Request request = new Request.Builder()
                .url(Api.agentWs() + "/command")
                .header("Authorization", "Bearer " + auth.bearer())
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                if (closed || webSocket != socket) return;
                opened = true;
                listener.onState("已连接图库指令");
                resubmitAll();
                flushControls();
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                if (closed || webSocket != socket) return;
                handle(text);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                scheduleReconnect(webSocket);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                scheduleReconnect(webSocket);
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
        queueControl("confirm", id);
    }

    public void cancel(String id) {
        queueControl("cancel", id);
    }

    private void resubmitAll() {
        for (CommandRequest request : queue) send(request);
    }

    private void send(CommandRequest request) {
        if (socket == null || !opened) return;
        socket.send(payloadFor(request.id, request.text, refs));
    }

    private void handle(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String type = obj.optString("type");
            String id = obj.optString("id", "");
            Log.d(TAG, "message type=" + type + " id=" + (id.isEmpty() ? "-" : id));
            if ("status".equals(type)) {
                listener.onState("working".equals(obj.optString("state")) ? "正在执行图库指令" : obj.optString("state", "已连接图库指令"));
                return;
            }
            if ("reply".equals(type)) {
                listener.onReply(obj.optString("text", ""), obj.optBoolean("ok", true));
                clearCommandState(id);
                if (!id.isEmpty()) resolve(id);
                return;
            }
            if ("confirm".equals(type)) {
                if (hasControl(id)) return;
                String confirmationText = confirmationText(obj);
                rememberConfirmation(id, confirmationText);
                listener.onConfirm(id, confirmationText);
                return;
            }
            if ("updated".equals(type)) {
                clearCommandState(id);
                listener.onUpdate(strings(obj.optJSONArray("stems")));
                resolve(id);
                return;
            }
            if ("error".equals(type)) {
                String message = obj.optString("message", "图库指令执行失败");
                listener.onReply(message, false);
                listener.onError(message);
                clearCommandState(id);
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
        for (String id : done) clearCommandState(id);
        for (String id : done) remove(id);
        for (CommandRequest request : queue) if (!known.contains(request.id)) send(request);
        persist();
        listener.onQueueChanged(queueSnapshot());
        listener.onState(queue.isEmpty() ? "已连接图库指令" : "正在执行图库指令");
    }

    private static List<String> strings(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String value = arr.optString(i, "");
            if (!value.isEmpty()) out.add(value);
        }
        return out;
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
        main.removeCallbacks(reconnectRunnable);
        main.postDelayed(reconnectRunnable, 1500);
    }

    private void scheduleReconnect(WebSocket current) {
        if (socket != current) return;
        socket = null;
        opened = false;
        if (closed) return;
        listener.onState("图库指令连接断开，稍后重试");
        reconnectLater();
    }

    private void notifyConfirmations() {
        for (CommandStateStore.Confirmation confirmation : new ArrayList<>(confirmations)) {
            listener.onConfirm(confirmation.id, confirmation.text);
        }
    }

    private void queueControl(String type, String id) {
        if (id == null || id.isEmpty()) return;
        clearConfirmation(id);
        for (Iterator<CommandStateStore.Control> it = controls.iterator(); it.hasNext();) {
            if (it.next().id.equals(id)) it.remove();
        }
        controls.add(new CommandStateStore.Control(type, id));
        CommandStateStore.saveControls(context, controls);
        flushControls();
    }

    private void flushControls() {
        if (socket == null || !opened) return;
        for (CommandStateStore.Control control : new ArrayList<>(controls)) {
            socket.send("confirm".equals(control.type) ? confirmPayload(control.id) : cancelPayload(control.id));
        }
    }

    private boolean hasControl(String id) {
        if (id == null || id.isEmpty()) return false;
        for (CommandStateStore.Control control : controls) if (id.equals(control.id)) return true;
        return false;
    }

    private void rememberConfirmation(String id, String text) {
        if (id == null || id.isEmpty()) return;
        clearConfirmation(id);
        confirmations.add(new CommandStateStore.Confirmation(id, text));
        CommandStateStore.saveConfirmations(context, confirmations);
    }

    private void clearConfirmation(String id) {
        if (id == null || id.isEmpty()) return;
        boolean changed = false;
        for (Iterator<CommandStateStore.Confirmation> it = confirmations.iterator(); it.hasNext();) {
            if (it.next().id.equals(id)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) CommandStateStore.saveConfirmations(context, confirmations);
    }

    private void clearCommandState(String id) {
        clearConfirmation(id);
        if (id == null || id.isEmpty()) return;
        boolean changed = false;
        for (Iterator<CommandStateStore.Control> it = controls.iterator(); it.hasNext();) {
            if (it.next().id.equals(id)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) CommandStateStore.saveControls(context, controls);
    }

    private void persist() {
        CommandQueueStore.save(context, queue);
    }

    private List<CommandRequest> queueSnapshot() {
        return new ArrayList<>(queue);
    }

    public void close() {
        closed = true;
        opened = false;
        main.removeCallbacks(reconnectRunnable);
        WebSocket current = socket;
        socket = null;
        if (current != null) current.close(1000, "bye");
    }

    public static String confirmationText(JSONObject obj) {
        if (obj == null) return "确认执行这条图库指令？";
        String summary = obj.optString("summary", "");
        if (!summary.isEmpty()) return summary;
        String text = obj.optString("text", "");
        if (!text.isEmpty()) return text;
        return obj.optString("message", "确认执行这条图库指令？");
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
