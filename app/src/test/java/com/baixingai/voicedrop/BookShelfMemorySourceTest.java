package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Prevents the shelf from creating or decoding an entire library's covers at once. */
public final class BookShelfMemorySourceTest {
    @Test public void shelfUsesRecyclerViewToCreateBookRowsOnlyAsTheyScrollIntoView() throws Exception {
        String source = read("src/main/java/com/baixingai/voicedrop/ui/BooksShelfPanel.java");
        assertTrue(source.contains("import androidx.recyclerview.widget.RecyclerView;"));
        assertTrue(source.contains("new LinearLayoutManager(context)"));
        assertTrue(source.contains("private final ShelfAdapter shelfAdapter;"));
        assertTrue(source.contains("private final class ShelfAdapter extends RecyclerView.Adapter"));
        assertTrue(source.contains("return (cellCount() + 1) / 2;"));
        assertTrue(source.contains("onBindViewHolder"));
        assertTrue(!source.contains("BOOKS_PER_PAGE"));
        assertTrue(!source.contains("addMoreBooksAction"));
        assertTrue(!source.contains("显示更多图书"));
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
