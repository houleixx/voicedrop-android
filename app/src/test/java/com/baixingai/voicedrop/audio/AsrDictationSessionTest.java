package com.baixingai.voicedrop.audio;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class AsrDictationSessionTest {
    @Test public void captureChunkIsOneHundredMillisecondsOfPcm16Mono() {
        assertEquals(9600, AsrDictationSession.captureChunkBytes(48000, 100));
        assertEquals(8820, AsrDictationSession.captureChunkBytes(44100, 100));
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

        assertTrue(loop.contains("if (!running.get())"));
        assertTrue(loop.indexOf("if (!running.get())")
                < loop.indexOf("new AudioRecord("));
    }

    @Test public void asrDoesNotKeepIdlePreparedWebSocketBeforePressingRecord() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        assertTrue(source.contains("public void startCapture()"));
        assertTrue(source.contains("public void activate()"));
        String start = methodBody(source, "public void start()");
        String capture = methodBody(source, "public void startCapture()");
        String activate = methodBody(source, "public void activate()");

        assertFalse(source.contains("prewarmConnection"));
        assertFalse(source.contains("PreparedSocket"));
        assertFalse(source.contains("claimPreparedSocket"));
        assertTrue(start.contains("prepareCapture()"));
        assertTrue(start.contains("activate();"));
        assertTrue(capture.contains("startMicLoop"));
        assertFalse(capture.contains("client.newWebSocket"));
        assertTrue(activate.contains("client.newWebSocket"));
    }

    @Test public void startOpensSocketBeforeSubmittingMicrophoneCaptureLikeIos() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String start = methodBody(source, "public void start()");

        assertTrue(start.contains("prepareCapture()"));
        assertTrue(start.contains("activate();"));
        assertTrue(start.contains("startMicLoop(audioFrames);"));
        assertTrue(start.indexOf("activate();") < start.indexOf("startMicLoop(audioFrames);"));
    }

    @Test public void quickTapCanWaitForMicReleaseBeforeStartingNormalRecording() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        assertTrue(source.contains("public void stop(Runnable onStopped)"));
        String stop = methodBody(source, "public void stop(Runnable onStopped)");

        assertTrue(stop.contains("running.set(false)"));
        assertTrue(stop.contains("releaseRecorder(localRecorder)"));
        assertTrue(stop.contains("audioThread.execute(onStopped)"));
        assertTrue(stop.indexOf("releaseRecorder(localRecorder)")
                < stop.indexOf("audioThread.execute(onStopped)"));
    }

    @Test public void finishCallbackIsClaimedAtomicallyAndSessionExecutorsAreDisposed() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String notify = methodBody(source, "private void notifyFinishComplete()");
        String stop = methodBody(source, "public void stop(Runnable onStopped)");

        assertTrue(source.contains("AtomicReference<Runnable> finishComplete"));
        assertTrue(notify.contains("finishComplete.getAndSet(null)"));
        assertTrue(stop.contains("senderThread.shutdownNow()"));
        assertTrue(stop.contains("scheduler.shutdownNow()"));
        assertTrue(stop.contains("audioThread.shutdown()"));
    }

    @Test public void finishCannotScheduleTailAfterExecutorsHaveTerminated() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String finish = methodBody(source, "public void finish(Runnable onComplete)");
        String schedule = methodBody(source, "private boolean scheduleTailStop()");
        String shutdown = methodBody(source, "private void shutdownExecutors()");

        assertTrue(source.contains("boolean executorsShutdown"));
        assertTrue(schedule.contains("if (executorsShutdown) return false"));
        assertTrue(finish.contains("if (!scheduleTailStop())"));
        assertTrue(shutdown.contains("executorsShutdown = true"));
    }

    @Test public void microphoneAndSenderErrorsTerminateTheWholeSession() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String terminate = methodBody(source, "private void terminateWithError(String message)");
        String mic = source.substring(source.indexOf("private void startMicLoop"),
                source.indexOf("private void releaseRecorder"));
        String sender = source.substring(source.indexOf("private void startSenderLoop"),
                source.indexOf("/** Compute RMS amplitude"));

        assertTrue(mic.contains("terminateWithError(\"AudioRecord 初始化失败\")"));
        assertTrue(mic.contains("terminateWithError(e.getMessage())"));
        assertTrue(sender.contains("terminateWithError(\"听写连接超时\")"));
        assertTrue(sender.contains("terminateWithError(e.getMessage())"));
        assertTrue(terminate.contains("notifyFinishComplete()"));
        assertTrue(terminate.contains("shutdownExecutors()"));
    }

    @Test public void startAndFinishDoNotBlockUiWaitingForMicThread() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String start = source.substring(source.indexOf("public void start()"));
        start = start.substring(0, start.indexOf("/**\n     * Gracefully end dictation"));
        String finish = methodBody(source, "public void finish(Runnable onComplete)");

        assertFalse(start.contains("micReady.await"));
        assertTrue(source.contains("finish(Runnable onComplete)"));
        assertFalse(finish.contains(".await("));
    }

    @Test public void senderMatchesIosWithoutSyntheticSilenceOrCatchUpSleeps() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String sender = source.substring(source.indexOf("private void startSenderLoop"),
                source.indexOf("/** Compute RMS amplitude"));

        assertFalse(source.contains("ASR_WARMUP_SILENCE_MS"));
        assertFalse(source.contains("warmupSilencePcm"));
        assertFalse(sender.contains("Thread.sleep("));
        assertFalse(sender.contains("frames.queuedCount()"));
    }

    @Test public void microphoneCapturesAtFortyEightKhzAndConvertsBeforeQueueing() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String mic = source.substring(source.indexOf("private void startMicLoop"),
                source.indexOf("private void releaseRecorder"));

        assertTrue(source.contains("CAPTURE_SAMPLE_RATE = 48_000"));
        assertTrue(source.contains("ASR_SAMPLE_RATE = 16_000"));
        assertTrue(source.contains("CAPTURE_CHUNK_MS = 100"));
        assertTrue(mic.contains("new PcmDownsampler48To16()"));
        assertTrue(mic.contains("downsampler.downsample"));
        assertTrue(mic.contains("frames.offer(pcm16, false)"));
        assertTrue(mic.contains("downsampler.flush"));
    }

    @Test public void normalFinishCapturesAShortAudioTailBeforeStoppingMic() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String finish = methodBody(source, "public void finish(Runnable onComplete)");
        String schedule = methodBody(source, "private boolean scheduleTailStop()");

        assertTrue(source.contains("TAIL_CAPTURE_MS = 250"));
        assertTrue(finish.contains("scheduleTailStop()"));
        assertTrue(schedule.contains("scheduler.schedule"));
        assertTrue(schedule.contains("TAIL_CAPTURE_MS, TimeUnit.MILLISECONDS"));
        assertTrue(schedule.contains("running.set(false)"));
    }

    @Test public void staleWebSocketCallbacksAreGuardedByGeneration() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");

        assertTrue(source.contains("turnGeneration"));
        assertTrue(source.contains("final int generation"));
        assertTrue(source.contains("isCurrentTurn(webSocket, generation)"));
        assertTrue(source.contains("handleSocketFailure(webSocket, generation, t)"));
        assertTrue(source.contains("handleSocketClosed(webSocket, generation, code, reason)"));
    }

    @Test public void finalResultTimeoutStartsOnlyAfterTheLastAudioFrameIsSent() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/AsrDictationSession.java");
        String finish = methodBody(source, "public void finish(Runnable onComplete)");
        String sender = source.substring(source.indexOf("private void startSenderLoop"),
                source.indexOf("/** Compute RMS amplitude"));

        assertFalse(finish.contains("scheduler.schedule"));
        assertTrue(sender.contains("if (frame.isLast)"));
        assertTrue(sender.contains("armFinishTimeout()"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
}
