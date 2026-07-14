package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class RecordingsActivitySourceTest {
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
    public void homeRecordButtonSupportsTapRecordAndLongPressTalk() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(source.contains("轻点录音 · 长按说话"));
        assertTrue(source.contains("松开发送 · 上滑取消"));
        assertTrue(source.contains("上滑取消 · 松开放弃"));
        assertTrue(source.contains("startRecordingFlow();"));
        assertTrue(source.contains("startLibraryCommandTalk();"));
        assertTrue(source.contains("finishLibraryCommandTalk"));
        assertTrue(source.contains("commandSession.enqueue(text, currentCommandRefs())"));
        assertTrue(source.contains("HoldToTalkGesture.shouldCancel(startRawY[0], event.getRawY(), dp(60))"));
        assertTrue(source.contains("if (rec.uploading) continue;"));
        assertFalse(source.contains("if (!rec.hasArticles) continue;"));
    }

    @Test
    public void homeRecordTouchOnlyRespondsOnRedButton() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String fab = methodBody(source, "protected void addRecordFab");

        assertTrue(fab.contains("fab.setOnTouchListener(commandTouch);"));
        assertFalse(fab.contains("fabCol.setOnTouchListener(commandTouch);"));
        assertFalse(fab.contains("fabRing.setOnTouchListener(commandTouch);"));
        assertFalse(fab.contains("fabLabel.setOnTouchListener(commandTouch);"));
    }

    @Test
    public void homeDoesNotShowPersistentLibraryCommandConnectionState() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String commandSessionListener = source.substring(source.indexOf("commandSession = new LibraryCommandSession"));
        commandSessionListener = commandSessionListener.substring(0, commandSessionListener.indexOf("root = new FrameLayout"));

        assertFalse(commandSessionListener.contains("commandReply = state"));
        assertTrue(commandSessionListener.contains("connection lifecycle is quiet"));
        assertTrue(commandSessionListener.contains("isQuietLibraryCommandMessage(text)"));
        assertTrue(commandSessionListener.contains("isQuietLibraryCommandMessage(message)"));
    }

    @Test
    public void homeCommandReplyUsesBubbleWithoutToastLikeIos() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String commandSessionListener = source.substring(source.indexOf("commandSession = new LibraryCommandSession"));
        commandSessionListener = commandSessionListener.substring(0, commandSessionListener.indexOf("root = new FrameLayout"));
        String onReply = commandSessionListener.substring(commandSessionListener.indexOf("@Override public void onReply"));
        onReply = onReply.substring(0, onReply.indexOf("@Override public void onConfirm"));

        assertTrue(onReply.contains("commandReply = text;"));
        assertTrue(onReply.contains("refreshHomePages();"));
        assertFalse(onReply.contains("toast("));
    }

    @Test
    public void homeCommandTalkStartsOnlyAfterLongPressConfirmation() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String touch = methodBody(source, "protected View.OnTouchListener createLibraryCommandFabTouchListener");

        assertTrue(source.contains("protected int commandLongPressConfirmDelayMs()"));
        assertTrue(source.contains("return 300;"));
        assertTrue(touch.contains("startLibraryCommandTalk();"));
        assertTrue(touch.contains("pressHandler.postDelayed(startRunnable[0], commandLongPressConfirmDelayMs())"));
        String actionDown = touch.substring(touch.indexOf("case MotionEvent.ACTION_DOWN:"),
                touch.indexOf("startRunnable[0] = () ->"));
        assertFalse(actionDown.contains("startLibraryCommandTalk();"));
        String longPressRunnable = touch.substring(touch.indexOf("startRunnable[0] = () ->"));
        longPressRunnable = longPressRunnable.substring(0, longPressRunnable.indexOf("pressHandler.postDelayed"));
        assertTrue(longPressRunnable.contains("startLibraryCommandTalk();"));
        String actionUp = touch.substring(touch.indexOf("case MotionEvent.ACTION_UP:"),
                touch.indexOf("case MotionEvent.ACTION_CANCEL:"));
        assertFalse(actionUp.contains("finishLibraryCommandTalk(true);"));
        assertFalse(touch.contains("ViewConfiguration.getLongPressTimeout()"));
    }

    @Test
    public void homeLongPressReleaseImmediatelyHidesDictationStateLikeIos() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String finish = methodBody(source, "protected void finishLibraryCommandTalk");
        String complete = methodBody(source, "protected void completeLibraryCommandTalk");

        assertTrue(finish.indexOf("commandTalking = false;") < finish.indexOf("session.finish("));
        assertTrue(finish.contains("commandReply = null;"));
        assertTrue(complete.contains("commandReply = null;"));
        assertFalse(complete.contains("commandReply = \"没有识别到指令\""));
        assertFalse(complete.contains("commandReply = \"已取消图库指令\""));
    }

    @Test
    public void homeCommandFeedbackIsAnchoredAboveRecordButtonNotList() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String listPage = methodBody(source, "protected View buildRecordingsListPage");
        String stoppedRecordingShell = methodBody(source, "protected void buildHomeShell");
        String fab = methodBody(source, "protected void addRecordFab");

        assertFalse(listPage.contains("addLibraryCommandStatus(list)"));
        assertFalse(stoppedRecordingShell.contains("addLibraryCommandStatus(list)"));
        assertTrue(fab.contains("addLibraryCommandStatus(fabCol)"));
        assertTrue(fab.indexOf("addLibraryCommandStatus(fabCol)") < fab.indexOf("fabCol.addView(fabRing"));
    }

    @Test
    public void homeCommandNumbersFloatOverIconTopLeftWhileHolding() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String row = methodBody(source, "protected View recordingRow");
        String badge = methodBody(source, "protected TextView commandNumberBadge");

        assertTrue(row.contains("FrameLayout iconWrap = new FrameLayout(this);"));
        assertTrue(row.contains("iconWrap.addView(waveIcon"));
        assertTrue(row.contains("new FrameLayout.LayoutParams(dp(24), dp(24),"));
        assertTrue(row.contains("Gravity.START | Gravity.TOP"));
        assertTrue(row.contains("iconWrap.addView(badge"));
        assertTrue(row.contains("row.addView(iconWrap"));
        assertFalse(row.contains("FrameLayout numberSlot"));
        assertFalse(row.contains("row.addView(numberSlot"));
        assertFalse(row.contains("badgeLp.setMargins(-dp("));
        assertTrue(row.contains("badge.setVisibility(commandTalking ? View.VISIBLE : View.GONE)"));
        assertTrue(badge.contains("Color.BLACK"));
        assertTrue(badge.contains("roundStroke(Color.WHITE, 12"));
        assertFalse(row.contains("if (commandTalking && !rec.uploading)"));
    }

    @Test
    public void homeCommandTipHasHorizontalBreathingRoom() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String status = methodBody(source, "protected void addLibraryCommandStatus");

        assertTrue(status.contains("lp.setMargins(dp(16), 0, dp(16), dp(12))"));
    }

    @Test
    public void homeCommandQueueShowsInstructionTextLikeIos() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String status = methodBody(source, "protected void updateFabStatus");

        assertTrue(status.contains("commandQueue.get(commandQueue.size() - 1).text"));
        assertFalse(status.contains("图库指令处理中：\" + commandQueue.size()"));
    }

    @Test
    public void homeCommandStatusShowsConnectingBeforeListening() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String start = methodBody(source, "protected void startLibraryCommandTalk");
        String status = methodBody(source, "protected void updateFabStatus");

        assertTrue(start.contains("commandReply = \"正在连接…\""));
        assertTrue(start.contains("if (commandTranscript.bestText().isEmpty()) commandReply = state;"));
        assertTrue(status.contains("String transcriptText = commandTranscript.bestText();"));
        assertTrue(status.contains("text = transcriptText.isEmpty() ? commandReply : transcriptText;"));
        assertFalse(start.contains("commandReply = \"按住说图库指令\""));
    }

    @Test
    public void homeConnectsAsrOnlyWhenPressingRecordToSavePower() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String onCreate = methodBody(source, "protected void onCreate");
        String start = methodBody(source, "protected void startLibraryCommandTalk");
        String complete = methodBody(source, "protected void completeLibraryCommandTalk");

        assertFalse(onCreate.contains("prewarmConnection"));
        assertFalse(complete.contains("prewarmConnection"));
        assertTrue(start.contains("commandDictationSession.start();"));
    }

    @Test
    public void commandTextCanArriveAfterFingerRelease() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String start = methodBody(source, "protected void startLibraryCommandTalk");

        assertTrue(start.contains("commandTranscript.accept(text, isFinal);"));
        assertTrue(start.contains("if (!commandTalking) return;"));
        assertTrue(start.indexOf("commandTranscript.accept(text, isFinal);") < start.indexOf("if (!commandTalking) return;"));
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
    public void homeSupportsDynamicTagTabsAndTaggedCommandTargets() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(source.contains("protected final List<String> homeTags = new ArrayList<>()"));
        assertTrue(source.contains("selectedTag = tag"));
        assertTrue(source.contains("buildTagTabPage(tag)"));
        assertTrue(source.contains("recordingsForTag(tag)"));
        assertTrue(source.contains("currentCommandTargets()"));
        assertTrue(source.contains("if (selectedTag == null) return recordings;"));
        assertTrue(source.contains("rec.tags != null && rec.tags.contains(selectedTag)"));
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
    public void homeRefreshIsDeferredUntilActiveRecordingStops() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String showHome = methodBody(source, "protected void showHome()");
        String stopRecording = methodBody(source, "protected void stopRecordingFlow()");

        assertTrue(source.contains("protected boolean homeRefreshDeferredWhileRecording;"));
        assertTrue(showHome.contains("if (recorder != null && recorder.isRecording())"));
        assertTrue(showHome.contains("homeRefreshDeferredWhileRecording = true;"));
        assertTrue(showHome.indexOf("homeRefreshDeferredWhileRecording = true;")
                < showHome.indexOf("root.removeAllViews();"));
        assertTrue(stopRecording.contains("boolean refreshDeferred = homeRefreshDeferredWhileRecording;"));
        assertTrue(stopRecording.indexOf("recorder.stop(null)") < stopRecording.indexOf("showHome();"));
        assertTrue(stopRecording.contains("if (refreshDeferred) refreshHomePages();"));
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
