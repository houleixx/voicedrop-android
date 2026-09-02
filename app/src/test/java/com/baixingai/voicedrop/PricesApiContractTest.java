package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Locks the public price endpoint and keeps local gating subordinate to server 402 responses. */
public final class PricesApiContractTest {
    @Test public void priceRefreshIsPublicDailyAndBookWritingUsesIt() throws Exception {
        String prices = read("data/Prices.java");
        String writer = read("BookWritingActivity.java");
        assertTrue(prices.contains("Api.agentBase() + \"/usage/prices\", \"\""));
        assertTrue(prices.contains("24L * 60 * 60 * 1000"));
        assertTrue(prices.contains("new Table(160, 40, 0)"));
        assertTrue(writer.contains("Prices.refreshIfNeeded(this, new HttpClient()).book"));
        assertTrue(writer.contains("result.balance != null"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
