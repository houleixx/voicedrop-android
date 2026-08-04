package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class PhotoMakingStateSourceTest {
    @Test
    public void ordinaryPhotoFailureDoesNotEnterMakingStateOrPoll() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String render = methodBody(source, "protected void renderArticleBody");
        String load = source;
        String loading = methodBody(source, "protected void showPhotoLoading");

        assertTrue(render.contains("showPhotoLoading(photo)"));
        assertTrue(render.contains("PhotoLoadPolicy.Intent.ORIGINAL"));
        assertTrue(load.contains("PhotoLoadPolicy.shouldPoll(intent)"));
        assertTrue(load.contains("showPhotoLoadFailed(frame, relKey, intent)"));
        assertTrue(loading.contains("ProgressBar spinner = new ProgressBar(this)"));
        assertTrue(loading.contains("spinner.setIndeterminate(true)"));
        assertTrue(loading.contains("Gravity.CENTER"));
    }

    @Test
    public void missingGeneratedPhotoShowsMakingStateAndPollsForItsResult() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("PHOTO_MAKING_GRACE_MS"));
        assertTrue(source.contains("PHOTO_POLL_INTERVAL_MS"));
        assertTrue(source.contains("PHOTO_POLL_TIMEOUT_MS"));
        assertTrue(source.contains("schedulePhotoMakingState(frame, startedAt, intent)"));
        assertTrue(source.contains("showPhotoMaking(frame)"));
        assertTrue(source.contains("正在制作中"));
        assertTrue(source.contains("约 1 分钟完成"));
        assertTrue(source.contains("fetchPhotoInto(frame, relKey, startedAt, intent, true)"));
        assertTrue(source.contains("library.photoDetailImage(scope + relKey, ignoringLocalCache)"));
        assertTrue(source.contains("isPhotoLoadActive(frame, startedAt)"));
        assertTrue(source.contains("markGeneratedPhotoKeys(currentArticleDoc, doc)"));
    }

    @Test
    public void generatedPhotoCanBeRetriedAfterPollingTimesOut() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("暂时无法显示"));
        assertTrue(source.contains("重试"));
        assertTrue(source.contains("loadPhotoInto(frame, relKey, intent, true)"));
    }

    @Test
    public void detailPhotosUseTheirOwnBoundedConcurrentExecutor() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String fetch = methodBody(source, "protected void fetchPhotoInto");
        String destroy = methodBody(source, "protected void onDestroy");

        assertTrue(source.contains("Executors.newFixedThreadPool(PhotoLoadPolicy.concurrentLoads())"));
        assertTrue(fetch.contains("photoIo.execute("));
        assertTrue(destroy.contains("photoIo.shutdownNow()"));
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
