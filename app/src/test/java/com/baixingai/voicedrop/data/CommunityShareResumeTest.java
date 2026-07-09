package com.baixingai.voicedrop.data;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CommunityShareResumeTest {
    @Test
    public void callbackRoutingLeavesPendingUntilMatchingDetailConsumesIt() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        ManualClock clock = new ManualClock();
        PendingCommunityShareStore store = new PendingCommunityShareStore(storage, clock);
        store.save("VoiceDrop-2026-07-03-120000.m4a", "share-parent");

        PendingCommunityShareStore.Pending pending = store.peek();
        assertNotNull(pending);
        assertEquals("VoiceDrop-2026-07-03-120000.m4a", detailAudioNameAfterLogin(pending));
        assertNotNull(store.peek());

        PendingCommunityShareStore.Pending consumed = consumeForDetailIfRequested(
                store, "VoiceDrop-2026-07-03-120000.m4a", true);
        assertNotNull(consumed);
        assertEquals("share-parent", consumed.replyToShareId);
        assertNull(store.peek());
    }

    @Test
    public void detailConsumesPendingExactlyOnceAndOnlyForExplicitMatchingResume() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        ManualClock clock = new ManualClock();
        PendingCommunityShareStore store = new PendingCommunityShareStore(storage, clock);
        store.save("VoiceDrop-2026-07-03-120000.m4a", "share-parent");

        assertNull(consumeForDetailIfRequested(store, "VoiceDrop-2026-07-03-120000.m4a", false));
        assertNotNull(store.peek());

        assertNull(consumeForDetailIfRequested(store, "VoiceDrop-2026-07-03-120001.m4a", true));
        assertNotNull(store.peek());

        assertNotNull(consumeForDetailIfRequested(store, "VoiceDrop-2026-07-03-120000.m4a", true));
        assertNull(consumeForDetailIfRequested(store, "VoiceDrop-2026-07-03-120000.m4a", true));
        assertNull(store.peek());
    }

    private static String detailAudioNameAfterLogin(PendingCommunityShareStore.Pending pending) throws Exception {
        Class<?> cls = Class.forName("com.baixingai.voicedrop.data.CommunityShareResume");
        Method method = cls.getMethod("detailAudioNameAfterLogin", PendingCommunityShareStore.Pending.class);
        return (String) method.invoke(null, pending);
    }

    private static PendingCommunityShareStore.Pending consumeForDetailIfRequested(
            PendingCommunityShareStore store, String audioName, boolean resumeRequested) throws Exception {
        Class<?> cls = Class.forName("com.baixingai.voicedrop.data.CommunityShareResume");
        Method method = cls.getMethod("consumeForDetailIfRequested",
                PendingCommunityShareStore.class, String.class, boolean.class);
        return (PendingCommunityShareStore.Pending) method.invoke(null, store, audioName, resumeRequested);
    }

    private static final class ManualClock implements PendingCommunityShareStore.Clock {
        @Override public long nowMs() {
            return 1_752_000_000_000L;
        }
    }

    private static final class MemoryStorage implements PendingCommunityShareStore.Storage {
        final Map<String, String> values = new HashMap<>();

        @Override public String get(String key) {
            return values.get(key);
        }

        @Override public void put(String key, String value) {
            values.put(key, value);
        }

        @Override public void remove(String key) {
            values.remove(key);
        }
    }
}
