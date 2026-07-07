package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.LibraryStore;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class LibraryStoreRequestTest {
    @Test
    public void restyleBodyOmitsStyleVWhenUsingCurrentHeadStyle() throws Exception {
        JSONObject body = LibraryStore.restyleRequestBody("VoiceDrop-2026-07-01-120000-0m1s", null);

        assertEquals("VoiceDrop-2026-07-01-120000-0m1s", body.getString("stem"));
        assertFalse(body.has("styleV"));
    }

    @Test
    public void restyleBodyIncludesExplicitStyleVersionOnlyWhenProvided() throws Exception {
        JSONObject body = LibraryStore.restyleRequestBody("VoiceDrop-2026-07-01-120000-0m1s", 8);

        assertEquals(8, body.getInt("styleV"));
    }

    @Test
    public void xhsPackBodyUsesStemOnly() throws Exception {
        JSONObject body = LibraryStore.xhsPackRequestBody("VoiceDrop-2026-07-01-120000-0m1s");

        assertEquals("VoiceDrop-2026-07-01-120000-0m1s", body.getString("stem"));
        assertEquals(1, body.length());
    }

    @Test
    public void patchQuestionBodyMatchesFilesApiContract() throws Exception {
        JSONObject body = LibraryStore.patchQuestionRequestBody("q1", "answered");

        assertEquals("q1", body.getString("id"));
        assertEquals("answered", body.getString("status"));
    }

    @Test
    public void loadRefreshesArticleDocWhenTagsAreMissingEvenIfTitleIsCached() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");

        assertTrue(source.contains("r.hasArticles && (r.articleTitle == null || r.tags == null)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
