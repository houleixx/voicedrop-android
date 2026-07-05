package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class RecordingsActivitySourceTest {
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
    public void recordingRowsRenderTagsInMetaLine() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");

        assertTrue(source.contains("formatTags(rec.tags)"));
        assertTrue(source.contains("metaParts.add(formatTags(rec.tags))"));
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

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
