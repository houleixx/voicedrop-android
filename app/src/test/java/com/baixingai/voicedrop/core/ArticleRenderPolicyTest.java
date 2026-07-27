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

    private static ArticleDoc doc(String body, String photo) {
        return new ArticleDoc("article-1", "transcript",
                Collections.singletonList(new MinedArticle("标题", body, 2, null)),
                Arrays.asList("标签"),
                Collections.singletonList(photo),
                "users/test/");
    }
}
