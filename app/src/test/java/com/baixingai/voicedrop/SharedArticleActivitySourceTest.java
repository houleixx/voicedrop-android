package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class SharedArticleActivitySourceTest {
    @Test
    public void sharedArticleActivityRendersReadOnlyArticleFromLinkPayload() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SharedArticleActivity.java");
        String manifest = readSource("src/main/AndroidManifest.xml");
        String recordings = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(source.contains("EXTRA_SHARED_JSON"));
        assertTrue(source.contains("ArticleDoc.fromJson"));
        assertTrue(source.contains("ArticleBody.segments"));
        assertTrue(source.contains("library.photoImage"));
        assertFalse(source.contains("feed("));
        assertFalse(source.contains("startHoldArticleEdit"));
        assertTrue(manifest.contains(".SharedArticleActivity"));
        assertTrue(recordings.contains("openSharedArticle(target, fallbackUrl)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
