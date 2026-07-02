package com.baixingai.voicedrop.ui;

public final class HoldToTalkGesture {
    private HoldToTalkGesture() {
    }

    public static boolean shouldCancel(float startRawY, float currentRawY, float cancelDistancePx) {
        return startRawY - currentRawY >= cancelDistancePx;
    }

    public static boolean shouldAbortOnEnd(boolean actionCanceled, boolean draggedToCancel) {
        return draggedToCancel;
    }
}
