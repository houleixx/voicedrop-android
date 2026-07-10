package com.baixingai.voicedrop.audio;

import org.junit.Test;

import static org.junit.Assert.*;

public class MuLawTest {
    @Test
    public void encodesSilenceToStableMuLawByte() {
        byte[] pcm = new byte[320];
        byte[] out = MuLaw.pcm16ToPcmu8k(pcm, 16000);

        assertEquals(80, out.length);
        for (byte b : out) assertEquals(out[0], b);
    }

    @Test
    public void positiveAndNegativeSamplesEncodeDifferently() {
        byte[] pcm = new byte[] {
                (byte) 0xff, 0x7f,
                0x00, (byte) 0x80
        };

        byte[] out = MuLaw.pcm16ToPcmu8k(pcm, 8000);

        assertEquals(2, out.length);
        assertNotEquals(out[0], out[1]);
    }
}
