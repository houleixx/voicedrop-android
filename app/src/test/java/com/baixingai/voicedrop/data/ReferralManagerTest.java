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
    }

    @Test
    public void ignoresStaticAndInvalidPaths() {
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/help"));
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/privacy"));
        assertNull(ReferralManager.shareToken("https://example.com/abc123"));
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/abc"));
    }
}
