package com.baixingai.voicedrop;

import com.baixingai.voicedrop.net.Api;

import org.junit.Test;

import static org.junit.Assert.*;

public class ApiTest {
    @Test
    public void debugBuildUsesOneDomainForAllTestServices() {
        assertTrue(Api.isTestEnvironment());
        assertEquals("https://voicedrop-test.baixing.work/files/api", Api.filesBase());
        assertEquals("https://voicedrop-test.baixing.work/files/api", Api.photoBase());
        assertEquals("https://voicedrop-test.baixing.work/agent", Api.agentBase());
        assertEquals("https://voicedrop-test.baixing.work/agent/wechat-pay/product", Api.wechatProductUrl());
        assertEquals("https://voicedrop-test.baixing.work/reco", Api.recoBase());
        assertEquals("wss://voicedrop-test.baixing.work/agent", Api.agentWs());
        assertEquals("https://voicedrop-test.baixing.work/voicedrop", Api.publicWebBase());
        assertTrue(Api.isKnownApiHost("voicedrop-test.baixing.work"));
        assertFalse(Api.isKnownApiHost("voicedrop-test.baixing.work.evil.example"));
        assertTrue(Api.isKnownApiHost("voicedrop-test.pages.dev"));
        assertTrue(Api.isKnownApiHost("voicedrop-agent-test.baixing.work"));
    }

    @Test
    public void sharingUsesTestDomainWhileWechatAuthorizationKeepsRegisteredHost() {
        assertFalse(Api.isKnownApiHost("example.test"));
        assertEquals("https://voicedrop-test.baixing.work/voicedrop/abc123", Api.sharePage("abc123"));
        assertEquals("https://voicedrop.cn/files/api", Api.wechatAuthorizationBase());
        assertTrue(Api.isWechatAuthorizationHost(Api.CN_HOST));
        assertFalse(Api.isWechatAuthorizationHost(Api.CF_HOST));
    }

    @Test
    public void generatedShareLinksKeepTestIdsAndEncodedParameters() {
        String base = "https://voicedrop-test.baixing.work/voicedrop";
        assertEquals(base + "/abc123?s=2&x=a%2Fb#title", Api.generatedShareUrl(
                "https://voicedrop.cn/abc123?s=2&x=a%2Fb#title"));
        assertEquals(base + "/i/Invite77", Api.generatedShareUrl("https://voicedrop.cn/i/Invite77"));
        assertEquals(base + "/1234567", Api.generatedShareUrl("https://jianshuo.dev/voicedrop/1234567"));
        assertEquals(base + "/abc123", Api.generatedShareUrl("https://voicedrop-test.pages.dev/voicedrop/abc123"));
        assertEquals(base + "/abc123", Api.generatedShareUrl(base + "/abc123"));
    }

    @Test
    public void shareRewritingDoesNotTouchAuthorizationOrUntrustedUrls() {
        for (String url : new String[]{"https://voicedrop.cn/files/api/wechat/scan?state=example",
                "https://voicedrop.cn.evil.example/abc123", "https://evil.example/abc123",
                "https://voicedrop.cn@evil.example/abc123", "https://user@voicedrop.cn/abc123",
                "https://voicedrop.cn:8443/abc123", "http://voicedrop.cn/abc123", "invalid URL", ""}) {
            assertEquals(url, Api.generatedShareUrl(url));
        }
        assertNull(Api.generatedShareUrl(null));
    }

    @Test
    public void pathEncodingKeepsSlashSeparatedKeysUsableInApiPaths() {
        assertEquals("articles/VoiceDrop-2026.json", Api.path("articles/VoiceDrop-2026.json"));
        assertEquals("photos/a%20b/1-x.jpg", Api.path("photos/a b/1-x.jpg"));
    }
}
