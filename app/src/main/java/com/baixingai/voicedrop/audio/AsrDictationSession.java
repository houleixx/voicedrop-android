package com.baixingai.voicedrop.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.VolcASRProtocol;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class AsrDictationSession {
    private static final int SAMPLE_RATE = 16000;

    private final AuthStore auth;
    private final Listener listener;
    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService audioThread = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private WebSocket socket;
    private AudioRecord recorder;

    public AsrDictationSession(AuthStore auth, Listener listener) {
        this.auth = auth;
        this.listener = listener;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        Request.Builder builder = new Request.Builder().url(Api.agentWs() + "/asr");
        String token = auth.bearer();
        if (token != null && !token.isEmpty()) builder.header("Authorization", "Bearer " + token);
        socket = client.newWebSocket(builder.build(), new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                try {
                    webSocket.send(ByteString.of(VolcASRProtocol.buildFullClientPayload("voicedrop-android-edit", SAMPLE_RATE)));
                    listener.onState("正在听写…");
                    startMicLoop(webSocket);
                } catch (Exception e) {
                    listener.onError(e.getMessage());
                    stop();
                }
            }

            @Override public void onMessage(WebSocket webSocket, ByteString bytes) {
                try {
                    VolcASRProtocol.ParsedMessage message = VolcASRProtocol.parseServerMessage(bytes.toByteArray());
                    if (message.isError) listener.onError(message.errorMessage);
                    if (message.text != null && !message.text.isEmpty()) listener.onText(message.text, message.isFinal);
                } catch (Exception e) {
                    listener.onError(e.getMessage());
                }
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (running.get()) listener.onError(t.getMessage());
                stop();
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                running.set(false);
                listener.onState("听写已停止");
            }
        });
    }

    public void stop() {
        if (!running.getAndSet(false)) return;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
            }
            recorder.release();
            recorder = null;
        }
        if (socket != null) {
            socket.close(1000, "done");
            socket = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @SuppressWarnings("MissingPermission")
    private void startMicLoop(WebSocket webSocket) {
        audioThread.execute(() -> {
            int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int frameSize = Math.max(minBuffer, SAMPLE_RATE / 5 * 2);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, frameSize * 2);
            byte[] buffer = new byte[frameSize];
            int sequence = 2;
            try {
                recorder.startRecording();
                while (running.get()) {
                    int n = recorder.read(buffer, 0, buffer.length);
                    if (n > 0) {
                        byte[] pcm = buffer;
                        if (n != buffer.length) {
                            pcm = new byte[n];
                            System.arraycopy(buffer, 0, pcm, 0, n);
                        }
                        webSocket.send(ByteString.of(VolcASRProtocol.buildAudioPayload(pcm, sequence++, false)));
                    }
                }
                webSocket.send(ByteString.of(VolcASRProtocol.buildAudioPayload(new byte[0], sequence, true)));
            } catch (Exception e) {
                listener.onError(e.getMessage());
            } finally {
                if (recorder != null) {
                    try {
                        recorder.stop();
                    } catch (RuntimeException ignored) {
                    }
                    recorder.release();
                    recorder = null;
                }
            }
        });
    }

    public interface Listener {
        void onText(String text, boolean isFinal);
        void onState(String state);
        void onError(String message);
    }
}
