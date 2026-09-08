package com.baixingai.voicedrop.core;

import com.baixingai.voicedrop.data.ArticleDoc;
import com.baixingai.voicedrop.data.MinedArticle;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArticleRenderPolicyTest {
    @Test
    public void identicalCachedAndRemoteSnapshotsDoNotRebuildTheDetailPage() {
        ArticleDoc cached = doc("正文\n[[photo:photos/session/1-a.jpg]]",
                "photos/session/1-a.jpg");
        ArticleDoc remote = doc("正文\n[[photo:photos/session/1-a.jpg]]",
                "photos/session/1-a.jpg");

        assertFalse(ArticleRenderPolicy.shouldRebuild(cached, remote));
    }

    @Test
    public void changedPhotoContentRebuildsTheDetailPage() {
        ArticleDoc previous = doc("正文\n[[photo:photos/session/1-a.jpg]]",
                "photos/session/1-a.jpg");
        ArticleDoc updated = doc("正文\n[[photo:photos/session/2-b.jpg]]",
                "photos/session/2-b.jpg");

        assertTrue(ArticleRenderPolicy.shouldRebuild(previous, updated));
    }

    @Test
    public void receivedImageUpdateIsComparedToWhatWasActuallyRendered() {
        ArticleRenderPolicy.RenderedState rendered = new ArticleRenderPolicy.RenderedState();
        ArticleDoc original = doc("正文", "photos/old.jpg");
        rendered.didRender(original, 0);
        // Mirrors onUpdated/onResolved assigning currentArticleDoc before repainting.
        ArticleDoc current = doc("[[photo:photos/new.jpg]]\n正文", "photos/new.jpg");
        assertTrue(rendered.needsRender(current, 0));
        rendered.didRender(current, 0);
        assertFalse(rendered.needsRender(current, 0));
        assertFalse(rendered.needsRender(doc("[[photo:photos/new.jpg]]\n正文", "photos/new.jpg"), 0));
    }

    @Test
    public void textGenerationStillRepaintsAndQueueUpdatesDoNotRebuildPhotos() {
        ArticleRenderPolicy.RenderedState rendered = new ArticleRenderPolicy.RenderedState();
        ArticleDoc original = doc("正文", "photos/old.jpg");
        rendered.didRender(original, 0);
        assertFalse(rendered.needsRender(original, 0));
        assertTrue(rendered.needsRender(doc("改写后的正文", "photos/old.jpg"), 0));
    }

    @Test
    public void switchingArticlesAndReopeningAlwaysPaintTheCorrectContent() {
        ArticleRenderPolicy.RenderedState rendered = new ArticleRenderPolicy.RenderedState();
        ArticleDoc doc = doc("正文", "photos/old.jpg");
        rendered.didRender(doc, 0);
        assertTrue(rendered.needsRender(doc, 1));
        rendered.clear();
        assertTrue(rendered.needsRender(doc, 0));
    }

    private static ArticleDoc doc(String body, String photo) {
        return new ArticleDoc("article-1", "transcript",
                Collections.singletonList(new MinedArticle("标题", body, 2, null)),
                Arrays.asList("标签"),
                Collections.singletonList(photo),
                "users/test/");
    }
}
