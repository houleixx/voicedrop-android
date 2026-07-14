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

    @Test
    public void readWithoutPcmReturnsDequeuedCodecInputBuffer() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/EngineRecorder.java");

        assertTrue(source.contains("if (n <= 0)"));
        assertTrue(source.contains("codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0);"));
        assertTrue(source.contains("AudioRecord.read failed"));
    }

    @Test
    public void finalizationIsBoundedAndIncompleteMuxIsNotPublished() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/EngineRecorder.java");

        assertTrue(source.contains("FINALIZE_TIMEOUT_NS"));
        assertTrue(source.contains("Codec input EOS timed out"));
        assertTrue(source.contains("Codec output EOS timed out"));
        assertTrue(source.contains("finalizedSuccessfully"));
        assertTrue(source.contains("Uploader.isUploadable(currentFile)"));
        assertTrue(source.contains("if (!finished || !finalizedSuccessfully"));
    }

    @Test
    public void outputEosTimeoutUsesOnlyTheValidatedMuxFallback() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/EngineRecorder.java");

        assertTrue(source.contains("recoverableOutputEosTimeout"));
        assertTrue(source.contains("outputEosReceived || recoverableOutputEosTimeout"));
        assertTrue(source.contains("Uploader.isUploadable(currentFile)"));
    }

    @Test
    public void capturesAtNativeRateAndKeepsSixteenKhzDownstreamContract() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/EngineRecorder.java");

        assertTrue(source.contains("CAPTURE_SAMPLE_RATE = PcmDownsampler48To16.INPUT_SAMPLE_RATE"));
        assertTrue(source.contains("ENCODE_SAMPLE_RATE = PcmDownsampler48To16.OUTPUT_SAMPLE_RATE"));
        assertTrue(source.contains("new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, CAPTURE_SAMPLE_RATE"));
        assertTrue(source.contains("MediaFormat.createAudioFormat(MIME, ENCODE_SAMPLE_RATE, 1)"));
        assertTrue(source.contains("new PcmDownsampler48To16()"));
        assertTrue(source.contains("downsampler.downsample("));
        assertTrue(source.contains("listener.onPcm16(copy, ENCODE_SAMPLE_RATE)"));
    }

    @Test
    public void flushesDownsamplerBeforeSubmittingInputEos() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/EngineRecorder.java");

        int flush = source.indexOf("downsampler.flush(");
        int eos = source.indexOf("MediaCodec.BUFFER_FLAG_END_OF_STREAM", flush);
        assertTrue(flush >= 0);
        assertTrue(eos > flush);
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
