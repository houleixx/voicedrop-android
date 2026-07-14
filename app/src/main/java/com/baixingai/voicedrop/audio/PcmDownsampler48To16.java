package com.baixingai.voicedrop.audio;

/** Stateful PCM16 little-endian 48 kHz to 16 kHz mono downsampler. */
final class PcmDownsampler48To16 {
    static final int INPUT_SAMPLE_RATE = 48_000;
    static final int OUTPUT_SAMPLE_RATE = 16_000;

    private int pendingSum;
    private int pendingCount;

    int downsample(byte[] input, int length, byte[] output) {
        return downsample(input, length, output, 0);
    }

    int downsample(byte[] input, int length, byte[] output, int outputOffset) {
        if (input == null || output == null) throw new NullPointerException();
        if (length < 0 || length > input.length || (length & 1) != 0) {
            throw new IllegalArgumentException("PCM16 input length must be even and in bounds");
        }
        if (outputOffset < 0 || outputOffset > output.length) {
            throw new IllegalArgumentException("Output offset is out of bounds");
        }

        int out = outputOffset;
        for (int i = 0; i < length; i += 2) {
            int sample = (short) ((input[i] & 0xff) | (input[i + 1] << 8));
            pendingSum += sample;
            pendingCount++;
            if (pendingCount == 3) {
                if (out + 2 > output.length) {
                    throw new IllegalArgumentException("Output buffer is too small");
                }
                writeSample(output, out, pendingSum / 3);
                out += 2;
                pendingSum = 0;
                pendingCount = 0;
            }
        }
        return out - outputOffset;
    }

    int flush(byte[] output) {
        if (output == null) throw new NullPointerException();
        if (pendingCount == 0) return 0;
        if (output.length < 2) throw new IllegalArgumentException("Output buffer is too small");
        writeSample(output, 0, pendingSum / pendingCount);
        pendingSum = 0;
        pendingCount = 0;
        return 2;
    }

    int pendingSamples() {
        return pendingCount;
    }

    private static void writeSample(byte[] output, int offset, int sample) {
        int clamped = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
        output[offset] = (byte) (clamped & 0xff);
        output[offset + 1] = (byte) ((clamped >>> 8) & 0xff);
    }
}
