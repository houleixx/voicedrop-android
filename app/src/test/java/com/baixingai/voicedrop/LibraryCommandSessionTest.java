package com.baixingai.voicedrop;

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
