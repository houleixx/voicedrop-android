package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Prevents the shelf from decoding an entire library's original-resolution covers at once. */
public final class BookShelfMemorySourceTest {
    @Test public void shelfPagesBookCellsBeforeStartingCoverLoads() throws Exception {
        String source = read("src/main/java/com/baixingai/voicedrop/ui/BooksShelfPanel.java");
        assertTrue(source.contains("private static final int BOOKS_PER_PAGE = 12;"));
        assertTrue(source.contains("books.subList(0, Math.min(visibleBookCount, books.size()))"));
        assertTrue(source.contains("visibleBookCount + BOOKS_PER_PAGE"));
    }

    @Test public void coverLoaderSamplesImagesToTheirDisplayScale() throws Exception {
        String source = read("src/main/java/com/baixingai/voicedrop/data/BookCoverLoader.java");
        assertTrue(source.contains("private static final int MAX_DECODED_EDGE = 720;"));
        assertTrue(source.contains("decodeSampled(data)"));
        assertTrue(source.contains("options.inSampleSize = sample;"));
        assertTrue(source.contains("options.inPreferredConfig = Bitmap.Config.RGB_565;"));
    }

    private static String read(String path) throws Exception {
        Path file = Path.of(path);
        if (!Files.exists(file)) file = Path.of("app", path);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
