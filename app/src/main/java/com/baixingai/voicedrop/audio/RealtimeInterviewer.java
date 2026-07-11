package com.baixingai.voicedrop.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import com.baixingai.voicedrop.data.AuthStore;

public final class RealtimeInterviewer {
    private static final int AI_RATE = 24000;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RealtimeSession session;
    private volatile boolean muted;
    private volatile boolean active;
    private volatile RealtimeSession.State state = RealtimeSession.State.IDLE;
    private int reconnectAttempt;
    private Runnable reconnectRunnable;
    private AudioTrack aiTrack;
    private Runnable onStateChanged;

    public RealtimeInterviewer(Context context, AuthStore auth) {
        this.context = context.getApplicationContext();
        this.session = new RealtimeSession(auth, new RealtimeSession.Listener() {
            @Override public void onState(RealtimeSession.State s) {
                state = s;
                if (s == RealtimeSession.State.LIVE) reconnectAttempt = 0;
                if (s == RealtimeSession.State.DEGRADED) scheduleReconnect();
                notifyChanged();
            }

            @Override public void onAudioDelta(byte[] pcm16le24k) {
                beginAiTurn();
                playAI(pcm16le24k);
            }

            @Override public void onResponseCreated() {
                beginAiTurn();
            }

            @Override public void onResponseDone() {
                main.postDelayed(RealtimeInterviewer.this::openMic, 500);
            }
        });
    }

    public void setOnStateChanged(Runnable onStateChanged) {
        this.onStateChanged = onStateChanged;
    }

    public void toggle() {
        if (active) stop();
        else start();
    }

    public void start() {
        if (active) return;
        active = true;
        muted = false;
        reconnectAttempt = 0;
        session.connect();
        notifyChanged();
    }

    public void stop() {
        active = false;
        muted = false;
        if (reconnectRunnable != null) {
            main.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }
        reconnectAttempt = 0;
        releasePlayer();
        session.disconnect();
        notifyChanged();
    }

    public boolean interviewActive() {
        return active;
    }

    public String stateText() {
        if (!active) return "";
        if (state == RealtimeSession.State.CONNECTING) return "AI 连接中…";
        if (state == RealtimeSession.State.LIVE) return muted ? "AI 正在说话" : "AI 采访中 · 再点一下结束";
        if (state == RealtimeSession.State.DEGRADED) return "AI 已断开 · 录音继续";
        return "AI 采访中";
    }

    public void onPcm16(byte[] pcm16le, int sampleRate) {
        if (!active || muted || pcm16le == null || pcm16le.length == 0) return;
        session.appendAudio(MuLaw.pcm16ToPcmu8k(pcm16le, sampleRate));
    }

    private void beginAiTurn() {
        muted = true;
        notifyChanged();
    }

    private void openMic() {
        if (!active) return;
        // Muting can cut a speaker off mid-sentence. Without clearing that fragment,
        // the first fresh audio frame can make server VAD treat it as a completed turn.
        session.clearInputBuffer();
        muted = false;
        notifyChanged();
    }

    private void scheduleReconnect() {
        if (!active || reconnectRunnable != null || !(reconnectAttempt < 6)) return;
        long delayMs = (1L << reconnectAttempt) * 1000L;
        reconnectAttempt++;
        reconnectRunnable = () -> {
            reconnectRunnable = null;
            if (!active || state != RealtimeSession.State.DEGRADED || reconnectAttempt > 6) return;
            session.disconnect();
            session.connect();
        };
        main.postDelayed(reconnectRunnable, delayMs);
    }

    private void playAI(byte[] pcm) {
        if (pcm == null || pcm.length == 0 || !active) return;
        AudioTrack track = player();
        if (track == null) return;
        track.write(pcm, 0, pcm.length);
    }

    private AudioTrack player() {
        if (aiTrack != null) return aiTrack;
        int min = AudioTrack.getMinBufferSize(AI_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        aiTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(AI_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(Math.max(min, AI_RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        aiTrack.play();
        return aiTrack;
    }

    private void releasePlayer() {
        AudioTrack local = aiTrack;
        aiTrack = null;
        if (local == null) return;
        try { local.stop(); } catch (Exception ignored) {}
        local.release();
    }

    private void notifyChanged() {
        Runnable r = onStateChanged;
        if (r != null) main.post(r);
    }
}
