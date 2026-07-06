package com.baixingai.voicedrop.audio;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class AsrDictationSessionTest {
    @Test public void warmupSilenceUsesSixteenBitMonoPcm() {
        byte[] silence = AsrDictationSession.warmupSilencePcm(16000, 160);

        assertEquals(5120, silence.length);
        for (byte value : silence) {
            assertEquals(0, value);
        }
    }

    @Test public void frameDurationMatchesSixteenBitMonoPcmLength() {
        assertEquals(200, AsrDictationSession.frameDurationMs(new byte[6400], 16000));
        assertEquals(0, AsrDictationSession.frameDurationMs(new byte[0], 16000));
    }

    @Test public void stoppingDictationDoesNotEmitPersistentStoppedState() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");

        assertFalse(source.contains("听写已停止"));
    }

    @Test public void audioRecorderReleaseUsesLocalInstanceToAvoidStopRace() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");

        assertTrue(source.contains("private final Object recorderLock = new Object();"));
        assertTrue(source.contains("AudioRecord localRecorder"));
        assertTrue(source.contains("releaseRecorder(localRecorder)"));
        assertFalse(source.contains("recorder.release();"));
    }

    @Test public void micLoopDoesNotOpenRecorderAfterFastCancel() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String loop = source.substring(source.indexOf("private void startMicLoop"));
        loop = loop.substring(0, loop.indexOf("private void releaseRecorder"));

        assertTrue(loop.contains("if (!running.get()) return;"));
        assertTrue(loop.indexOf("if (!running.get()) return;")
                < loop.indexOf("new AudioRecord("));
    }

    @Test public void asrDoesNotKeepIdlePreparedWebSocketBeforePressingRecord() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String start = source.substring(source.indexOf("public void start()"));
        start = start.substring(0, start.indexOf("/**\n     * Gracefully end dictation"));

        assertFalse(source.contains("prewarmConnection"));
        assertFalse(source.contains("PreparedSocket"));
        assertFalse(source.contains("claimPreparedSocket"));
        assertTrue(start.contains("client.newWebSocket"));
    }

    @Test public void startDoesNotBlockUiWaitingForMicThread() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String start = source.substring(source.indexOf("public void start()"));
        start = start.substring(0, start.indexOf("/**\n     * Gracefully end dictation"));

        assertFalse(start.contains("micReady.await"));
        assertTrue(source.contains("finish(Runnable onComplete)"));
        assertTrue(source.substring(source.indexOf("public void finish(Runnable onComplete)")).contains("latch.await"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
