package com.baixingai.voicedrop;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

/** Prevents the books tab from becoming a separate Activity transition that visibly jumps. */
public final class BooksHomeTabContractTest {
    @Test public void booksUsesTheSameHomePagerTransitionAsOtherFixedTabs() throws Exception {
        String source = read("RecordingsActivity.java");
        assertTrue(source.contains("return 3 + homeTags.size();"));
        assertTrue(source.contains("position == 2"));
        assertTrue(source.contains("page = buildBooksTabPage();"));
        assertTrue(source.contains("homePager.setCurrentItem(2, true)"));
        assertFalse(source.contains("booksTabTitle.setOnClickListener(v -> startActivity"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
