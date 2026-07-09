package com.baixingai.voicedrop.data;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PendingCommunityShareStoreTest {
    @Test
    public void saveAndPeekReturnsPendingShare() {
        MemoryStorage storage = new MemoryStorage();
        ManualClock clock = new ManualClock(1_000L);
        PendingCommunityShareStore store = new PendingCommunityShareStore(storage, clock);

        store.save("VoiceDrop-2026-07-03-120000.m4a", "share-parent");

        PendingCommunityShareStore.Pending pending = store.peek();
        assertEquals("VoiceDrop-2026-07-03-120000.m4a", pending.audioName);
        assertEquals("share-parent", pending.replyToShareId);
    }

    @Test
    public void consumeMatchesOnceAndClearsThePendingShare() {
        MemoryStorage storage = new MemoryStorage();
        ManualClock clock = new ManualClock(1_000L);
        PendingCommunityShareStore store = new PendingCommunityShareStore(storage, clock);
        store.save("VoiceDrop-2026-07-03-120000.m4a", "share-parent");

        PendingCommunityShareStore.Pending consumed = store.consume("VoiceDrop-2026-07-03-120000.m4a");

        assertEquals("VoiceDrop-2026-07-03-120000.m4a", consumed.audioName);
        assertEquals("share-parent", consumed.replyToShareId);
        assertNull(store.peek());
    }

    @Test
    public void consumeMismatchPreservesThePendingShare() {
        MemoryStorage storage = new MemoryStorage();
        ManualClock clock = new ManualClock(1_000L);
        PendingCommunityShareStore store = new PendingCommunityShareStore(storage, clock);
        store.save("VoiceDrop-2026-07-03-120000.m4a", "share-parent");

        PendingCommunityShareStore.Pending consumed = store.consume("VoiceDrop-2026-07-03-120001.m4a");

        assertNull(consumed);
        assertEquals("VoiceDrop-2026-07-03-120000.m4a",
                store.peek().audioName);
    }

    @Test
    public void clearRemovesThePendingShare() {
        MemoryStorage storage = new MemoryStorage();
        ManualClock clock = new ManualClock(1_000L);
        PendingCommunityShareStore store = new PendingCommunityShareStore(storage, clock);
        store.save("VoiceDrop-2026-07-03-120000.m4a", null);

        store.clear();

        assertNull(store.peek());
    }

    @Test
    public void peekDropsExpiredPendingShare() {
        MemoryStorage storage = new MemoryStorage();
        ManualClock clock = new ManualClock(1_000L);
        PendingCommunityShareStore store = new PendingCommunityShareStore(storage, clock);
        store.save("VoiceDrop-2026-07-03-120000.m4a", "share-parent");
        clock.nowMs = 1_000L + (15L * 60L * 1000L);

        assertNull(store.peek());
        assertNull(storage.values.get("vd.pendingCommunityShare"));
    }

    @Test
    public void peekDropsMalformedPendingShare() {
        MemoryStorage storage = new MemoryStorage();
        ManualClock clock = new ManualClock(1_000L);
        storage.values.put("vd.pendingCommunityShare", "not-json");
        PendingCommunityShareStore store = new PendingCommunityShareStore(storage, clock);

        assertNull(store.peek());
        assertNull(storage.values.get("vd.pendingCommunityShare"));
    }

    private static final class ManualClock implements PendingCommunityShareStore.Clock {
        long nowMs;

        ManualClock(long nowMs) {
            this.nowMs = nowMs;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }
    }

    private static final class MemoryStorage implements PendingCommunityShareStore.Storage {
        final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    }
}
