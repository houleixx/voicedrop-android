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

    @Test public void commandSocketRestoresConfirmationAndControlState() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/net/LibraryCommandSession.java");

        assertTrue(source.contains("CommandStateStore.loadControls"));
        assertTrue(source.contains("CommandStateStore.loadConfirmations"));
        assertTrue(source.contains("obj.optString(\"summary\""));
        assertTrue(source.contains("flushControls()"));
        assertTrue(source.contains("notifyConfirmations()"));
    }

    @Test public void completedSnapshotClearsAControlWhoseReplyWasMissed() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/net/LibraryCommandSession.java");
        String reconcile = source.substring(source.indexOf("private void reconcile"),
                source.indexOf("private static List<String> strings"));

        assertTrue(reconcile.contains("for (String id : done) clearCommandState(id);"));
    }

    @Test public void commandSocketLogsMessageTypeAndIdWithoutInstructionText() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/net/LibraryCommandSession.java");
        String handle = source.substring(source.indexOf("private void handle(String text)"),
                source.indexOf("private void reconcile"));

        assertTrue(source.contains("private static final String TAG = \"LibraryCommandSession\";"));
        assertTrue(handle.contains("Log.d(TAG, \"message type=\" + type + \" id=\""));
        assertFalse(handle.contains("Log.d(TAG, text)"));
        assertFalse(handle.contains("Log.d(TAG, \"message=\" + text)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
