package com.baixingai.voicedrop;

import com.baixingai.voicedrop.audio.RecordingQuality;

import org.junit.Test;

import static org.junit.Assert.*;

public class RecordingQualityTest {
    @Test
    public void flagsVeryLowPeakAsSilent() {
        assertTrue(RecordingQuality.looksSilent(80, 12.0));
    }

    @Test
    public void acceptsAudiblePeak() {
        assertFalse(RecordingQuality.looksSilent(1200, 12.0));
    }

    @Test
    public void ignoresExtremelyShortTakes() {
        assertFalse(RecordingQuality.looksSilent(0, 0.4));
    }
}
