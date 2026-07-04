package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class RecordingsActivitySourceTest {
    @Test
    public void homeRecordButtonSupportsTapRecordAndLongPressTalk() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(source.contains("轻点录音 · 长按说话"));
        assertTrue(source.contains("松开发送 · 上滑取消"));
        assertTrue(source.contains("上滑取消 · 松开放弃"));
        assertTrue(source.contains("startRecordingFlow();"));
        assertTrue(source.contains("startLibraryCommandTalk();"));
        assertTrue(source.contains("finishLibraryCommandTalk"));
        assertTrue(source.contains("commandSession.enqueue(text, currentCommandRefs())"));
        assertTrue(source.contains("HoldToTalkGesture.shouldCancel(startRawY[0], event.getRawY(), dp(60))"));
        assertTrue(source.contains("if (rec.uploading) continue;"));
        assertFalse(source.contains("if (!rec.hasArticles) continue;"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
