package com.baixingai.voicedrop.audio;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class PendingAudioFrames {
    private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();

    public void offer(byte[] data, boolean isLast) {
        frames.offer(new Frame(data, isLast));
    }

    public Frame take() throws InterruptedException {
        return frames.take();
    }

    public int queuedCount() {
        return frames.size();
    }

    public static final class Frame {
        public final byte[] data;
        public final boolean isLast;

        Frame(byte[] data, boolean isLast) {
            this.data = data;
            this.isLast = isLast;
        }
    }
}
