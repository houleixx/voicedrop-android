package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BookWritingSettingsEntryTest {
    @Test
    public void bookWritingLivesInSettingsExperimentalSection() throws Exception {
        String settings = read("SettingsActivity.java");
        String about = read("AboutActivity.java");
        assertTrue(settings.contains("addSection(content, \"实验功能\")"));
        assertTrue(settings.contains("\"写书\", \"每本 320 算力"));
        assertTrue(settings.contains("this::openBookWriting"));
        assertFalse(about.contains("\"写书\""));
    }

    private static String read(String name) throws Exception {
        java.nio.file.Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
