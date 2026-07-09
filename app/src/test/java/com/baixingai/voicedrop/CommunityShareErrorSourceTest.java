package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommunityShareErrorSourceTest {
    @Test
    public void detailPageClearsInvalidSessionAndDoesNotMentionApple() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("result.hasInvalidSession()"));
        assertTrue(source.contains("auth.signOutWechat()"));
        assertTrue(source.contains("result.failureMessage()"));
        assertFalse(source.contains("社区分享失败，可能需要 Apple 会话"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
