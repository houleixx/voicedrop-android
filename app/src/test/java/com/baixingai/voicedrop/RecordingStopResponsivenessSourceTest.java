package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecordingStopResponsivenessSourceTest {
    @Test
    public void periodicRecordingTickUpdatesStableViewsWithoutRebuildingPage() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String tick = source.substring(source.indexOf("protected final Runnable timerTick"),
                source.indexOf("@Override\n    protected void onCreate", source.indexOf("protected final Runnable timerTick")));
        String update = methodBody(source, "protected void updateRecordingUi()");
        String show = methodBody(source, "protected void showRecording(boolean first)");

        assertTrue(tick.contains("updateRecordingUi();"));
        assertFalse(tick.contains("showRecording(false);"));
        assertTrue(update.contains("recordingTimerText.setText"));
        assertTrue(update.contains("recordingWaveformHost.removeAllViews();"));
        assertTrue(show.contains("recordingTimerText = timer;"));
        assertTrue(show.contains("recordingWaveformHost = waveformHost;"));
    }

    @Test
    public void stopAcknowledgesOnceThenFinalizesOffMainThread() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String stop = methodBody(source, "protected void stopRecordingFlow()");

        assertTrue(stop.contains("if (recordingStopInProgress"));
        assertTrue(stop.contains("recordingStopInProgress = true;"));
        assertTrue(stop.contains("main.removeCallbacks(timerTick);"));
        assertTrue(stop.contains("showRecordingSavingState();"));
        assertTrue(stop.indexOf("showRecordingSavingState();") < stop.indexOf("io.execute(() ->"));
        assertTrue(stop.contains("io.execute(() ->"));
        assertTrue(stop.contains("stoppingRecorder.stop(null)"));
        assertTrue(stop.contains("main.post(() -> completeStopRecording"));
        assertFalse(stop.contains("AudioRecorder.Take take = recorder.stop(null);"));
    }

    @Test
    public void savingStateDisablesEveryStopClickTarget() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String saving = methodBody(source, "protected void showRecordingSavingState()");

        assertTrue(saving.contains("recordingStopLabel.setText(\"正在保存…\")"));
        assertTrue(saving.contains("recordingStopColumn.setEnabled(false)"));
        assertTrue(saving.contains("recordingStopButton.setEnabled(false)"));
        assertTrue(saving.contains("recordingStopLabel.setEnabled(false)"));
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
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(brace, i + 1);
            }
        }
        throw new IllegalArgumentException("Unclosed method " + signature);
    }
}
