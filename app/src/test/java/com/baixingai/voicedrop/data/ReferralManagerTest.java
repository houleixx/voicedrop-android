package com.baixingai.voicedrop.data;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReferralManagerTest {
    @Test
    public void extractsShareTokenFromSupportedLinks() {
        assertEquals("abc123", ReferralManager.shareToken("https://voicedrop.cn/abc123"));
        assertEquals("AbC_123-xy", ReferralManager.shareToken("打开 https://www.voicedrop.cn/AbC_123-xy?s=1"));
        assertEquals("legacy9", ReferralManager.shareToken("https://jianshuo.dev/voicedrop/legacy9"));
        assertEquals("Invite77", ReferralManager.shareToken("https://voicedrop.cn/i/Invite77"));
        assertEquals("ABC123", ReferralManager.shareToken("https://jianshuo.dev/voicedrop/i/ABC123"));
    }

    @Test
    public void extractsTestLinksWithoutMatchingLookalikeDomains() {
        String base = "https://voicedrop-test.baixing.work/voicedrop";
        assertEquals("abc123", ReferralManager.shareToken("打开 " + base + "/abc123?s=1"));
        assertEquals("Invite77", ReferralManager.shareToken(base + "/i/Invite77"));
        assertNull(ReferralManager.shareToken("https://evil.voicedrop-test.baixing.work/voicedrop/abc123"));
        assertNull(ReferralManager.shareToken("https://voicedrop-test.baixing.work.evil.example/voicedrop/abc123"));
        assertNull(ReferralManager.shareToken(base + "/abc123/extra"));
        assertNull(ReferralManager.shareToken(base + "/privacy"));
    }

    @Test
    public void ignoresStaticAndInvalidPaths() {
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/help"));
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/privacy"));
        assertNull(ReferralManager.shareToken("https://example.com/abc123"));
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/abc"));
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/i/ABC123/extra"));
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/i/ABCDEF12345678901"));
    }
}
