package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecordingWaveformTest {
    @Test
    public void visualLevelPreservesSilenceAndFullScaleBounds() {
        assertEquals(0.0, RecordingWaveform.visualLevel(0), 0.0001);
        assertEquals(1.0, RecordingWaveform.visualLevel(32767), 0.0001);
        assertEquals(0.0, RecordingWaveform.visualLevel(-1), 0.0001);
        assertEquals(1.0, RecordingWaveform.visualLevel(50000), 0.0001);
    }

    @Test
    public void visualLevelMakesOrdinaryVoiceClearlyMoreVisibleThanLinearMapping() {
        double quietVoice = RecordingWaveform.visualLevel(4096);
        double louderVoice = RecordingWaveform.visualLevel(16384);

        assertTrue(quietVoice > 0.45);
        assertTrue(louderVoice > quietVoice);
        assertTrue(louderVoice < 1.0);
    }

    @Test
    public void waveformStartsSmallAndUsesMostOfItsHeightForOrdinaryVoice() {
        assertEquals(0.06, RecordingWaveform.heightScale(0.0), 0.0001);
        assertTrue(RecordingWaveform.heightScale(
                RecordingWaveform.visualLevel(4096)) > 0.60);
        assertEquals(1.0, RecordingWaveform.heightScale(1.0), 0.0001);
    }
}
