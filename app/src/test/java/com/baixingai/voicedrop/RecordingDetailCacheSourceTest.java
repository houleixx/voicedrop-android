package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class RecordingDetailCacheSourceTest {
    @Test
    public void detailRendersSnapshotBeforeNetworkAndEditPushRefreshesIt() throws Exception {
        Path path = sourcePath("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        int cached = source.indexOf("ArticleDoc cached = library.cachedDoc(rec)");
        int network = source.indexOf("runIoIfActive(() ->", cached);

        assertTrue(cached >= 0 && network > cached);
        assertTrue(source.contains("showArticle(rec, cached, false, false)"));
        assertTrue(source.contains("library.cacheDoc(rec, doc)"));
    }

    @Test
    public void equivalentHttpAndWebSocketSnapshotsDoNotRebuildLoadedPhotos() throws Exception {
        Path path = sourcePath("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        assertTrue(source.contains("ArticleRenderPolicy.shouldRebuild(currentArticleDoc, doc)"));
        assertTrue(source.contains("if (sameEditQueue(editQueue, queue)) return;"));
    }

    @Test
    public void rebuiltArticleIsFullyPreparedBeforeReplacingTheVisiblePage() throws Exception {
        Path path = sourcePath("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        int showArticle = source.indexOf("protected void showArticle(Recording rec, ArticleDoc doc, boolean animateOpen, boolean refreshHistory)");
        int render = source.indexOf("renderCurrentArticle(content, rec, doc)", showArticle);
        int attach = source.indexOf("attachPage(articleFrame, animateOpen)", showArticle);

        assertTrue(render > showArticle);
        assertTrue(attach > render);
    }

    private static Path sourcePath(String moduleRelative) {
        Path path = Paths.get(moduleRelative);
        return Files.exists(path) ? path : Paths.get("app", moduleRelative);
    }
}
