package com.baixingai.voicedrop;

import com.baixingai.voicedrop.audio.PendingAudioFrames;

import org.junit.Test;

import static org.junit.Assert.*;

public class PendingAudioFramesTest {
    @Test public void buffersAudioFramesUntilSenderCanDrainThem() throws Exception {
        PendingAudioFrames frames = new PendingAudioFrames();

        frames.offer(new byte[]{1, 2}, false);
        frames.offer(new byte[]{3}, true);

        PendingAudioFrames.Frame first = frames.take();
        PendingAudioFrames.Frame second = frames.take();

        assertArrayEquals(new byte[]{1, 2}, first.data);
        assertFalse(first.isLast);
        assertArrayEquals(new byte[]{3}, second.data);
        assertTrue(second.isLast);
    }

    @Test public void exposesQueuedFrameCountForBacklogPacing() throws Exception {
        PendingAudioFrames frames = new PendingAudioFrames();

        frames.offer(new byte[]{1}, false);
        frames.offer(new byte[]{2}, false);
        assertEquals(2, frames.queuedCount());

        frames.take();
        assertEquals(1, frames.queuedCount());
    }
}
