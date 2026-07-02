package com.baixingai.voicedrop.audio;

public final class RecordingQuality {
    private static final int SILENT_PEAK_THRESHOLD = 300;
    private static final double MIN_CHECK_SECONDS = 1.0;

    private RecordingQuality() {}

    public static boolean looksSilent(int peakAmplitude, double durationSeconds) {
        return durationSeconds >= MIN_CHECK_SECONDS && peakAmplitude < SILENT_PEAK_THRESHOLD;
    }
}
