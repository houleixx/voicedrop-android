package com.baixingai.voicedrop;

import com.baixingai.voicedrop.ui.AudioPlaybackState;

import org.junit.Assert;
import org.junit.Test;

public class AudioPlaybackStateTest {
    @Test public void idleClickStartsLoadingAndBlocksSecondStart() {
        AudioPlaybackState state = new AudioPlaybackState();

        Assert.assertTrue(state.requestPlay());
        Assert.assertEquals(AudioPlaybackState.Mode.LOADING, state.mode());
        Assert.assertFalse(state.requestPlay());
    }

    @Test public void playingClickRequestsStop() {
        AudioPlaybackState state = new AudioPlaybackState();
        state.requestPlay();
        state.started();

        Assert.assertTrue(state.requestStop());
        Assert.assertEquals(AudioPlaybackState.Mode.IDLE, state.mode());
        Assert.assertEquals(0f, state.progress(), 0.001f);
    }

    @Test public void playbackProgressIsClampedAndCompletionResets() {
        AudioPlaybackState state = new AudioPlaybackState();
        state.requestPlay();
        state.started();

        state.progress(500, 1000);
        Assert.assertEquals(0.5f, state.progress(), 0.001f);

        state.progress(1500, 1000);
        Assert.assertEquals(1f, state.progress(), 0.001f);

        state.completed();
        Assert.assertEquals(AudioPlaybackState.Mode.IDLE, state.mode());
        Assert.assertEquals(0f, state.progress(), 0.001f);
    }
}
