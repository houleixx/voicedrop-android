package com.baixingai.voicedrop.net;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientReliabilityTest {
    @Test public void authoritativeCommandSnapshotsRefreshAndInvalidateAllWithoutStems() {
        assertTrue(ClientReliability.commandMessageRequiresRefresh("updated"));
        assertTrue(ClientReliability.commandMessageRequiresRefresh("snapshot"));
        assertFalse(ClientReliability.commandMessageRequiresRefresh("reply"));

        assertTrue(ClientReliability.shouldInvalidateAllArticleCaches(Collections.emptyList()));
        assertFalse(ClientReliability.shouldInvalidateAllArticleCaches(
                Collections.singletonList("VoiceDrop-2026-07-27-120000")));
    }

    @Test public void longLivedSocketsUseIosCompatibleKeepaliveAndRejectStaleGenerations() {
        assertEquals(25_000, ClientReliability.newLongLivedWebSocketClient().pingIntervalMillis());

        assertTrue(ClientReliability.isCurrentGeneration(4, 4));
        assertFalse(ClientReliability.isCurrentGeneration(5, 4));

        assertTrue(ClientReliability.accountIdentityChanged("anon_old", "anon_new"));
        assertFalse(ClientReliability.accountIdentityChanged("anon_same", "anon_same"));
    }
}
