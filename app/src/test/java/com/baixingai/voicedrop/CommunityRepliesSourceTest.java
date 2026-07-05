package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class CommunityRepliesSourceTest {
    @Test
    public void communityRepliesRenderContinuationPreviews() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/CommunityDetailActivity.java");

        assertTrue(source.contains("replyPreviewText"));
        assertTrue(source.contains("续文"));
        assertTrue(source.contains("继续阅读"));
        assertTrue(source.contains("reply.doc == null || reply.doc.articles.isEmpty()"));
        assertTrue(source.contains("preview.replaceAll(\"\\\\[\\\\[photo:[^\\\\]]+\\\\]\\\\]\", \" \")"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
