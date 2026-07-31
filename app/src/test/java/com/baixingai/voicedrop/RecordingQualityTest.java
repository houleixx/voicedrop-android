package com.baixingai.voicedrop;

import com.baixingai.voicedrop.audio.RecordingQuality;

import org.junit.Test;

import static org.junit.Assert.*;

public class RecordingQualityTest {
    @Test
    public void flagsRecordingShorterThanFourSeconds() {
        assertTrue(RecordingQuality.isTooShort(0));
        assertTrue(RecordingQuality.isTooShort(3.999));
    }

    @Test
    public void acceptsRecordingAtFourSeconds() {
        assertFalse(RecordingQuality.isTooShort(4.0));
        assertFalse(RecordingQuality.isTooShort(12.0));
    }

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
