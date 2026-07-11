package com.baixingai.voicedrop.audio;

import android.util.Base64;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.net.Api;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class RealtimeSession {
    public enum State { IDLE, CONNECTING, LIVE, DEGRADED }

    public interface Listener {
        void onState(State state);
        void onAudioDelta(byte[] pcm16le24k);
        void onResponseCreated();
        void onResponseDone();
    }

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build();

    private final AuthStore auth;
    private final Listener listener;
    private WebSocket socket;
    private int generation;
    private State state = State.IDLE;

    public RealtimeSession(AuthStore auth, Listener listener) {
        this.auth = auth;
        this.listener = listener;
    }

    public void connect() {
        if (socket != null) return;
        setState(State.CONNECTING);
        final int gen = ++generation;
        String token = auth == null ? "" : auth.bearer();
        if (token == null || token.isEmpty()) {
            setState(State.DEGRADED);
            return;
        }
        Request request = new Request.Builder()
                .url(Api.agentWs() + "/realtime/relay?fmt=pcmu")
                .header("Authorization", "Bearer " + token)
                .build();
        socket = CLIENT.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                if (!current(webSocket, gen)) return;
                setState(State.LIVE);
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                if (!current(webSocket, gen)) return;
                handle(text);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (!current(webSocket, gen)) return;
                setState(State.DEGRADED);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (!current(webSocket, gen)) return;
                socket = null;
                setState(State.IDLE);
            }
        });
    }

    private boolean current(WebSocket ws, int gen) {
        return gen == generation && ws == socket;
    }

    private void handle(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String type = obj.optString("type", "");
            if ("response.created".equals(type)) {
                listener.onResponseCreated();
            } else if ("response.output_audio.delta".equals(type)) {
                String b64 = obj.optString("delta", "");
                if (!b64.isEmpty()) listener.onAudioDelta(Base64.decode(b64, Base64.DEFAULT));
            } else if ("response.done".equals(type)) {
                listener.onResponseDone();
            }
        } catch (Exception ignored) {
        }
    }

    public void appendAudio(byte[] pcmu8k) {
        if (pcmu8k == null || pcmu8k.length == 0 || socket == null) return;
        try {
            JSONObject obj = new JSONObject()
                    .put("type", "input_audio_buffer.append")
                    .put("audio", Base64.encodeToString(pcmu8k, Base64.NO_WRAP));
            socket.send(obj.toString());
        } catch (Exception ignored) {
        }
    }

    /** Drops speech captured before the half-duplex gate muted the microphone. */
    public void clearInputBuffer() {
        if (socket == null) return;
        try {
            socket.send(new JSONObject().put("type", "input_audio_buffer.clear").toString());
        } catch (Exception ignored) {
        }
    }

    public void disconnect() {
        generation++;
        WebSocket ws = socket;
        socket = null;
        if (ws != null) ws.close(1000, "done");
        setState(State.IDLE);
    }

    public State state() {
        return state;
    }

    private void setState(State next) {
        if (state == next) return;
        state = next;
        listener.onState(next);
    }
}
