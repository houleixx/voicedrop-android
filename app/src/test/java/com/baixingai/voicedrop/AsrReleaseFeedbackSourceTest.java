package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AsrReleaseFeedbackSourceTest {
    @Test public void homeShowsRecognitionProgressAfterFingerRelease() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String finish = methodBody(source, "protected void finishLibraryCommandTalk");

        assertTrue(finish.contains("正在识别…"));
        assertTrue(finish.contains("session.finish"));
    }

    @Test public void audioDetailKeepsProgressVisibleWhileSharedAsrFinishes() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String finish = methodBody(source, "protected void finishHoldArticleEdit");
        String liveText = methodBody(source, "protected void updateHoldArticleEditLiveText");

        assertTrue(finish.contains("正在整理…"));
        assertTrue(finish.contains("holdEditPromptText = \"正在识别…\""));
        assertTrue(finish.contains("updateHoldArticleEditTranscriptBubble()"));
        assertTrue(finish.contains("session.finish"));
        assertFalse(liveText.contains("holdEditFinishing"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("Method not found: " + signature);
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
