package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.ReferralManager;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReferralManagerTest {
    @Test
    public void parsesInviteLinksFromClipboardText() {
        // 邀请落地页链接（「邀请好友」分享出去的形态）
        assertEquals("ABC123", ReferralManager.shareToken("来 VoiceDrop https://voicedrop.cn/i/ABC123 一起写"));
        assertEquals("ABCDEF", ReferralManager.shareToken("https://www.voicedrop.cn/i/ABCDEF"));
        assertEquals("ABCDEF0123", ReferralManager.shareToken("https://jianshuo.dev/voicedrop/i/ABCDEF0123"));
        assertEquals("ABC123", ReferralManager.shareToken("voicedrop.cn/i/ABC123"));
    }

    @Test
    public void parsesArticleShareLinks() {
        assertEquals("AbC_123-xy", ReferralManager.shareToken("https://voicedrop.cn/AbC_123-xy?s=1"));
        assertEquals("legacy99", ReferralManager.shareToken("https://jianshuo.dev/voicedrop/legacy99"));
    }

    @Test
    public void ignoresStaticPathsAndGarbage() {
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/privacy"));
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/welcome"));
        assertNull(ReferralManager.shareToken("随便一段没有链接的文字"));
        assertNull(ReferralManager.shareToken(""));
        assertNull(ReferralManager.shareToken(null));
        // /i/ 后面不是合法码（太短）→ 不能误吞
        assertNull(ReferralManager.shareToken("https://voicedrop.cn/i/ab"));
    }

    @Test
    public void invitePatternWinsOverSharePattern() {
        // 同一段文字里同时有文章分享链接和邀请链接（邀请在后）→ 邀请码优先（更窄的先判）
        assertEquals("ABC123", ReferralManager.shareToken(
                "看文章 https://voicedrop.cn/AbCdEf99 邀请 https://voicedrop.cn/i/ABC123"));
    }
}
