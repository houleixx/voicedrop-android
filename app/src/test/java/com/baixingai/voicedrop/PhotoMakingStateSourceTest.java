package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class PhotoMakingStateSourceTest {
    @Test
    public void photoUsesSpinnerBeforeDelayedMakingStateOnInitialLoadAndRetry() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String render = methodBody(source, "protected void renderArticleBody");
        String load = methodBody(source, "protected void loadPhotoInto(FrameLayout frame, String relKey, boolean ignoringLocalCache)");
        String loading = methodBody(source, "protected void showPhotoLoading");
        String schedule = methodBody(source, "protected void schedulePhotoMakingState");

        assertTrue(render.contains("showPhotoLoading(photo)"));
        assertTrue(load.indexOf("showPhotoLoading(frame)") < load.indexOf("schedulePhotoMakingState(frame, startedAt)"));
        assertTrue(loading.contains("ProgressBar spinner = new ProgressBar(this)"));
        assertTrue(loading.contains("spinner.setIndeterminate(true)"));
        assertTrue(schedule.contains("PHOTO_MAKING_GRACE_MS"));
        assertTrue(schedule.contains("isPhotoLoadActive(frame, startedAt)"));
        assertTrue(schedule.contains("showPhotoMaking(frame)"));
    }

    @Test
    public void missingGeneratedPhotoShowsMakingStateAndPollsForItsResult() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("PHOTO_MAKING_GRACE_MS"));
        assertTrue(source.contains("PHOTO_POLL_INTERVAL_MS"));
        assertTrue(source.contains("PHOTO_POLL_TIMEOUT_MS"));
        assertTrue(source.contains("schedulePhotoMakingState(frame, startedAt)"));
        assertTrue(source.contains("showPhotoMaking(frame)"));
        assertTrue(source.contains("正在制作中"));
        assertTrue(source.contains("约 1 分钟完成"));
        assertTrue(source.contains("fetchPhotoInto(frame, relKey, startedAt, true)"));
        assertTrue(source.contains("library.photoImage(scope + relKey, ignoringLocalCache)"));
        assertTrue(source.contains("isPhotoLoadActive(frame, startedAt)"));
    }

    @Test
    public void generatedPhotoCanBeRetriedAfterPollingTimesOut() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("暂时无法显示"));
        assertTrue(source.contains("重试"));
        assertTrue(source.contains("loadPhotoInto(frame, relKey, true)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int next = source.indexOf("\n    protected ", start + signature.length());
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }
}
