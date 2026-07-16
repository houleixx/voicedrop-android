package com.baixingai.voicedrop.ui;

public final class HoldToTalkTranscript {
    private String latestText = "";

    public synchronized void clear() {
        latestText = "";
    }

    public synchronized void accept(String text, boolean isFinal) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return;
        // Volc ASR sends cumulative transcript snapshots. Always keep the
        // newest snapshot, even when a late partial follows an early final.
        latestText = trimmed;
    }

    public synchronized String bestText() {
        return latestText;
    }

    public synchronized String bubbleText() {
        String text = bestText();
        return text.isEmpty() ? "在听…" : text;
    }
}
