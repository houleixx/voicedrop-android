package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the low-memory pager policy used during locale-triggered Activity recreation. */
public final class HomePagerMemorySourceTest {
    @Test
    public void keepsOnlyAdjacentHomePagesAndReleasesDestroyedPageReferences() throws Exception {
        String source = new String(Files.readAllBytes(
                Path.of("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("homePager.setOffscreenPageLimit(1);"));
        assertFalse(source.contains("homePager.setOffscreenPageLimit(Math.max(3, homeTags.size() + 3))"));
        String destroy = source.substring(source.indexOf("public void destroyItem"),
                source.indexOf("public int getItemPosition", source.indexOf("public void destroyItem")));
        assertTrue(destroy.contains("recordingsListsByPage.remove(recordingPageKey)"));
        assertTrue(destroy.contains("emptyListTextByPage.remove(recordingPageKey)"));
        assertTrue(destroy.contains("communityFeedView = null"));
        assertTrue(destroy.contains("booksShelfPanel = null"));
    }

    @Test
    public void discardsLateCoverResultsAfterAnActivityIsDestroyed() throws Exception {
        String source = new String(Files.readAllBytes(
                Path.of("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("final int loadGeneration = recordingMetadataGeneration;"));
        assertTrue(source.contains("loadGeneration != recordingMetadataGeneration || isFinishing() || isDestroyed()"));
    }
}
