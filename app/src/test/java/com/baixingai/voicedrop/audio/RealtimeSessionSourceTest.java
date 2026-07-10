package com.baixingai.voicedrop.audio;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class RealtimeSessionSourceTest {
    @Test
    public void realtimeRelayUsesPcmuFormatAndGenerationGuard() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/RealtimeSession.java");

        assertTrue(source.contains("/realtime/relay?fmt=pcmu"));
        assertTrue(source.contains("Authorization"));
        assertTrue(source.contains("generation"));
        assertTrue(source.contains("response.output_audio.delta"));
        assertTrue(source.contains("input_audio_buffer.append"));
    }

    @Test
    public void interviewerSchedulesReconnectWhenRelayDegrades() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/RealtimeInterviewer.java");

        assertTrue(source.contains("scheduleReconnect"));
        assertTrue(source.contains("reconnectAttempt < 6"));
        assertTrue(source.contains("1L << reconnectAttempt"));
        assertTrue(source.contains("session.connect()"));
    }

    @Test
    public void interviewerConsumesRecorderTeeInsteadOfOpeningSecondMicrophone() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/RealtimeInterviewer.java");

        assertTrue(source.contains("public void onPcm16(byte[] pcm16le, int sampleRate)"));
        assertTrue(source.contains("session.appendAudio(MuLaw.pcm16ToPcmu8k(pcm16le, sampleRate))"));
        assertFalse(source.contains("new AudioRecord"));
        assertFalse(source.contains("startCapture"));
        assertFalse(source.contains("releaseRecorder"));
    }

    @Test
    public void interviewerPlaysAiAudioOnMediaVolumeStream() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/RealtimeInterviewer.java");

        assertTrue(source.contains("AudioAttributes.USAGE_MEDIA"));
        assertFalse(source.contains("AudioAttributes.USAGE_VOICE_COMMUNICATION"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
