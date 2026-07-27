package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class RecordingPhotoMarkerRepairSourceTest {
    @Test public void remembersExpectedPhotoKeysBeforeAudioAndRepairsReadyArticles() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String photos = methodBody(source, "protected boolean uploadCapturedPhotos");
        String load = methodBody(source, "protected boolean loadRecordingsAndPublishPendingReplies");

        assertTrue(photos.indexOf("uploader.stagePhotos(staged)")
                < photos.indexOf("photoMarkerRepairs.remember(recordingName, photoKeys)"));
        assertTrue(photos.contains("return photoKeys.isEmpty() || photoMarkerRepairs.remember"));
        assertTrue(load.indexOf("library.load(")
                < load.indexOf("photoMarkerRepairs.repairReady(recordings, library)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new IllegalArgumentException("Missing " + signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}') {
                depth--;
                if (depth == 0) return source.substring(brace, i + 1);
            }
        }
        throw new IllegalArgumentException("Unclosed method " + signature);
    }
}
