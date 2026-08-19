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
    @Test public void bothShelvesKeepTypographyBelowAnOptionalImage() throws Exception {
        String panel = read("ui/BooksShelfPanel.java");
        String activity = read("BooksShelfActivity.java");

        assertTrue(panel.contains("addBookTypography(cover, book)"));
        assertTrue(activity.contains("addBookTypography(cover, book)"));
        assertFalse(activity.contains("cover.removeAllViews()"));
    }

    @Test public void bothShelvesUsePersistentVersionedLoaderAndCancelIt() throws Exception {
        String panel = read("ui/BooksShelfPanel.java");
        String activity = read("BooksShelfActivity.java");
        String loader = read("data/BookCoverLoader.java");

        assertTrue(panel.contains("coverLoader.load(book, image)"));
        assertTrue(activity.contains("coverLoader.load(book, image)"));
        assertTrue(panel.contains("coverLoader.cancelAll()"));
        assertTrue(activity.contains("coverLoader.cancelAll()"));
        assertTrue(loader.contains("getFilesDir()"));
        assertTrue(loader.contains("BookCoverPolicy.cacheKey(book.slug, book.coverAt)"));
        assertTrue(loader.contains("no-cache"));
    }

    @Test public void retriesAreScheduledWithoutStarvingTheIndexExecutor() throws Exception {
        String panel = read("ui/BooksShelfPanel.java");
        String activity = read("BooksShelfActivity.java");
        String loader = read("data/BookCoverLoader.java");

        assertTrue(panel.contains("new BookCoverLoader(context)"));
        assertTrue(activity.contains("new BookCoverLoader(this)"));
        assertTrue(loader.contains("ScheduledExecutorService"));
        assertTrue(loader.contains("schedule("));
        assertFalse(loader.contains("Thread.sleep"));
        assertTrue(panel.contains("coverLoader.shutdown()"));
        assertTrue(activity.contains("coverLoader.shutdown()"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
