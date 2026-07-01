package com.baixingai.voicedrop;

import com.baixingai.voicedrop.net.AgentMessage;

import org.junit.Test;

import static org.junit.Assert.*;

public class AgentMessageTest {
    @Test
    public void parsesStatusUpdateMessages() throws Exception {
        AgentMessage.Status status = AgentMessage.status("{\"type\":\"status_update\",\"stem\":\"VoiceDrop-1\",\"status\":\"mining\"}");

        assertEquals("VoiceDrop-1", status.stem);
        assertEquals("mining", status.status);
    }

    @Test
    public void ignoresNonStatusMessages() throws Exception {
        assertNull(AgentMessage.status("{\"type\":\"hello\"}"));
    }

    @Test
    public void detectsArticleUpdatedMessage() throws Exception {
        AgentMessage.Update update = AgentMessage.update("{\"type\":\"updated\",\"doc\":{\"articles\":[{\"title\":\"A\",\"body\":\"B\"}]}}");

        assertNotNull(update);
        assertTrue(update.docJson.contains("\"articles\""));
    }

    @Test
    public void parsesDeviceLinkRequestMessages() throws Exception {
        AgentMessage.LinkRequest request = AgentMessage.linkRequest("{\"type\":\"link_request\",\"pairingId\":\"p1\",\"code\":\"1234\",\"pubkey\":\"pub\"}");

        assertEquals("p1", request.pairingId);
        assertEquals("1234", request.code);
        assertEquals("pub", request.pubkey);
    }

    @Test
    public void parsesDeviceLinkReleaseMessages() throws Exception {
        AgentMessage.LinkRelease release = AgentMessage.linkRelease("{\"type\":\"link_release\",\"pairingId\":\"p2\"}");

        assertEquals("p2", release.pairingId);
    }
}
