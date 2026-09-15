package com.baixingai.voicedrop;

import com.baixingai.voicedrop.net.Api;

import org.junit.Test;

import static org.junit.Assert.*;

public class ApiTest {
    @Test
    public void debugBuildUsesThreeFixedTestServicesInsteadOfRacingThem() {
        assertTrue(Api.isTestEnvironment());
        assertEquals("https://voicedrop-test.pages.dev/files/api", Api.filesBase());
        assertEquals("https://voicedrop-test.pages.dev/files/api", Api.photoBase());
        assertEquals("https://voicedrop-agent-test.houleixx.workers.dev/agent", Api.agentBase());
        assertEquals("https://voicedrop-agent-test.houleixx.workers.dev/agent/wechat-pay/product", Api.wechatProductUrl());
        assertEquals("https://voicedrop-reco-test.houleixx.workers.dev/reco", Api.recoBase());
        assertEquals("wss://voicedrop-agent-test.houleixx.workers.dev/agent", Api.agentWs());
        assertEquals("https://voicedrop-test.pages.dev/voicedrop", Api.publicWebBase());
        assertTrue(Api.isKnownApiHost("voicedrop-test.pages.dev"));
        assertTrue(Api.isKnownApiHost("voicedrop-agent-test.houleixx.workers.dev"));
    }

    @Test
    public void productionOnlyShareHostDoesNotChangeInDebugBuild() {
        assertFalse(Api.isKnownApiHost("example.test"));
        assertEquals("https://voicedrop.cn/abc123", Api.sharePage("abc123"));
        assertEquals("https://voicedrop.cn/files/api", Api.wechatAuthorizationBase());
        assertTrue(Api.isWechatAuthorizationHost(Api.CN_HOST));
        assertFalse(Api.isWechatAuthorizationHost(Api.CF_HOST));
    }

    @Test
    public void pathEncodingKeepsSlashSeparatedKeysUsableInApiPaths() {
        assertEquals("articles/VoiceDrop-2026.json", Api.path("articles/VoiceDrop-2026.json"));
        assertEquals("photos/a%20b/1-x.jpg", Api.path("photos/a b/1-x.jpg"));
    }
}
