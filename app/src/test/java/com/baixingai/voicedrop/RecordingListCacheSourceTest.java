package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class RecordingListCacheSourceTest {
    @Test
    public void recordingsUseAccountScopedSWRCacheAndInvalidateItOnDeletion() throws Exception {
        String auth = source("src/main/java/com/baixingai/voicedrop/data/AuthStore.java");
        String library = source("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");
        String activity = source("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(auth.contains("LIBRARY_LIST_PREFIX"));
        assertTrue(auth.contains("clearAllLibraryListCaches()"));
        assertTrue(library.contains("auth.storeLibraryListCache(response.text())"));
        assertTrue(library.contains("public List<Recording> cachedRecordings"));
        assertTrue(library.contains("auth.clearCurrentLibraryListCache()"));
        assertTrue(activity.contains("recordings = library.cachedRecordings("));
    }

    @Test
    public void recordingMetadataUsesStableAccountIdentityInsteadOfRotatingBearer() throws Exception {
        String auth = source("src/main/java/com/baixingai/voicedrop/data/AuthStore.java");
        String library = source("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");
        String activity = source("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(auth.contains("public String libraryCacheIdentity()"));
        assertTrue(auth.contains("digest.digest(libraryCacheIdentity().getBytes"));
        assertTrue(auth.contains("cacheComponent(libraryCacheIdentity())"));
        assertTrue(library.contains("private String metadataIdentity"));
        assertTrue(library.contains("String identity = auth.libraryCacheIdentity()"));
        assertTrue(activity.contains("protected String connectedAccountIdentity"));
        assertTrue(activity.contains("connectedAccountIdentity = auth.libraryCacheIdentity()"));
    }

    @Test
    public void cachedRecordingMetadataIncludesCoverKeysForColdStartRendering() throws Exception {
        String recording = source("src/main/java/com/baixingai/voicedrop/data/Recording.java");
        String library = source("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");
        String activity = source("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(recording.contains("public String coverPhotoKey"));
        assertTrue(library.contains("private final Map<String, String> coverCache"));
        assertTrue(library.contains("root.optJSONObject(\"covers\")"));
        assertTrue(library.contains(".put(\"covers\", covers)"));
        assertTrue(library.contains("r.coverPhotoKey = coverCache.get"));
        assertTrue(activity.contains("String cachedFallbackKey = rec.coverPhotoKey"));
        assertTrue(activity.contains("String dedicatedKey = rec.coverJpgKey()"));
        assertTrue(activity.contains("coverIo.execute"));
    }

    private static String source(String relative) throws Exception {
        return new String(Files.readAllBytes(Path.of(relative)), StandardCharsets.UTF_8);
    }
}
