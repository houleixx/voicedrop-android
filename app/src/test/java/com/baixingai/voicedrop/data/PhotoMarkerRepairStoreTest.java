package com.baixingai.voicedrop.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhotoMarkerRepairStoreTest {
    @Test public void appendsOnlyMissingMarkersToLastArticleAndPreservesMetadata() {
        String firstKey = "photos/2026-07-27-112012/2-abc.jpg";
        String missingKey = "photos/2026-07-27-112012/5-def.jpg";
        List<MinedArticle> articles = Arrays.asList(
                new MinedArticle("第一篇", "正文\n\n[[photo:" + firstKey + "]]", 2, "wx-one"),
                new MinedArticle("第二篇", "结尾", 3, "wx-two"));

        PhotoMarkerRepairStore.RepairResult result = PhotoMarkerRepairStore.ensurePhotoMarkers(
                articles, Arrays.asList(firstKey, missingKey, missingKey));

        assertTrue(result.changed);
        assertEquals(articles.get(0).body, result.articles.get(0).body);
        assertEquals("结尾\n\n[[photo:" + missingKey + "]]", result.articles.get(1).body);
        assertEquals(Integer.valueOf(3), result.articles.get(1).style);
        assertEquals("wx-two", result.articles.get(1).wechatMediaId);
    }

    @Test public void leavesCompleteArticlesUntouched() {
        String key = "photos/2026-07-27-112012/2-abc.jpg";
        List<MinedArticle> articles = Collections.singletonList(
                new MinedArticle("现场", "正文\n\n[[photo:" + key + "]]", 1, null));

        PhotoMarkerRepairStore.RepairResult result =
                PhotoMarkerRepairStore.ensurePhotoMarkers(articles, Collections.singletonList(key));

        assertFalse(result.changed);
        assertEquals(articles.get(0).body, result.articles.get(0).body);
    }

    @Test public void ignoresInvalidPhotoKeys() {
        List<MinedArticle> articles = Collections.singletonList(
                new MinedArticle("现场", "正文", 1, null));

        PhotoMarkerRepairStore.RepairResult result = PhotoMarkerRepairStore.ensurePhotoMarkers(
                articles, Arrays.asList("", "../secret", "photo:not-a-key"));

        assertFalse(result.changed);
    }
}
