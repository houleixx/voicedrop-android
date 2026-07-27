package com.baixingai.voicedrop.core;

public final class RecordingWaveform {
    private static final double MAX_AMPLITUDE = 32767.0;
    private static final double NOISE_FLOOR = 0.015;
    private static final double RESPONSE_EXPONENT = 0.35;

    private RecordingWaveform() {}

    public static double visualLevel(int amplitude) {
        double normalized = Math.max(0.0, Math.min(1.0, amplitude / MAX_AMPLITUDE));
        if (normalized <= NOISE_FLOOR) return 0.0;
        double audible = (normalized - NOISE_FLOOR) / (1.0 - NOISE_FLOOR);
        return Math.pow(audible, RESPONSE_EXPONENT);
    }

    public static double heightScale(double visualLevel) {
        double level = Math.max(0.0, Math.min(1.0, visualLevel));
        return Math.min(1.0, 0.06 + level * 1.30);
    }
}
