package com.baixingai.voicedrop;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertTrue;

/** Protects the cross-platform physical shelf details defined by iOS BooksShelfView. */
public final class BooksShelfVisualContractTest {
    @Test public void shelfKeepsIosPhysicalBookStructure() throws Exception {
        String source = read("ui/BooksShelfPanel.java");
        assertTrue(source.contains("width / 0.7f"));
        assertTrue(source.contains("weightedCellParams(dp(22))"));
        assertTrue(source.contains("new int[]{0xffe3d7c2, 0xffc9b99e}"));
        assertTrue(source.contains("params.topMargin = dp(6)"));
        assertTrue(source.contains("float spineWidth = 13 * density"));
        assertTrue(source.contains("int pageWidth = Math.max(1, Math.round(3 * density))"));
        assertTrue(source.contains("paper.setStroke(dpF(1.5f), 0xffcfc0a6, dp(5), dp(4))"));
        assertTrue(source.contains("Typeface.create(serif ? \"serif\" : \"sans-serif\", style)"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
