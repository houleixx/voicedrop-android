package com.baixingai.voicedrop.audio;

public final class MuLaw {
    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;

    private MuLaw() {}

    public static byte[] pcm16ToPcmu8k(byte[] pcm16le, int sampleRate) {
        if (pcm16le == null || pcm16le.length < 2 || sampleRate <= 0) return new byte[0];
        int samples = pcm16le.length / 2;
        int step = Math.max(1, sampleRate / 8000);
        byte[] out = new byte[(samples + step - 1) / step];
        int j = 0;
        for (int i = 0; i < samples; i += step) {
            int lo = pcm16le[i * 2] & 0xff;
            int hi = pcm16le[i * 2 + 1];
            short sample = (short) (lo | (hi << 8));
            out[j++] = linearToMuLaw(sample);
        }
        if (j == out.length) return out;
        byte[] exact = new byte[j];
        System.arraycopy(out, 0, exact, 0, j);
        return exact;
    }

    private static byte linearToMuLaw(int sample) {
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) sample = -sample;
        if (sample > CLIP) sample = CLIP;
        sample += BIAS;
        int exponent = 7;
        for (int mask = 0x4000; (sample & mask) == 0 && exponent > 0; mask >>= 1) exponent--;
        int mantissa = (sample >> (exponent + 3)) & 0x0f;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }
}
