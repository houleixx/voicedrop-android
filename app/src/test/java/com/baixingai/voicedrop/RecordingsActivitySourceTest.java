package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class RecordingsActivitySourceTest {
    @Test
    public void silentResumeRefreshRedrawsOnlyWhenRecordingSnapshotChanges() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String silent = methodBody(source, "protected void refreshDataSilently");

        assertTrue(silent.contains("recordingsViewSignature"));
        assertTrue(silent.contains("refreshHomeAfterRecordingLoad"));
    }

    @Test
    public void appTitleUsesVoiceDropBranding() throws Exception {
        String recordings = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String community = readSource("src/main/java/com/baixingai/voicedrop/CommunityActivity.java");
        String strings = readSource("src/main/res/values/strings.xml");

        assertTrue(recordings.contains("\" VoiceDrop 口述\""));
        assertTrue(community.contains("\" VoiceDrop 口述\""));
        assertTrue(strings.contains("<string name=\"app_name\">VoiceDrop</string>"));
    }

    @Test
    public void rowCoverStaysSquareUntilDedicatedCoverSucceedsLikeIos() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String row = methodBody(source, "protected View recordingRow");
        String load = methodBody(source, "protected void maybeLoadRowCover");

        assertTrue(row.contains("iconWrap.addView(waveIcon, new FrameLayout.LayoutParams(dp(44), dp(44)"));
        assertTrue(row.contains("row.addView(iconWrap, new LinearLayout.LayoutParams(dp(44), dp(44)))"));
        assertTrue(load.indexOf("bitmap = PhotoService.thumbnail(dedicatedFullKey)")
                < load.indexOf("dedicated = bitmap != null"));
        assertTrue(load.indexOf("if (bitmap == null)")
                < load.indexOf("ArticleBody.firstPhotoKey"));
        String display = methodBody(source, "protected View displayRowCover");
        assertTrue(display.contains("int width = dedicated ? dp(40) : dp(44)"));
        assertTrue(display.contains("int height = dedicated ? dp(60) : dp(44)"));

        String beforeUiSuccess = load.substring(0, load.indexOf("main.post(() ->"));
        assertFalse(beforeUiSuccess.contains("getLayoutParams().height"));
    }

    @Test
    public void swipeDeleteDisallowsParentScrollAfterHorizontalIntent() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String pullRefresh = readSource("src/main/java/com/baixingai/voicedrop/ui/PullRefreshLayout.java");

        assertTrue(source.contains("v.getParent().requestDisallowInterceptTouchEvent(true)"));
        assertTrue(source.contains("v.getParent().requestDisallowInterceptTouchEvent(false)"));
        assertTrue(pullRefresh.contains("downX = ev.getX()"));
        assertTrue(pullRefresh.contains("if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy))"));
        assertTrue(pullRefresh.contains("return false"));
    }

    @Test
    public void homeSupportsDynamicTagTabsAndTaggedRecordings() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(source.contains("protected final List<String> homeTags = new ArrayList<>()"));
        assertTrue(source.contains("selectedTag = tag"));
        assertTrue(source.contains("buildTagTabPage(tag)"));
        assertTrue(source.contains("recordingsForTag(tag)"));
        assertTrue(source.contains("defaultRecordTag = link.tag"));
        assertTrue(source.contains("Uploader.writeTagsSidecar(take.file, java.util.Collections.singletonList(defaultRecordTag))"));
    }

    @Test
    public void tagChangesRebuildHomeShellSoNewTabsAppearLikeIos() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(source.contains("protected boolean refreshHomeTagsFromRecordings()"));
        assertTrue(source.contains("return !previous.equals(homeTags);"));
        assertTrue(source.contains("protected void refreshHomeAfterRecordingLoad(boolean tagsChanged)"));
        assertTrue(source.contains("if (tagsChanged && homePager != null"));
        assertTrue(source.contains("clearHomePagerRefs();"));
        assertTrue(source.contains("showHome();"));
    }

    @Test
    public void recordingRowsRenderTagsInMetaLine() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(source.contains("formatTags(rec.tags)"));
        assertTrue(source.contains("metaParts.add(formatTags(rec.tags))"));
    }

    @Test
    public void deletingARecordingRemovesOnlyItsExistingRowsWithoutReloadingOtherIcons() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String row = methodBody(source, "protected View recordingRow");
        String remove = methodBody(source, "protected void removeRecordingFromHome");

        assertTrue(row.contains("container.setTag(rec.audioName)"));
        assertTrue(row.contains("confirmDeleteRecording(rec, () -> removeRecordingFromHome(rec))"));
        assertFalse(row.contains("confirmDeleteRecording(rec, null)"));
        assertTrue(remove.contains("recordingsListsByPage.values()"));
        assertTrue(remove.contains("list.removeViewAt(i)"));
        assertTrue(remove.contains("rec.audioName.equals(child.getTag())"));
        assertFalse(remove.contains("refreshAndDrain()"));
        assertFalse(remove.contains("refreshHomePages()"));
        assertFalse(remove.contains("populateRecordingList("));
    }

    @Test
    public void phaseUpdatesChangeOnlyTheMatchingMountedRow() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String markPhase = methodBody(source, "protected void markPhase");
        String updateRows = methodBody(source, "protected void updateRecordingPhaseRows");
        String row = methodBody(source, "protected View recordingRow");

        assertTrue(markPhase.contains("updateRecordingPhaseRows(changed)"));
        assertFalse(markPhase.contains("showHome()"));
        assertFalse(markPhase.contains("refreshHomePages()"));
        assertTrue(updateRows.contains("recordingsListsByPage.values()"));
        assertTrue(updateRows.contains("rec.audioName.equals(row.getTag())"));
        assertTrue(updateRows.contains("ROW_STATUS_LABEL_TAG"));
        assertTrue(updateRows.contains("label.setText(rec.statusLabel())"));
        assertFalse(updateRows.contains("populateRecordingList("));
        assertTrue(row.contains("chip.setTag(ROW_STATUS_LABEL_TAG)"));
    }

    @Test
    public void completedStatusRefreshesOnlyItsRecordingRow() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String statusListener = source.substring(source.indexOf("statusSession = new StatusSession"),
                source.indexOf("restoreCachedCommunityFeed();"));
        String complete = methodBody(source, "protected void refreshCompletedRecording");
        String replace = methodBody(source, "protected void replaceRecordingRows");

        assertTrue(statusListener.contains("refreshCompletedRecording(stem, status)"));
        assertFalse(statusListener.contains("RecordingsActivity.this::refreshAndDrain"));
        assertFalse(complete.contains("showHome()"));
        assertFalse(complete.contains("refreshHomePages()"));
        assertTrue(complete.contains("replaceRecordingRows(refreshed)"));
        assertTrue(source.contains("previousAudioName.equals(row.getTag())"));
        assertTrue(source.contains("list.removeViewAt(i)"));
        assertTrue(source.contains("list.addView(recordingRow(rec), i)"));
    }

    @Test
    public void universalShareLinksResolveThroughFilesApi() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(source.contains("openShareLink(link.id, link.url)"));
        assertTrue(source.contains("library.resolveShareLink(id)"));
        assertTrue(source.contains("target.isCommunity()"));
        assertTrue(source.contains("openWebFallback(fallbackUrl)"));
    }

    @Test
    public void manifestExposesStartRecordingShortcut() throws Exception {
        String manifest = readSource("src/main/AndroidManifest.xml");
        String shortcuts = readSource("src/main/res/xml/shortcuts.xml");

        assertTrue(manifest.contains("android.app.shortcuts"));
        assertTrue(shortcuts.contains("android:shortcutId=\"start_recording\""));
        assertTrue(shortcuts.contains("android:data=\"voicedrop://record\""));
        assertTrue(shortcuts.contains("android:targetClass=\"com.baixingai.voicedrop.RecordingsActivity\""));
    }

    @Test
    public void aiInterviewerSharesPrimaryRecorderCaptureInModernMode() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String start = methodBody(source, "protected void startRecordingFlow");
        String create = methodBody(source, "protected RecordingBackend createRecorderBackend");

        assertTrue(source.contains("protected RecordingBackend recorder;"));
        assertTrue(start.contains("EngineRecorder engineRecorder = null;"));
        assertTrue(start.contains("engineRecorder.setPcmListener"));
        assertTrue(start.contains("interviewer.onPcm16(pcm16le, sampleRate)"));
        assertTrue(source.contains("recorder = createRecorderBackend();"));
        assertTrue(create.contains("if (prefs.classicRecorder())"));
        assertTrue(create.contains("new AudioRecorder(this)"));
        assertTrue(create.contains("new EngineRecorder(this)"));
    }

    @Test
    public void recordingPhotoButtonUsesTheFullPickerAndKeepsPhotoUploadBeforeAudio() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String openCamera = methodBody(source, "protected void openCamera");
        String result = methodBody(source, "protected void onActivityResult");
        String upload = methodBody(source, "protected void uploadTake");

        assertTrue(openCamera.contains("new Intent(this, InsertPhotoActivity.class)"));
        assertFalse(openCamera.contains("resolveActivity"));
        assertTrue(result.contains("InsertPhotoActivity.EXTRA_PHOTO_PATHS"));
        assertTrue(result.contains("InsertPhotoActivity.EXTRA_CAPTURE_TIMES"));
        assertTrue(result.contains("addRecordingPhotoPath"));
        assertTrue(upload.indexOf("uploadCapturedPhotos(take.file.getName(), photos)") < upload.indexOf("uploader.upload(take.file)"));
        assertTrue(upload.contains("if (!uploadCapturedPhotos(take.file.getName(), photos))"));
        assertTrue(upload.contains("if (!uploader.upload(take.file))"));
    }

    @Test
    public void homeRefreshIsDeferredUntilActiveRecordingStops() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String showHome = methodBody(source, "protected void showHome()");
        String stopRecording = methodBody(source, "protected void stopRecordingFlow()");
        String completeStop = methodBody(source, "protected void completeStopRecording");

        assertTrue(source.contains("protected boolean homeRefreshDeferredWhileRecording;"));
        assertTrue(showHome.contains("if (recorder != null && recorder.isRecording())"));
        assertTrue(showHome.contains("homeRefreshDeferredWhileRecording = true;"));
        assertTrue(showHome.indexOf("homeRefreshDeferredWhileRecording = true;")
                < showHome.indexOf("root.removeAllViews();"));
        assertTrue(stopRecording.contains("boolean refreshDeferred = homeRefreshDeferredWhileRecording;"));
        assertTrue(stopRecording.contains("main.post(() -> completeStopRecording"));
        assertTrue(stopRecording.indexOf("showHome();") < stopRecording.indexOf("io.execute(() ->"));
        assertFalse(completeStop.contains("showHome();"));
        assertTrue(completeStop.contains("if (refreshDeferred) refreshHomePages();"));
    }

    @Test
    public void stoppedRecordingAppearsAsSavingBeforeItsBackgroundUpload() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String completeStop = methodBody(source, "protected void completeStopRecording");
        String addSaving = methodBody(source, "protected void addSavingRecording");
        String markUploading = methodBody(source, "protected void markRecordingUploading");
        String upload = methodBody(source, "protected void uploadTake");

        String stop = methodBody(source, "protected void stopRecordingFlow");
        String beginSaving = methodBody(source, "protected Recording beginSavingRecording");
        assertTrue(stop.indexOf("beginSavingRecording(start, elapsedSeconds)") < stop.indexOf("showHome();"));
        assertTrue(beginSaving.contains("saving.saving = true;"));
        assertTrue(markUploading.contains("rec.saving = false;"));
        assertTrue(markUploading.contains("rec.uploading = true;"));
        assertTrue(upload.indexOf("markRecordingUploading(take.file.getName())")
                < upload.indexOf("uploader.upload(take.file)"));
    }

    @Test
    public void completedUploadReplacesOnlyItsRecordingRowUnlessTagsChanged() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String upload = methodBody(source, "protected void uploadTake");

        assertFalse(upload.contains("main.post(this::showHome)"));
        assertTrue(upload.contains("tagsChanged = loadRecordingsAndPublishPendingReplies()"));
        assertTrue(upload.contains("if (rebuildHome) refreshHomeAfterRecordingLoad(true)"));
        assertTrue(upload.contains("else if (refreshed != null) replaceRecordingRows(refreshed)"));
    }

    @Test
    public void aiInterviewButtonUsesIosLikeWaveformMicIcon() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String icon = readSource("src/main/res/drawable/ic_waveform_mic.xml");

        assertTrue(source.contains("R.drawable.ic_waveform_mic"));
        assertFalse(source.contains("AliIconFont.apply(aiIcon, AliIconFont.MIC"));
        assertTrue(icon.contains("android:viewportWidth=\"24\""));
        assertTrue(icon.contains("android:strokeLineCap=\"round\""));
        assertTrue(icon.contains("M3.2,13.2"));
        assertTrue(icon.contains("M12.2,18.4"));
        assertTrue(icon.contains("M15.2,12.2"));
        assertFalse(icon.contains("M15,5"));
    }

    @Test
    public void homeUsesOrdinaryRecordingForTapAndHoldWithoutCommandSession() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String fab = methodBody(source, "protected void addRecordFab");
        String touch = methodBody(source, "protected View.OnTouchListener createRecordFabTouchListener");
        String start = methodBody(source, "protected void startRecordingFlow");
        assertTrue(fab.contains("fab.setOnClickListener(v -> startRecordingFlow())"));
        assertTrue(fab.contains("fab.setOnTouchListener(createRecordFabTouchListener())"));
        assertTrue(fab.contains("轻点录音"));
        assertTrue(touch.contains("RecordButtonGesture.HOLD_MILLIS"));
        assertTrue(touch.contains("recordButtonGesture.hold()"));
        assertTrue(touch.contains("performHapticFeedback"));
        assertTrue(touch.contains("startRecordingFlow();"));
        assertTrue(touch.contains("if (launch) v.performClick()"));
        assertTrue(methodBody(source, "protected void onPause").contains("cancelRecordHold()"));
        assertTrue(start.contains("recordPermissionPending || (recorder != null && recorder.isRecording())"));
        assertTrue(methodBody(source, "public void onRequestPermissionsResult").contains("recordPermissionPending = false"));
        assertFalse(source.contains("LibraryCommand"));
        assertFalse(source.contains("commandDictationSession"));
        assertFalse(source.contains("COMMAND_NUMBER_BADGE_TAG"));
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
