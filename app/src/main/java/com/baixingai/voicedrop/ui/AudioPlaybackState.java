package com.baixingai.voicedrop.ui;

public final class AudioPlaybackState {
    public enum Mode {
        IDLE,
        LOADING,
        PLAYING
    }

    private Mode mode = Mode.IDLE;
    private float progress;

    public Mode mode() {
        return mode;
    }

    public float progress() {
        return progress;
    }

    public boolean requestPlay() {
        if (mode != Mode.IDLE) return false;
        mode = Mode.LOADING;
        progress = 0f;
        return true;
    }

    public boolean requestStop() {
        if (mode == Mode.IDLE) return false;
        reset();
        return true;
    }

    public void started() {
        mode = Mode.PLAYING;
        progress = 0f;
    }

    public void failed() {
        reset();
    }

    public void completed() {
        reset();
    }

    public void progress(int positionMs, int durationMs) {
        if (durationMs <= 0) {
            progress = 0f;
            return;
        }
        float next = positionMs / (float) durationMs;
        progress = Math.max(0f, Math.min(1f, next));
    }

    private void reset() {
        mode = Mode.IDLE;
        progress = 0f;
    }
}
