package com.baixingai.voicedrop.audio;

public final class RecordingQuality {
    public static final double MIN_DURATION_SECONDS = 4.0;
    private static final int SILENT_PEAK_THRESHOLD = 300;
    private static final double MIN_CHECK_SECONDS = 1.0;

    private RecordingQuality() {}

    public static boolean isTooShort(double durationSeconds) {
        return durationSeconds < MIN_DURATION_SECONDS;
    }

    public static boolean discardIfTooShort(AudioRecorder.Take take) {
        if (take == null || !isTooShort(take.duration)) return false;
        if (take.file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            take.file.delete();
        }
        return true;
    }

    public static boolean looksSilent(int peakAmplitude, double durationSeconds) {
        return durationSeconds >= MIN_CHECK_SECONDS && peakAmplitude < SILENT_PEAK_THRESHOLD;
    }
}
