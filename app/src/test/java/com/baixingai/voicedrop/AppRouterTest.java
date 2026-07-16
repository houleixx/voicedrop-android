package com.baixingai.voicedrop;

import org.junit.Test;

import static org.junit.Assert.*;

public class AppRouterTest {
    @Test
    public void parsesVoicedropRoutes() {
        assertEquals(AppRouter.Kind.RECORDINGS, AppRouter.parse("voicedrop://recordings").kind);
        assertEquals(AppRouter.Kind.COMMUNITY, AppRouter.parse("voicedrop://community").kind);
        assertEquals(AppRouter.Kind.SETTINGS, AppRouter.parse("voicedrop://settings").kind);
        assertEquals(AppRouter.Kind.RECORD, AppRouter.parse("voicedrop://record").kind);

        AppRouter.DeepLink taggedRecord = AppRouter.parse("voicedrop://record?tag=%E5%88%9B%E4%B8%9A");
        assertEquals(AppRouter.Kind.RECORD, taggedRecord.kind);
        assertEquals("创业", taggedRecord.tag);

        AppRouter.DeepLink article = AppRouter.parse("voicedrop://article/VoiceDrop-2026-07-04-093000");
        assertEquals(AppRouter.Kind.ARTICLE, article.kind);
        assertEquals("VoiceDrop-2026-07-04-093000", article.stem);
    }

    @Test
    public void rejectsNonVoicedropRoutes() {
        assertEquals(AppRouter.Kind.NONE, AppRouter.parse("voicedrop://article").kind);
    }

    @Test
    public void parsesUniversalShareLinks() {
        AppRouter.DeepLink voiceDrop = AppRouter.parse("https://voicedrop.cn/abc123?s=1");
        assertEquals(AppRouter.Kind.SHARE_LINK, voiceDrop.kind);
        assertEquals("abc123", voiceDrop.id);
        assertEquals("https://voicedrop.cn/abc123?s=1", voiceDrop.url);

        AppRouter.DeepLink www = AppRouter.parse("https://www.voicedrop.cn/AbC_123-xy");
        assertEquals(AppRouter.Kind.SHARE_LINK, www.kind);
        assertEquals("AbC_123-xy", www.id);

        AppRouter.DeepLink legacy = AppRouter.parse("https://jianshuo.dev/voicedrop/legacy9");
        assertEquals(AppRouter.Kind.SHARE_LINK, legacy.kind);
        assertEquals("legacy9", legacy.id);
    }

    @Test
    public void parsesInviteLandingLinks() {
        // 已装用户点邀请链接 → 进 App 记归因，不开下载页（与 iOS DeepLink.invite 同款）
        AppRouter.DeepLink invite = AppRouter.parse("https://voicedrop.cn/i/ABC123");
        assertEquals(AppRouter.Kind.INVITE, invite.kind);
        assertEquals("ABC123", invite.id);

        AppRouter.DeepLink direct = AppRouter.parse("https://jianshuo.dev/voicedrop/i/ABCDEF0123");
        assertEquals(AppRouter.Kind.INVITE, direct.kind);
        assertEquals("ABCDEF0123", direct.id);

        // /i/ 下不是合法码 → 网页兜底，不误吞
        assertEquals(AppRouter.Kind.WEB, AppRouter.parse("https://voicedrop.cn/i/ab").kind);
        assertEquals(AppRouter.Kind.WEB, AppRouter.parse("https://voicedrop.cn/i/ABC123/extra").kind);
    }

    @Test
    public void parsesUniversalRootAndWebFallbacks() {
        assertEquals(AppRouter.Kind.RECORDINGS, AppRouter.parse("https://voicedrop.cn/").kind);

        AppRouter.DeepLink help = AppRouter.parse("https://voicedrop.cn/help");
        assertEquals(AppRouter.Kind.WEB, help.kind);
        assertEquals("https://voicedrop.cn/help", help.url);

        assertEquals(AppRouter.Kind.NONE, AppRouter.parse("https://example.com/abc123").kind);
    }

    @Test
    public void parsesPromptImportMagicNumbersWithoutTakingEightDigitLinks() {
        AppRouter.DeepLink universal = AppRouter.parse("https://voicedrop.cn/1234567");
        assertEquals(AppRouter.Kind.PROMPT_IMPORT, universal.kind);
        assertEquals("1234567", universal.shareCode);

        AppRouter.DeepLink scheme = AppRouter.parse("voicedrop://prompt-import?code=7654321");
        assertEquals(AppRouter.Kind.PROMPT_IMPORT, scheme.kind);
        assertEquals("7654321", scheme.shareCode);

        assertEquals(AppRouter.Kind.SHARE_LINK, AppRouter.parse("https://voicedrop.cn/12345678").kind);
    }
}
