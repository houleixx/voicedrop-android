package com.baixingai.voicedrop.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class UsageStoreSourceTest {
    @Test
    public void usageStoreUsesServerSummaryAndCursorBasedLedgerPagination() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/UsageStore.java");

        assertTrue(source.contains("/usage/summary"));
        assertTrue(source.contains("has_more"));
        assertTrue(source.contains("next"));
        assertTrue(source.contains("&before="));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
