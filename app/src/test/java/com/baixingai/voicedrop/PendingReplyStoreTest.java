package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.PendingReplyStore;
import com.baixingai.voicedrop.data.Recording;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PendingReplyStoreTest {
    @Test
    public void storesRepliesUsingIosCompatibleAudioNameKey() {
        MemoryStorage storage = new MemoryStorage();
        PendingReplyStore store = new PendingReplyStore(storage);

        store.put("VoiceDrop-2026-07-03-120000.m4a", "share-parent");

        assertEquals("share-parent", storage.values.get("vd.pendingReply.VoiceDrop-2026-07-03-120000.m4a"));
        assertEquals("share-parent", store.replyTo("VoiceDrop-2026-07-03-120000.m4a"));
    }

    @Test
    public void publishesOnlyReadyArticleRepliesAndClearsSuccessfulOnes() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        PendingReplyStore store = new PendingReplyStore(storage);
        store.put("pending.m4a", "parent-a");
        store.put("ready.m4a", "parent-b");
        store.put("failed.m4a", "parent-c");

        List<String> calls = new ArrayList<>();
        store.publishReadyReplies(Arrays.asList(
                new Recording("pending.m4a", "", false, false),
                new Recording("ready.m4a", "2026", true, false),
                new Recording("failed.m4a", "2026", true, false)
        ), (recording, replyTo) -> {
            calls.add(recording.audioName + "->" + replyTo);
            return "ready.m4a".equals(recording.audioName);
        });

        assertEquals(Arrays.asList("ready.m4a->parent-b", "failed.m4a->parent-c"), calls);
        assertEquals("parent-a", store.replyTo("pending.m4a"));
        assertNull(store.replyTo("ready.m4a"));
        assertEquals("parent-c", store.replyTo("failed.m4a"));
    }

    private static final class MemoryStorage implements PendingReplyStore.Storage {
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
