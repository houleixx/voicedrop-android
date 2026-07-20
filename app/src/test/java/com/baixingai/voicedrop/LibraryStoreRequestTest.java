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
    public void linkTargetParsesArticleAndCommunityResponses() throws Exception {
        LibraryStore.LinkTarget article = LibraryStore.LinkTarget.fromJson("{"
                + "\"type\":\"article\","
                + "\"owner\":\"users/me/\","
                + "\"stem\":\"VoiceDrop-2026-07-10-090000\","
                + "\"title\":\"标题\","
                + "\"articles\":[{\"title\":\"标题\",\"body\":\"正文\"}]"
                + "}");
        assertEquals("article", article.type);
        assertEquals("users/me/", article.owner);
        assertEquals("VoiceDrop-2026-07-10-090000", article.stem);
        assertEquals(1, article.doc.articles.size());

        LibraryStore.LinkTarget community = LibraryStore.LinkTarget.fromJson("{"
                + "\"type\":\"community\","
                + "\"owner\":\"users/other/\","
                + "\"stem\":\"VoiceDrop-2026-07-10-090000\""
                + "}");
        assertEquals("community", community.type);
    }

    @Test
    public void resolveShareLinkUsesFilesApiLinkEndpoint() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");

        assertTrue(source.contains("/link/\" + Api.path(id)"));
    }

    @Test
    public void recordingRowsPublishBeforeBoundedMetadataEnrichment() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");

        assertTrue(source.contains("META_IO = Executors.newFixedThreadPool(5)"));
        assertTrue(source.contains("public boolean enrichMissingMetadata"));
        assertTrue(source.contains("auth.storeLibraryMetadataCache"));
        int fastLoad = source.indexOf("public List<Recording> load(List<String> localUploading, Map");
        int enrichment = source.indexOf("public boolean enrichMissingMetadata");
        assertFalse(source.substring(fastLoad, enrichment).contains("fetchDoc(r)"));
    }

    @Test
    public void homeRefreshUpdatesExistingListsWithoutRecreatingEveryPagerPage() throws Exception {
        String activity = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String feed = readSource("src/main/java/com/baixingai/voicedrop/ui/CommunityFeedView.java");

        assertTrue(activity.contains("communityFeedView.updateFeed(communityFeed)"));
        assertTrue(activity.contains("scheduleRecordingMetadataEnrichment(recordings)"));
        assertTrue(feed.contains("public void updateFeed(CommunityStore.Feed next)"));
    }

    @Test
    public void recordingListPrefersTheLightweightIndexAndFallsBackToTheLegacyList() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");

        int indexed = source.indexOf("Api.filesBase() + \"/recordings\"");
        int legacy = source.indexOf("Api.filesBase() + \"/list\"");
        assertTrue(indexed >= 0);
        assertTrue(legacy > indexed);
        assertTrue(source.contains("row.optBoolean(\"hasArticles\")"));
        assertTrue(source.contains("row.optBoolean(\"isEmpty\")"));
        assertTrue(source.contains("row.optBoolean(\"hasTags\")"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
