package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.CommandQueueStore;
import com.baixingai.voicedrop.net.LibraryCommandSession;

import org.junit.Test;
import org.json.JSONObject;

import java.util.Arrays;

import static org.junit.Assert.*;

public class LibraryCommandSessionTest {
    @Test
    public void buildsCommandPayloadWithNumberedRefs() throws Exception {
        String payload = LibraryCommandSession.payloadFor("cmd-1", "删掉第二条", Arrays.asList(
                new LibraryCommandSession.CommandRef(1, "VoiceDrop-a", "第一篇"),
                new LibraryCommandSession.CommandRef(2, "VoiceDrop-b", "第二篇")));

        assertTrue(payload.contains("\"type\":\"instruct\""));
        assertTrue(payload.contains("\"id\":\"cmd-1\""));
        assertTrue(payload.contains("\"text\":\"删掉第二条\""));
        assertTrue(payload.contains("\"n\":2"));
        assertTrue(payload.contains("\"stem\":\"VoiceDrop-b\""));
        assertTrue(payload.contains("\"title\":\"第二篇\""));
    }

    @Test
    public void persistsNumberedRefsWithEachQueuedCommandAcrossColdStart() throws Exception {
        LibraryCommandSession.CommandRequest original = new LibraryCommandSession.CommandRequest(
                "cmd-1", "把第二篇和第三篇合并", Arrays.asList(
                new LibraryCommandSession.CommandRef(2, "VoiceDrop-b", "第二篇"),
                new LibraryCommandSession.CommandRef(3, "VoiceDrop-c", "第三篇")));

        String stored = CommandQueueStore.serialize(Arrays.asList(original));
        LibraryCommandSession.CommandRequest restored = CommandQueueStore.parse(stored).get(0);

        JSONObject payload = new JSONObject(LibraryCommandSession.payloadFor(
                restored.id, restored.text, restored.refs));
        assertEquals(2, payload.getJSONArray("refs").length());
        assertEquals("VoiceDrop-b", payload.getJSONArray("refs").getJSONObject(0).getString("stem"));
        assertEquals("VoiceDrop-c", payload.getJSONArray("refs").getJSONObject(1).getString("stem"));
    }

    @Test
    public void buildsConfirmAndCancelPayloads() {
        assertEquals("{\"type\":\"confirm\",\"id\":\"abc\"}", LibraryCommandSession.confirmPayload("abc"));
        assertEquals("{\"type\":\"cancel\",\"id\":\"abc\"}", LibraryCommandSession.cancelPayload("abc"));
    }

    @Test
    public void readsTheBackendConfirmationSummary() throws Exception {
        JSONObject message = new JSONObject()
                .put("type", "confirm")
                .put("summary", "要删掉《文章2》吗？");

        assertEquals("要删掉《文章2》吗？", LibraryCommandSession.confirmationText(message));
    }
}
