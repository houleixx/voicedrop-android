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
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class AsrDictationSession {
    private static final String TAG = "AsrDictationSession";
    private static final int CAPTURE_SAMPLE_RATE = 48_000;
    private static final int ASR_SAMPLE_RATE = 16_000;
    private static final int CAPTURE_CHUNK_MS = 100;
    private static final long FINAL_RESULT_TIMEOUT_MS = 3000;
    private static final long TAIL_CAPTURE_MS = 250;

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
    private final AtomicBoolean activated = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final Object executorLock = new Object();
    private boolean executorsShutdown;
    private volatile boolean awaitingFinishResult;
    private volatile boolean lastAudioSent;
    private volatile ScheduledFuture<?> finishTimeout;
    private volatile ScheduledFuture<?> tailStopFuture;
    private final AtomicReference<Runnable> finishComplete = new AtomicReference<>();
    private WebSocket socket;
    private volatile int turnGeneration;
    private final Object recorderLock = new Object();
    private AudioRecord recorder;
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

    private static String bearerToken(AuthStore auth) {
        if (auth == null) return "";
        String token = auth.bearer();
        return token == null ? "" : token;
    }

    private static Request asrRequest(String token) {
        Request.Builder builder = new Request.Builder().url(Api.agentWs() + "/asr");
        if (token != null && !token.isEmpty()) builder.header("Authorization", "Bearer " + token);
        return builder.build();
    }

    /**
     * Start capturing microphone audio immediately and open the WebSocket in
     * parallel. Audio frames are buffered until the WebSocket has accepted the
     * ASR config, so short utterances during connection setup are not lost.
     */
    public void start() {
        if (!prepareCapture()) return;
        activate();
        if (running.get()) startMicLoop(audioFrames);
    }

    /** Start microphone capture without opening the ASR WebSocket. */
    public void startCapture() {
        if (!prepareCapture()) return;
        startMicLoop(audioFrames);
    }

    private boolean prepareCapture() {
        Log.d(TAG, "prepareCapture()");
        if (!running.compareAndSet(false, true)) return false;
        cancelTailStop();
        terminated.set(false);
        activated.set(false);
        awaitingFinishResult = false;
        lastAudioSent = false;
        audioFrames = new PendingAudioFrames();
        ++turnGeneration;
        return true;
    }

    /** Open the ASR connection and drain audio captured since startCapture(). */
    public void activate() {
        if (!running.get() || !activated.compareAndSet(false, true)) return;
        PendingAudioFrames frames = audioFrames;
        if (frames == null) {
            activated.set(false);
            return;
        }
        socketReady = new CountDownLatch(1);
        final int generation = turnGeneration;
        startSenderLoop(frames, socketReady);

        String token = bearerToken(auth);
        socket = client.newWebSocket(asrRequest(token), new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                if (!isCurrentTurn(webSocket, generation)) return;
                Log.d(TAG, "WebSocket opened");
                try {
                    webSocket.send(ByteString.of(VolcASRProtocol.buildFullClientPayload(
                            "voicedrop-android-edit", ASR_SAMPLE_RATE)));
                    onSocketReady(webSocket);
                } catch (Exception e) {
                    Log.e(TAG, "onOpen error", e);
                    terminateWithError(e.getMessage());
                }
            }

            @Override public void onMessage(WebSocket webSocket, ByteString bytes) {
                if (!isCurrentTurn(webSocket, generation)) return;
                handleSocketMessage(bytes);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                handleSocketFailure(webSocket, generation, t);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                handleSocketClosed(webSocket, generation, code, reason);
            }
        });
    }

    private boolean isCurrentTurn(WebSocket webSocket, int generation) {
        return generation == turnGeneration && webSocket == socket;
    }

    private void onSocketReady(WebSocket webSocket) {
        listener.onState("正在听写…");
        CountDownLatch latch = socketReady;
        if (latch != null) latch.countDown();
    }

    private void handleSocketMessage(ByteString bytes) {
        try {
            VolcASRProtocol.ParsedMessage message = VolcASRProtocol.parseServerMessage(bytes.toByteArray());
            Log.d(TAG, "onMessage: text=\"" + (message.text == null ? "null" : message.text)
                    + "\" isFinal=" + message.isFinal + " isError=" + message.isError
                    + (message.isError ? " error=" + message.errorMessage : ""));
            if (message.isError) listener.onError(message.errorMessage);
            if (message.text != null && !message.text.isEmpty()) listener.onText(message.text, message.isFinal);
            // When finishing, close the WebSocket once the final result arrives.
            if (awaitingFinishResult && lastAudioSent && message.isFinal) {
                Log.d(TAG, "final result received, closing socket");
                terminated.set(true);
                awaitingFinishResult = false;
                cancelFinishTimeout();
                notifyFinishComplete();
                closeSocket();
                shutdownExecutors();
            }
        } catch (Exception e) {
            Log.e(TAG, "onMessage parse error", e);
            terminateWithError(e.getMessage());
        }
    }

    private void handleSocketFailure(WebSocket webSocket, int generation, Throwable t) {
        if (!isCurrentTurn(webSocket, generation)) return;
        Log.e(TAG, "WebSocket failure", t);
        terminateWithError(t.getMessage());
    }

    private void handleSocketClosed(WebSocket webSocket, int generation, int code, String reason) {
        if (!isCurrentTurn(webSocket, generation)) return;
        Log.d(TAG, "WebSocket closed: code=" + code + " reason=" + reason);
        running.set(false);
        terminated.set(true);
        awaitingFinishResult = false;
        cancelTailStop();
        cancelFinishTimeout();
        notifyFinishComplete();
        shutdownExecutors();
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
        finishComplete.set(onComplete);
        if (!running.get()) {
            notifyFinishComplete();
            return;
        }
        if (!activated.get()) {
            finishComplete.set(null);
            stop(onComplete);
            return;
        }
        awaitingFinishResult = true;
        if (!scheduleTailStop()) {
            running.set(false);
            awaitingFinishResult = false;
            notifyFinishComplete();
            closeSocket();
        }

        // The sender arms the final-result timeout only after it has actually
        // sent the last audio frame. Connection setup and buffered audio do not
        // consume the server's response window.
    }

    private boolean scheduleTailStop() {
        cancelTailStop();
        synchronized (executorLock) {
            if (executorsShutdown) return false;
            tailStopFuture = scheduler.schedule(() -> {
                tailStopFuture = null;
                // Keep recording briefly after ACTION_UP so the final spoken
                // syllable already buffered by AudioRecord is included.
                running.set(false);
            }, TAIL_CAPTURE_MS, TimeUnit.MILLISECONDS);
            return true;
        }
    }

    private synchronized void cancelTailStop() {
        if (tailStopFuture != null) {
            tailStopFuture.cancel(false);
            tailStopFuture = null;
        }
    }

    private void armFinishTimeout() {
        synchronized (executorLock) {
            if (executorsShutdown || !awaitingFinishResult || finishTimeout != null) return;
            finishTimeout = scheduler.schedule(() -> {
                if (awaitingFinishResult) {
                    Log.w(TAG, "finish timeout, closing socket");
                    terminated.set(true);
                    awaitingFinishResult = false;
                    notifyFinishComplete();
                    closeSocket();
                    shutdownExecutors();
                }
            }, FINAL_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelFinishTimeout() {
        if (finishTimeout != null) {
            finishTimeout.cancel(false);
            finishTimeout = null;
        }
    }

    private void notifyFinishComplete() {
        Runnable complete = finishComplete.getAndSet(null);
        if (complete != null) complete.run();
    }

    /**
     * Immediately abort dictation.
     */
    public void stop() {
        stop(null);
    }

    public void stop(Runnable onStopped) {
        Log.d(TAG, "stop() called");
        turnGeneration++;
        terminated.set(true);
        running.set(false);
        activated.set(false);
        awaitingFinishResult = false;
        cancelTailStop();
        cancelFinishTimeout();
        finishComplete.set(null);
        PendingAudioFrames frames = audioFrames;
        if (frames != null) frames.offer(new byte[0], true);
        AudioRecord localRecorder;
        synchronized (recorderLock) {
            localRecorder = recorder;
            recorder = null;
        }
        releaseRecorder(localRecorder);
        closeSocket();
        boolean runCallbackDirectly = false;
        synchronized (executorLock) {
            if (onStopped != null) {
                if (executorsShutdown) runCallbackDirectly = true;
                else audioThread.execute(onStopped);
            }
            executorsShutdown = true;
            senderThread.shutdownNow();
            scheduler.shutdownNow();
            audioThread.shutdown();
        }
        if (runCallbackDirectly) onStopped.run();
    }

    private void shutdownExecutors() {
        synchronized (executorLock) {
            if (executorsShutdown) return;
            executorsShutdown = true;
            senderThread.shutdownNow();
            scheduler.shutdownNow();
            audioThread.shutdown();
        }
    }

    private void terminateWithError(String message) {
        if (!terminated.compareAndSet(false, true)) return;
        running.set(false);
        activated.set(false);
        awaitingFinishResult = false;
        cancelTailStop();
        cancelFinishTimeout();
        PendingAudioFrames frames = audioFrames;
        if (frames != null) frames.offer(new byte[0], true);
        AudioRecord localRecorder;
        synchronized (recorderLock) {
            localRecorder = recorder;
            recorder = null;
        }
        releaseRecorder(localRecorder);
        listener.onError(message == null || message.isEmpty() ? "听写失败" : message);
        notifyFinishComplete();
        closeSocket();
        shutdownExecutors();
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
            AudioRecord localRecorder = null;
            if (!running.get()) {
                if (awaitingFinishResult) frames.offer(new byte[0], true);
                return;
            }
            int minBuffer = AudioRecord.getMinBufferSize(CAPTURE_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int chunkBytes = captureChunkBytes(CAPTURE_SAMPLE_RATE, CAPTURE_CHUNK_MS);
            int recorderBufferBytes = Math.max(minBuffer, chunkBytes * 2);
            Log.d(TAG, "mic loop: captureRate=" + CAPTURE_SAMPLE_RATE
                    + " asrRate=" + ASR_SAMPLE_RATE + " minBuffer=" + minBuffer
                    + " chunkBytes=" + chunkBytes + " recorderBuffer=" + recorderBufferBytes);
            localRecorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    CAPTURE_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, recorderBufferBytes);
            synchronized (recorderLock) {
                recorder = localRecorder;
            }
            if (localRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized, state=" + localRecorder.getState());
                terminateWithError("AudioRecord 初始化失败");
                return;
            }
            byte[] captureBuffer = new byte[chunkBytes];
            byte[] convertedBuffer = new byte[chunkBytes];
            PcmDownsampler48To16 downsampler = new PcmDownsampler48To16();
            int capturedBytes = 0;
            int convertedBytes = 0;
            int maxAmplitude = 0;
            try {
                localRecorder.startRecording();
                Log.d(TAG, "recording started, actualRate=" + localRecorder.getSampleRate());
                while (running.get()) {
                    int n = localRecorder.read(captureBuffer, 0, captureBuffer.length);
                    if (n > 0) {
                        int evenBytes = n & ~1;
                        if (evenBytes == 0) continue;
                        capturedBytes += evenBytes;
                        int written = downsampler.downsample(
                                captureBuffer, evenBytes, convertedBuffer);
                        if (written == 0) continue;
                        convertedBytes += written;
                        int frameRms = computeRms(convertedBuffer, written);
                        maxAmplitude = Math.max(maxAmplitude, frameRms);
                        if (convertedBytes % ASR_SAMPLE_RATE < written) {
                            Log.d(TAG, "audio frame: captured=" + evenBytes
                                    + " converted=" + written + " rms=" + frameRms
                                    + " maxRms=" + maxAmplitude);
                        }
                        byte[] pcm16 = new byte[written];
                        System.arraycopy(convertedBuffer, 0, pcm16, 0, written);
                        frames.offer(pcm16, false);
                    }
                }
                int flushed = downsampler.flush(convertedBuffer);
                if (flushed > 0) {
                    byte[] pcm16 = new byte[flushed];
                    System.arraycopy(convertedBuffer, 0, pcm16, 0, flushed);
                    frames.offer(pcm16, false);
                    convertedBytes += flushed;
                }
                Log.d(TAG, "mic loop exiting, capturedBytes=" + capturedBytes
                        + " convertedBytes=" + convertedBytes + " maxRms=" + maxAmplitude);
                frames.offer(new byte[0], true);
            } catch (Exception e) {
                Log.e(TAG, "mic loop exception", e);
                terminateWithError(e.getMessage());
            } finally {
                boolean shouldRelease;
                synchronized (recorderLock) {
                    shouldRelease = recorder == localRecorder;
                    if (shouldRelease) recorder = null;
                }
                if (shouldRelease) releaseRecorder(localRecorder);
            }
        });
    }

    private void releaseRecorder(AudioRecord localRecorder) {
        if (localRecorder == null) return;
        try {
            localRecorder.stop();
        } catch (RuntimeException ignored) {
        }
        localRecorder.release();
    }

    private void startSenderLoop(PendingAudioFrames frames, CountDownLatch ready) {
        senderThread.execute(() -> {
            try {
                if (!ready.await(5000, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "WebSocket not ready within 5s");
                    terminateWithError("听写连接超时");
                    return;
                }
                int sequence = 2;
                WebSocket ws = socket;
                if (ws == null) return;
                while (true) {
                    PendingAudioFrames.Frame frame = frames.take();
                    ws = socket;
                    if (ws == null) return;
                    boolean sent = ws.send(ByteString.of(VolcASRProtocol.buildAudioPayload(frame.data, sequence++, frame.isLast)));
                    if (frame.isLast) {
                        if (sent) {
                            lastAudioSent = true;
                            armFinishTimeout();
                        }
                        else {
                            awaitingFinishResult = false;
                            notifyFinishComplete();
                            closeSocket();
                            shutdownExecutors();
                        }
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "sender loop exception", e);
                terminateWithError(e.getMessage());
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

    static int captureChunkBytes(int sampleRate, int durationMs) {
        if (sampleRate <= 0 || durationMs <= 0) return 0;
        return sampleRate * durationMs / 1000 * 2;
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
