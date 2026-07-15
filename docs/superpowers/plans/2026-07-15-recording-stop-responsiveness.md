# Recording Stop Responsiveness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the main recording screen keep a stable stop target, acknowledge stopping immediately, and finalize audio without blocking the Android main thread.

**Architecture:** Retain references to the timer, waveform host, and stop controls created by `RecordingsActivity.showRecording()`. The periodic runnable updates only the dynamic timer/waveform subtree, while stop finalization is guarded, dispatched through the existing single-thread I/O executor, and completed on the main thread through a dedicated helper.

**Tech Stack:** Java 17, Android native Views, `ExecutorService`, Android `Handler`, JUnit 4 source-contract tests, Android Gradle Plugin.

## Global Constraints

- Change only the main recording flow in `RecordingsActivity` and its JVM source-contract tests.
- Preserve recording formats, filenames, duration calculation, silence detection, captured photos, interview shutdown, upload behavior, tags, and navigation results.
- Keep the current user-facing action as stop; do not add pause/resume recording support.
- Do not modify iOS, mini-program, backend, API, WebSocket, article schema, image markers, or recording naming contracts.
- Preserve all unrelated working-tree changes already present in `RecordingsActivity.java` and `RecordingsActivitySourceTest.java`.

---

### Task 1: Lock the responsive recording UI contract with a failing test

**Files:**
- Create: `app/src/test/java/com/baixingai/voicedrop/RecordingStopResponsivenessSourceTest.java`
- Modify: `app/src/test/java/com/baixingai/voicedrop/RecordingsActivitySourceTest.java:357-372`
- Test: `app/src/test/java/com/baixingai/voicedrop/RecordingStopResponsivenessSourceTest.java`

**Interfaces:**
- Consumes: source for `RecordingsActivity.timerTick`, `showRecording(boolean)`, `updateRecordingUi()`, `stopRecordingFlow()`, and `completeStopRecording(...)`.
- Produces: regression constraints that prohibit periodic root replacement and synchronous main-thread recorder finalization.

- [ ] **Step 1: Write the focused failing source-contract tests**

Create `RecordingStopResponsivenessSourceTest.java`:

```java
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
            if (c == '}' && --depth == 0) return source.substring(brace, i + 1);
        }
        throw new IllegalArgumentException("Unclosed method " + signature);
    }
}
```

In `homeRefreshIsDeferredUntilActiveRecordingStops()`, read the new completion helper and move the navigation/order assertions to it:

```java
String completeStop = methodBody(source, "protected void completeStopRecording");

assertTrue(stopRecording.contains("boolean refreshDeferred = homeRefreshDeferredWhileRecording;"));
assertTrue(stopRecording.contains("main.post(() -> completeStopRecording"));
assertTrue(completeStop.contains("showHome();"));
assertTrue(completeStop.contains("if (refreshDeferred) refreshHomePages();"));
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.RecordingStopResponsivenessSourceTest --tests com.baixingai.voicedrop.RecordingsActivitySourceTest
```

Expected: `RecordingStopResponsivenessSourceTest` fails because `updateRecordingUi()`, saving-state fields/helpers, the duplicate-stop guard, and asynchronous completion do not exist yet. The failure must be an assertion or the deliberate `Missing ...` exception, not a compilation error.

- [ ] **Step 3: Review the test-only diff**

Run:

```bash
git diff -- app/src/test/java/com/baixingai/voicedrop/RecordingStopResponsivenessSourceTest.java app/src/test/java/com/baixingai/voicedrop/RecordingsActivitySourceTest.java
```

Expected: only the new regression test and the narrowly updated deferred-refresh assertions appear; retain the user's unrelated existing test changes.

---

### Task 2: Keep recording controls stable and finalize stop asynchronously

**Files:**
- Modify: `app/src/main/java/com/baixingai/voicedrop/RecordingsActivity.java:129-209,2128-2265,2335-2400`
- Test: `app/src/test/java/com/baixingai/voicedrop/RecordingStopResponsivenessSourceTest.java`
- Test: `app/src/test/java/com/baixingai/voicedrop/RecordingsActivitySourceTest.java`

**Interfaces:**
- Consumes: unchanged `RecordingBackend.stop(String)`, existing `io` executor, `main` handler, `RecordingQuality`, `confirmSilentRecording(...)`, `uploadTake(...)`, and `refreshHomePages()`.
- Produces: `updateRecordingUi()`, `showRecordingSavingState()`, and `completeStopRecording(AudioRecorder.Take, List<CapturedPhoto>, boolean)`.

- [ ] **Step 1: Add recording-view state and change the periodic tick**

Add fields beside the existing recorder/UI state:

```java
protected TextView recordingTimerText;
protected FrameLayout recordingWaveformHost;
protected LinearLayout recordingStopColumn;
protected View recordingStopButton;
protected TextView recordingStopLabel;
protected boolean recordingStopInProgress;
```

Change `timerTick` to update stable children:

```java
protected final Runnable timerTick = new Runnable() {
    @Override public void run() {
        if (recorder != null && recorder.isRecording() && !recordingStopInProgress) {
            updateRecordingUi();
            main.postDelayed(this, 500);
        }
    }
};
```

- [ ] **Step 2: Retain timer/waveform references and implement focused updates**

