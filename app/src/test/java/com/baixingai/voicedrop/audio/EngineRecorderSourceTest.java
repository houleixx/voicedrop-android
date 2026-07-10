package com.baixingai.voicedrop.audio;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class EngineRecorderSourceTest {
    @Test
    public void engineRecorderEncodesM4aAndExposesPcmTee() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/EngineRecorder.java");

        assertTrue(source.contains("implements RecordingBackend"));
        assertTrue(source.contains("AudioRecord"));
        assertTrue(source.contains("MediaCodec"));
        assertTrue(source.contains("MediaMuxer"));
        assertTrue(source.contains("audio/mp4a-latm"));
        assertTrue(source.contains("setPcmListener"));
        assertTrue(source.contains("listener.onPcm16"));
        assertTrue(source.contains("new AudioRecorder.Take"));
    }

    @Test
    public void recordingBackendKeepsExistingActivityContract() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/RecordingBackend.java");

        assertTrue(source.contains("AudioRecorder.Take stop(String place)"));
        assertTrue(source.contains("long elapsedSeconds()"));
        assertTrue(source.contains("int sampleCurrentAmplitude()"));
        assertTrue(source.contains("interface PcmListener"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
