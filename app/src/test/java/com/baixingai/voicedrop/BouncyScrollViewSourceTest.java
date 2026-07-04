package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class BouncyScrollViewSourceTest {
    @Test
    public void bounceTranslatesContentNotScrollContainer() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/BouncyScrollView.java");

        assertTrue(source.contains("private View bouncyChild()"));
        assertTrue(source.contains("View child = bouncyChild();"));
        assertTrue(source.contains("child.setTranslationY(next);"));
        assertTrue(source.contains("child.getTranslationY()"));
        assertFalse(source.contains("float next = getTranslationY() +"));
        assertFalse(source.contains("\n        setTranslationY(next);"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
