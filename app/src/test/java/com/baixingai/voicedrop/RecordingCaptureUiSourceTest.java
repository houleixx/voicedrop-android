package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class RecordingCaptureUiSourceTest {
    @Test
    public void recordingPhotoStripClipsThumbnailsToRoundedCorners() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String filmstrip = source.substring(
                source.indexOf("protected View recordingFilmstrip()"),
                source.indexOf("protected void startRecordingFlow()"));

        assertTrue(filmstrip.contains("new RoundedImageView(this)"));
    }

    private static String readSource(String relativePath) throws Exception {
        Path direct = Paths.get(relativePath);
        Path path = Files.exists(direct) ? direct : Paths.get("app").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
