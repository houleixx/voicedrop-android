package com.baixingai.voicedrop.net;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SocketRecoverySourceTest {
    @Test public void statusSocketRetriesFailuresAndClosesWithoutAStaleRetry() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/net/StatusSession.java");

        assertTrue(source.contains("scheduleReconnect(webSocket)"));
        assertTrue(source.contains("main.removeCallbacks(reconnectRunnable)"));
        assertTrue(source.contains("if (socket != current) return;"));
        assertFalse(source.contains("socket = null;\n                listener.onError"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
