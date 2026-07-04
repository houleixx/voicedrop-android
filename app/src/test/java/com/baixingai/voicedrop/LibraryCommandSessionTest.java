package com.baixingai.voicedrop;

import com.baixingai.voicedrop.net.LibraryCommandSession;

import org.junit.Test;

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
}
