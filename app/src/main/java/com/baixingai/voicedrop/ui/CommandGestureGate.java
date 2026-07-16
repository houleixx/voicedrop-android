package com.baixingai.voicedrop.ui;

/** Main-thread state gate for the home record/voice-command gesture. */
public final class CommandGestureGate {
    private enum State {
        IDLE,
        PRIMING,
        TALKING,
        FINISHING,
        HANDOFF
    }

    private State state = State.IDLE;

    public boolean beginPress() {
        if (state != State.IDLE) return false;
        state = State.PRIMING;
        return true;
    }

    public boolean confirmLongPress() {
        if (state != State.PRIMING) return false;
        state = State.TALKING;
        return true;
    }

    public boolean beginHandoff() {
        if (state != State.PRIMING) return false;
        state = State.HANDOFF;
        return true;
    }

    public boolean beginFinish() {
        if (state != State.TALKING) return false;
        state = State.FINISHING;
        return true;
    }

    public void complete() {
        state = State.IDLE;
    }

    /** Cancel a press or quick-tap handoff without interrupting command finalization. */
    public void cancelPendingPress() {
        if (state == State.PRIMING || state == State.HANDOFF) state = State.IDLE;
    }

    public void cancel() {
        state = State.IDLE;
    }
}
