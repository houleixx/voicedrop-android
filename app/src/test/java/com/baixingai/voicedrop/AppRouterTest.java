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

        AppRouter.DeepLink article = AppRouter.parse("voicedrop://article/VoiceDrop-2026-07-04-093000");
        assertEquals(AppRouter.Kind.ARTICLE, article.kind);
        assertEquals("VoiceDrop-2026-07-04-093000", article.stem);
    }

    @Test
    public void rejectsNonVoicedropRoutes() {
        assertEquals(AppRouter.Kind.NONE, AppRouter.parse("https://jianshuo.dev/voicedrop/x").kind);
        assertEquals(AppRouter.Kind.NONE, AppRouter.parse("voicedrop://article").kind);
    }
}