In `showRecording(boolean first)`, retain the timer and replace the direct waveform child with a stable host:

```java
TextView timer = text("00:00", 78, Theme.INK, Typeface.NORMAL);
timer.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
timer.setGravity(Gravity.CENTER);
recordingTimerText = timer;
center.addView(timer, new LinearLayout.LayoutParams(-2, -2));

FrameLayout waveformHost = new FrameLayout(this);
recordingWaveformHost = waveformHost;
center.addView(waveformHost, new LinearLayout.LayoutParams(-1, dp(46)));
updateRecordingUi();
```

Add the focused updater immediately after `showRecording`:

```java
protected void updateRecordingUi() {
    if (recorder == null || recordingTimerText == null || recordingWaveformHost == null) return;
    long elapsed = recorder.elapsedSeconds();
    recordingTimerText.setText(String.format("%02d:%02d", elapsed / 60, elapsed % 60));
    int amp = recorder.sampleCurrentAmplitude();
    double level = amp / 32767.0;
    recordingWaveformHost.removeAllViews();
    recordingWaveformHost.addView(buildWaveform(level),
            new FrameLayout.LayoutParams(-2, dp(46), Gravity.CENTER));
}
```

Store the three stop targets after constructing them:

```java
recordingStopColumn = stopCol;
recordingStopButton = stopBtn;
recordingStopLabel = stopLabel;
if (recordingStopInProgress) showRecordingSavingState();
```

- [ ] **Step 3: Add immediate saving feedback and duplicate protection**

Add:

```java
protected void showRecordingSavingState() {
    if (recordingStopLabel != null) {
        recordingStopLabel.setText("正在保存…");
        recordingStopLabel.setEnabled(false);
    }
    if (recordingStopColumn != null) recordingStopColumn.setEnabled(false);
    if (recordingStopButton != null) recordingStopButton.setEnabled(false);
}
```

At the start of `startRecordingFlow()`, reset the guard:

```java
recordingStopInProgress = false;
```

- [ ] **Step 4: Move recorder finalization to the I/O executor**

Replace `stopRecordingFlow()` with a guarded dispatch and extract the existing main-thread completion behavior:

```java
protected void stopRecordingFlow() {
    if (recordingStopInProgress || recorder == null || !recorder.isRecording()) return;
    recordingStopInProgress = true;
    main.removeCallbacks(timerTick);
    if (interviewer != null) {
        interviewer.stop();
        interviewer = null;
    }
    showRecordingSavingState();

    boolean refreshDeferred = homeRefreshDeferredWhileRecording;
    RecordingBackend stoppingRecorder = recorder;
    List<CapturedPhoto> photos = new ArrayList<>(capturedPhotos);
    capturedPhotos.clear();
    recordingStart = null;

    io.execute(() -> {
        AudioRecorder.Take take = stoppingRecorder.stop(null);
        main.post(() -> completeStopRecording(take, photos, refreshDeferred));
    });
}

protected void completeStopRecording(AudioRecorder.Take take, List<CapturedPhoto> photos,
                                     boolean refreshDeferred) {
    recordingStopInProgress = false;
    if (isFinishing() || isDestroyed()) return;
    closeOpenSwipes();
    clearHomePagerRefs();
    showHome();
    if (take != null) {
        if (RecordingQuality.looksSilent(take.peakAmplitude, take.duration)) {
            confirmSilentRecording(take, photos);
        } else {
            uploadTake(take, photos);
        }
    }
    if (refreshDeferred) refreshHomePages();
}
```

This leaves a successfully finalized file in local storage if the activity is already gone; the existing recording drain can discover it on a later active screen. Do not attempt to render dialogs or home UI on a destroyed activity.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.RecordingStopResponsivenessSourceTest --tests com.baixingai.voicedrop.RecordingsActivitySourceTest
```

Expected: both test classes pass with zero failures.

- [ ] **Step 6: Inspect the production diff for unrelated changes**

Run:

```bash
git diff --check
git diff -- app/src/main/java/com/baixingai/voicedrop/RecordingsActivity.java app/src/test/java/com/baixingai/voicedrop/RecordingsActivitySourceTest.java app/src/test/java/com/baixingai/voicedrop/RecordingStopResponsivenessSourceTest.java
```

Expected: the existing user changes remain intact, and new hunks are limited to stable recording updates, stop feedback, async completion, and their tests.

---

### Task 3: Verify the Android project and document device validation

**Files:**
- No production file changes expected.

**Interfaces:**
- Consumes: completed implementation from Task 2.
- Produces: fresh unit-test and build evidence plus a concrete manual validation checklist.

- [ ] **Step 1: Run the full required unit-test suite**

Run:

```bash
./gradlew testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 2: Compile the debug APK**

Run:

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` and a debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Record the remaining device/emulator validation**

Report these manual checks as pending unless an Android device or emulator is available:

1. Start recording and tap stop repeatedly at different points in the 500 ms waveform cycle.
2. Confirm the first tap immediately changes the label to `正在保存…`.
3. Confirm later taps do not trigger duplicate saves or duplicate navigation.
4. Confirm the UI remains responsive during encoder finalization.
5. Confirm one normal recording uploads through the existing flow and one silent recording still opens the existing silence confirmation.
6. Confirm camera and interview controls remain usable while the timer/waveform refreshes.
