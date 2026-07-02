package com.baixingai.voicedrop.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AsrDictationSessionTest {
    @Test public void warmupSilenceUsesSixteenBitMonoPcm() {
        byte[] silence = AsrDictationSession.warmupSilencePcm(16000, 160);

        assertEquals(5120, silence.length);
        for (byte value : silence) {
            assertEquals(0, value);
        }
    }

    @Test public void frameDurationMatchesSixteenBitMonoPcmLength() {
        assertEquals(200, AsrDictationSession.frameDurationMs(new byte[6400], 16000));
        assertEquals(0, AsrDictationSession.frameDurationMs(new byte[0], 16000));
    }
}
