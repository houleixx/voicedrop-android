package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeHeaderSpacingSourceTest {
    @Test
    public void homeBrandAndTabsUseCompactVerticallyAlignedSpacing() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        String compactTopBar = "SystemBarDefaults.applyTopInsets(topBar, dp(18), dp(8), dp(12), 0)";
        assertEquals(2, occurrences(source, compactTopBar));
        assertTrue(source.contains("recordingsTabTitle.setPadding(0, dp(6), dp(14), dp(6))"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
