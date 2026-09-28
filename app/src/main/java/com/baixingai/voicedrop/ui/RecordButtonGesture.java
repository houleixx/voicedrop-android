package com.baixingai.voicedrop.ui;

/** One recording launch per press, whether released quickly or held like iOS. */
public final class RecordButtonGesture {
    public static final long HOLD_MILLIS = 400;
    private boolean pending;
    private float downX;
    private float downY;

    public void begin(float x, float y) {
        pending = true;
        downX = x;
        downY = y;
    }

    /** Once dragged outside the tolerance, returning cannot re-arm this press. */
    public boolean move(float x, float y, float maximumDistance) {
        if (!pending) return false;
        float dx = x - downX;
        float dy = y - downY;
        if (dx * dx + dy * dy <= maximumDistance * maximumDistance) return false;
        cancel();
        return true;
    }

    public boolean hold() {
        return release();
    }

    public boolean release() {
        boolean launch = pending;
        pending = false;
        return launch;
    }

    public void cancel() {
        pending = false;
    }
}
