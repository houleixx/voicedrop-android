package com.baixingai.voicedrop.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class PcmDownsampler48To16Test {
    @Test
    public void averagesEveryThreePcm16Samples() {
        PcmDownsampler48To16 downsampler = new PcmDownsampler48To16();
        byte[] output = new byte[4];

        int written = downsampler.downsample(pcm(300, 600, 900, -300, -600, -900), 12, output);

        assertEquals(4, written);
        assertArrayEquals(pcm(600, -600), Arrays.copyOf(output, written));
    }

    @Test
    public void preservesPartialGroupAcrossReadBoundaries() {
        PcmDownsampler48To16 split = new PcmDownsampler48To16();
        byte[] splitOutput = new byte[8];
        int first = split.downsample(pcm(300, 600), 4, splitOutput);
        int second = split.downsample(pcm(900, 1200, 1500, 1800), 8,
                splitOutput, first);

        PcmDownsampler48To16 single = new PcmDownsampler48To16();
        byte[] singleOutput = new byte[8];
        int singleWritten = single.downsample(
                pcm(300, 600, 900, 1200, 1500, 1800), 12, singleOutput);

        assertEquals(0, first);
        assertEquals(singleWritten, second);
        assertArrayEquals(Arrays.copyOf(singleOutput, singleWritten),
                Arrays.copyOf(splitOutput, second));
    }

    @Test
    public void flushWritesAverageOfOneOrTwoRemainingSamples() {
        PcmDownsampler48To16 one = new PcmDownsampler48To16();
        byte[] oneOutput = new byte[2];
        assertEquals(0, one.downsample(pcm(1234), 2, oneOutput));
        assertEquals(2, one.flush(oneOutput));
        assertArrayEquals(pcm(1234), oneOutput);
        assertEquals(0, one.flush(oneOutput));

        PcmDownsampler48To16 two = new PcmDownsampler48To16();
        byte[] twoOutput = new byte[2];
        assertEquals(0, two.downsample(pcm(1000, 2000), 4, twoOutput));
        assertEquals(2, two.flush(twoOutput));
        assertArrayEquals(pcm(1500), twoOutput);
    }

    @Test
    public void readsAndWritesSignedLittleEndianExtremes() {
        PcmDownsampler48To16 downsampler = new PcmDownsampler48To16();
        byte[] output = new byte[4];

        int written = downsampler.downsample(
                pcm(Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE,
                        Short.MIN_VALUE, Short.MIN_VALUE, Short.MIN_VALUE),
                12, output);

        assertEquals(4, written);
        assertArrayEquals(pcm(Short.MAX_VALUE, Short.MIN_VALUE), output);
    }

    private static byte[] pcm(int... samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            short sample = (short) samples[i];
            bytes[i * 2] = (byte) (sample & 0xff);
            bytes[i * 2 + 1] = (byte) ((sample >>> 8) & 0xff);
        }
        return bytes;
    }
}
