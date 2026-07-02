package com.baixingai.voicedrop.ui;

public final class HoldToTalkTranscript {
    private final StringBuilder finalText = new StringBuilder();
    private String partialText = "";

    public synchronized void clear() {
        finalText.setLength(0);
        partialText = "";
    }

    public synchronized void accept(String text, boolean isFinal) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return;
        if (isFinal) {
            if (finalText.length() > 0 && finalText.charAt(finalText.length() - 1) != ' ') {
                finalText.append(' ');
            }
            finalText.append(trimmed);
            partialText = "";
        } else {
            partialText = trimmed;
        }
    }

    public synchronized String bestText() {
        String text = finalText.toString().trim();
        return text.isEmpty() ? partialText.trim() : text;
    }

    public synchronized String bubbleText() {
        String text = bestText();
        return text.isEmpty() ? "在听…" : text;
    }
}
