package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression guard for covers disappearing permanently after a weak-network failure. */
public final class BookCoverLoadingContractTest {
    @Test public void shelfKeepsTypographyBelowAnOptionalImage() throws Exception {
        String panel = read("ui/BooksShelfPanel.java");

        assertTrue(panel.contains("addBookTypography(cover, book)"));
        assertFalse(panel.contains("cover.removeAllViews()"));
    }

    @Test public void shelfUsesPersistentVersionedLoaderAndCancelsIt() throws Exception {
        String panel = read("ui/BooksShelfPanel.java");
        String loader = read("data/BookCoverLoader.java");

        assertTrue(panel.contains("coverLoader.load(book, book.coverUrl(Api.publicWebBase()), image)"));
        assertTrue(panel.contains("coverLoader.cancelAll()"));
        assertTrue(loader.contains("getFilesDir()"));
        assertTrue(loader.contains("BookCoverPolicy.cacheKey(book.slug, book.coverAt)"));
        assertTrue(loader.contains("no-cache"));
    }

    @Test public void retriesAreScheduledWithoutStarvingTheIndexExecutor() throws Exception {
        String panel = read("ui/BooksShelfPanel.java");
        String loader = read("data/BookCoverLoader.java");

        assertTrue(panel.contains("new BookCoverLoader(context)"));
        assertTrue(loader.contains("ScheduledExecutorService"));
        assertTrue(loader.contains("schedule("));
        assertFalse(loader.contains("Thread.sleep"));
        assertTrue(panel.contains("coverLoader.shutdown()"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
