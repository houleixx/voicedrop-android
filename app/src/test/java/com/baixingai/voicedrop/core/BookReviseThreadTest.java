package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class BookReviseThreadTest {
    @Test public void parsesPermanentCreateAndReviseConversation() {
        BookReviseThread thread = BookReviseThread.parse("{\"slug\":\"my-book\",\"author\":\"作者\"," +
                "\"running\":false,\"thread\":[" +
                "{\"ts\":1000,\"kind\":\"create\",\"instruction\":\"开书\",\"status\":\"done\"}," +
                "{\"ts\":2000,\"kind\":\"revise\",\"instruction\":\"精简第三章\"," +
                "\"status\":\"done\",\"reply\":\"删掉了重复段落\",\"error\":null}]}");

        assertEquals("my-book", thread.slug);
        assertEquals("作者", thread.author);
        assertFalse(thread.running);
        assertEquals(2, thread.entries.size());
        assertTrue(thread.entries.get(0).creation());
        assertEquals("删掉了重复段落", thread.entries.get(1).reply);
        assertNull(thread.entries.get(1).error);
    }

    @Test public void runningEntryWinsWhenTopLevelFlagIsStale() {
        BookReviseThread thread = BookReviseThread.parse("{\"running\":false,\"thread\":[" +
                "{\"ts\":3,\"kind\":\"revise\",\"instruction\":\"换标题\",\"status\":\"running\"}]}");
        assertTrue(thread.running);
        assertTrue(thread.entries.get(0).running());
    }

    @Test public void malformedPayloadProducesSafeEmptyThread() {
        BookReviseThread thread = BookReviseThread.parse("not-json");
        assertTrue(thread.entries.isEmpty());
        assertFalse(thread.running);
    }
}
