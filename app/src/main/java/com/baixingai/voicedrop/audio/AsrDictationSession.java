package com.baixingai.voicedrop.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.VolcASRProtocol;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class AsrDictationSession {
    private static final String TAG = "AsrDictationSession";
    private static final int SAMPLE_RATE = 16000;
    private static final long FINAL_RESULT_TIMEOUT_MS = 3000;
    private static final int ASR_WARMUP_SILENCE_MS = 160;

    // Shared OkHttpClient — connection pooling makes reconnects faster.
    private static final OkHttpClient SHARED_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final AuthStore auth;
    private final Listener listener;
    private final OkHttpClient client;
    private final ExecutorService audioThread = Executors.newSingleThreadExecutor();
    private final ExecutorService senderThread = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean awaitingFinishResult;
    private volatile ScheduledFuture<?> finishTimeout;
    private volatile Runnable finishComplete;
    private WebSocket socket;
    private AudioRecord recorder;
    // Used by finish() to know when the mic thread has actually started recording.
    private volatile CountDownLatch micReady;
    private volatile CountDownLatch socketReady;
    private volatile PendingAudioFrames audioFrames;

    public AsrDictationSession(AuthStore auth, Listener listener) {
        this(auth, listener, SHARED_CLIENT);
    }

    public AsrDictationSession(AuthStore auth, Listener listener, OkHttpClient client) {
        this.auth = auth;
        this.listener = listener;
        this.client = client;
    }

    /**
     * Start capturing microphone audio immediately and open the WebSocket in
     * parallel. Audio frames are buffered until the WebSocket has accepted the
     * ASR config, so short utterances during connection setup are not lost.
     */
    public void start() {
        Log.d(TAG, "start()");
        if (!running.compareAndSet(false, true)) return;
        micReady = new CountDownLatch(1);
        socketReady = new CountDownLatch(1);
        audioFrames = new PendingAudioFrames();
        startMicLoop(audioFrames);
        startSenderLoop(audioFrames, socketReady);

        Request.Builder builder = new Request.Builder().url(Api.agentWs() + "/asr");
        String token = auth.bearer();
        if (token != null && !token.isEmpty()) builder.header("Authorization", "Bearer " + token);

        socket = client.newWebSocket(builder.build(), new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket opened");
                try {
                    webSocket.send(ByteString.of(VolcASRProtocol.buildFullClientPayload("voicedrop-android-edit", SAMPLE_RATE)));
                    listener.onState("正在听写…");
                    CountDownLatch latch = socketReady;
                    if (latch != null) latch.countDown();
                } catch (Exception e) {
                    Log.e(TAG, "onOpen error", e);
                    listener.onError(e.getMessage());
                    running.set(false);
                }
            }

            @Override public void onMessage(WebSocket webSocket, ByteString bytes) {
                try {
                    VolcASRProtocol.ParsedMessage message = VolcASRProtocol.parseServerMessage(bytes.toByteArray());
                    Log.d(TAG, "onMessage: text=\"" + (message.text == null ? "null" : message.text)
                            + "\" isFinal=" + message.isFinal + " isError=" + message.isError
                            + (message.isError ? " error=" + message.errorMessage : ""));
                    if (message.isError) listener.onError(message.errorMessage);
                    if (message.text != null && !message.text.isEmpty()) listener.onText(message.text, message.isFinal);
                    // When finishing, close the WebSocket once the final result arrives.
                    if (awaitingFinishResult && message.isFinal) {
                        Log.d(TAG, "final result received, closing socket");
                        awaitingFinishResult = false;
                        cancelFinishTimeout();
                        notifyFinishComplete();
                        closeSocket();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onMessage parse error", e);
                    listener.onError(e.getMessage());
                }
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure", t);
                if (running.get()) listener.onError(t.getMessage());
                awaitingFinishResult = false;
                cancelFinishTimeout();
                notifyFinishComplete();
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: code=" + code + " reason=" + reason);
                running.set(false);
                awaitingFinishResult = false;
                cancelFinishTimeout();
                notifyFinishComplete();
                listener.onState("听写已停止");
            }
        });

        // Wait for mic thread to confirm it's recording.
        try {
            if (!micReady.await(2000, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "mic thread did not start within 2s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gracefully end dictation: stop reading audio, signal end-of-audio to the server,
     * and wait (up to 3s) for the final recognition result.  Returns immediately;
     * the WebSocket is closed asynchronously when the result arrives or timeout fires.
     */
    public void finish() {
        finish(null);
    }

    public void finish(Runnable onComplete) {
        Log.d(TAG, "finish() called, running=" + running.get());
        finishComplete = onComplete;
        if (!running.get()) {
            notifyFinishComplete();
            return;
        }
        awaitingFinishResult = true;

        // Ensure the mic thread has started recording before we stop it.
        CountDownLatch latch = micReady;
        if (latch != null) {
            try {
                if (!latch.await(2000, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "mic thread never entered read loop");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Signal the mic loop to stop and send isLast=true.
        running.set(false);

        // Schedule a safety timeout.
        finishTimeout = scheduler.schedule(() -> {
            if (awaitingFinishResult) {
                Log.w(TAG, "finish timeout, closing socket");
                awaitingFinishResult = false;
                notifyFinishComplete();
                closeSocket();
            }
        }, FINAL_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelFinishTimeout() {
        if (finishTimeout != null) {
            finishTimeout.cancel(false);
            finishTimeout = null;
        }
    }

    private void notifyFinishComplete() {
        Runnable complete = finishComplete;
        finishComplete = null;
        if (complete != null) complete.run();
    }

    /**
     * Immediately abort dictation.
     */
    public void stop() {
        Log.d(TAG, "stop() called");
        if (!running.getAndSet(false)) return;
        awaitingFinishResult = false;
        cancelFinishTimeout();
        finishComplete = null;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
            }
            recorder.release();
            recorder = null;
        }
        closeSocket();
    }

    private void closeSocket() {
        if (socket != null) {
            Log.d(TAG, "closeSocket()");
            socket.close(1000, "done");
            socket = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @SuppressWarnings("MissingPermission")
    private void startMicLoop(PendingAudioFrames frames) {
        audioThread.execute(() -> {
            int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int frameSize = Math.max(minBuffer, SAMPLE_RATE / 5 * 2);
            Log.d(TAG, "mic loop: minBuffer=" + minBuffer + " frameSize=" + frameSize);
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, frameSize * 2);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized, state=" + recorder.getState());
                listener.onError("AudioRecord 初始化失败");
                CountDownLatch latch = micReady;
                if (latch != null) latch.countDown();
                return;
            }
            byte[] buffer = new byte[frameSize];
            int sequence = 2;
            int totalBytes = 0;
            int maxAmplitude = 0;
            try {
                recorder.startRecording();
                CountDownLatch latch = micReady;
                if (latch != null) latch.countDown();

                Log.d(TAG, "recording started");
                while (running.get()) {
                    int n = recorder.read(buffer, 0, buffer.length);
                    if (n > 0) {
                        totalBytes += n;
                        int frameRms = computeRms(buffer, n);
                        maxAmplitude = Math.max(maxAmplitude, frameRms);
                        if (totalBytes % 16000 < n) {
                            Log.d(TAG, "audio frame: n=" + n + " rms=" + frameRms + " maxRms=" + maxAmplitude);
                        }
                        byte[] pcm = buffer;
                        if (n != buffer.length) {
                            pcm = new byte[n];
                            System.arraycopy(buffer, 0, pcm, 0, n);
                        }
                        frames.offer(pcm, false);
                    }
                }
                Log.d(TAG, "mic loop exiting, totalBytes=" + totalBytes + " maxAmplitude=" + maxAmplitude);
                frames.offer(new byte[0], true);
            } catch (Exception e) {
                Log.e(TAG, "mic loop exception", e);
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

    private void startSenderLoop(PendingAudioFrames frames, CountDownLatch ready) {
        senderThread.execute(() -> {
            try {
                if (!ready.await(5000, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "WebSocket not ready within 5s");
                    listener.onError("听写连接超时");
                    running.set(false);
                    closeSocket();
                    return;
                }
                int sequence = 2;
                WebSocket ws = socket;
                if (ws == null) return;
                ws.send(ByteString.of(VolcASRProtocol.buildAudioPayload(
                        warmupSilencePcm(SAMPLE_RATE, ASR_WARMUP_SILENCE_MS), sequence++, false)));
                Thread.sleep(ASR_WARMUP_SILENCE_MS);
                while (true) {
                    PendingAudioFrames.Frame frame = frames.take();
                    ws = socket;
                    if (ws == null) return;
                    ws.send(ByteString.of(VolcASRProtocol.buildAudioPayload(frame.data, sequence++, frame.isLast)));
                    if (frame.isLast) return;
                    if (frames.queuedCount() > 0) {
                        Thread.sleep(frameDurationMs(frame.data, SAMPLE_RATE));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "sender loop exception", e);
                listener.onError(e.getMessage());
            }
        });
    }

    /** Compute RMS amplitude from 16-bit little-endian PCM samples. */
    private static int computeRms(byte[] data, int bytesRead) {
        long sum = 0;
        int count = 0;
        for (int i = 0; i + 1 < bytesRead; i += 2) {
            short sample = (short) (data[i] | (data[i + 1] << 8));
            sum += sample * sample;
            count++;
        }
        return count > 0 ? (int) Math.sqrt(sum / count) : 0;
    }

    static byte[] warmupSilencePcm(int sampleRate, int durationMs) {
        int samples = Math.max(0, sampleRate * durationMs / 1000);
        return new byte[samples * 2];
    }

    static long frameDurationMs(byte[] pcm16Mono, int sampleRate) {
        if (pcm16Mono == null || sampleRate <= 0) return 0;
        return (pcm16Mono.length / 2L) * 1000L / sampleRate;
    }

    public interface Listener {
        void onText(String text, boolean isFinal);
        void onState(String state);
        void onError(String message);
    }
}
