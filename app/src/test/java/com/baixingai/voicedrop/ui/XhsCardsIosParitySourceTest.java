package com.baixingai.voicedrop.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class XhsCardsIosParitySourceTest {
    @Test
    public void textCardsKeepIosTypographyAndParagraphRhythm() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/XhsCards.java");

        assertTrue(source.contains("private static final int PARAGRAPH_SPACING = 36;"));
        assertTrue(source.contains("new ParagraphSpacingSpan(PARAGRAPH_SPACING)"));
        assertTrue(source.contains("systemTypeface(600)"));
        assertTrue(source.contains("systemTypeface(500)"));
        assertTrue(source.contains("paint.setLetterSpacing(0.012f)"));
        assertFalse(source.contains("textPaint(78, INK, Typeface.BOLD)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
