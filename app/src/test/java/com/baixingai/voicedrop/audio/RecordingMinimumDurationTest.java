package com.baixingai.voicedrop.audio;

import org.junit.Test;

import java.io.File;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecordingMinimumDurationTest {
    @Test
    public void discardsTooShortTakeAndDeletesItsFile() throws Exception {
        File file = File.createTempFile("voicedrop-short-", ".m4a");
        AudioRecorder.Take take = new AudioRecorder.Take(file, ZonedDateTime.now(), 3.5, 800);

        assertTrue(RecordingQuality.discardIfTooShort(take));
        assertFalse(file.exists());
    }

    @Test
    public void keepsTakeAtMinimumDuration() throws Exception {
        File file = File.createTempFile("voicedrop-kept-", ".m4a");
        try {
            AudioRecorder.Take take = new AudioRecorder.Take(file, ZonedDateTime.now(), 4.0, 800);

            assertFalse(RecordingQuality.discardIfTooShort(take));
            assertTrue(file.exists());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
