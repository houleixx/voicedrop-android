package com.baixingai.voicedrop;

import com.baixingai.voicedrop.net.Api;

import org.junit.Test;

import static org.junit.Assert.*;

public class ApiTest {
    @Test
    public void apiBasesMatchIosNetworkingContract() {
        assertEquals("https://jianshuo.dev/files/api", Api.filesBase());
        assertEquals("https://jianshuo.dev/agent", Api.agentBase());
        assertEquals("https://jianshuo.dev/reco", Api.recoBase());
        assertEquals("wss://jianshuo.dev/agent", Api.agentWs());
        assertEquals("https://voicedrop.cn/abc123", Api.sharePage("abc123"));
    }

    @Test
    public void pathEncodingKeepsSlashSeparatedKeysUsableInApiPaths() {
        assertEquals("articles/VoiceDrop-2026.json", Api.path("articles/VoiceDrop-2026.json"));
        assertEquals("photos/a%20b/1-x.jpg", Api.path("photos/a b/1-x.jpg"));
    }
}
