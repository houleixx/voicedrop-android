package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class UsageCardStyleSourceTest {
    @Test
    public void whiteUsageCardsUseTheSharedCardBackgroundAndBorder() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/UsageActivity.java");

        assertTrue(source.contains(
                "card.setBackground(strokedRound(Theme.CARD, 11, Theme.BORDER_CHROME))"));
        assertTrue(countOccurrences(source,
                "card.setBackground(strokedRound(Theme.CARD, 11, Theme.BORDER_CHROME))") >= 2);
    }

    private static int countOccurrences(String source, String needle) {
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
