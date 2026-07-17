package com.baixingai.voicedrop.data;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class CommunityStoreTest {
    @Test
    public void postFromCommunityDetailKeepsInlineArticles() throws Exception {
        JSONObject json = new JSONObject("{"
                + "\"shareId\":\"share-1\","
                + "\"title\":\"社区标题\","
                + "\"owner\":\"users/anon-author/\","
                + "\"articles\":[{\"title\":\"正文标题\",\"body\":\"正文内容\"}]"
                + "}");

        CommunityStore.Post post = CommunityStore.Post.from(json);

        assertNotNull(post.doc);
        assertEquals(1, post.doc.articles.size());
        assertEquals("users/anon-author/", post.doc.ownerScope);
        assertEquals("正文标题", post.doc.articles.get(0).title);
        assertEquals("正文内容", post.doc.articles.get(0).body);
    }

    @Test
    public void unauthorizedShareRequiresWechatReauthentication() {
        CommunityStore.ShareResult result = CommunityStore.ShareResult.error(401, "unauthorized");

        assertTrue(result.needsWechatSignin());
        assertTrue(result.hasInvalidSession());
        assertEquals("微信登录已失效，请重新登录", result.failureMessage());
    }

    @Test
    public void shareFailureReportsTheActualBackendError() {
        CommunityStore.ShareResult result = CommunityStore.ShareResult.error(404, "article not found");

        assertFalse(result.needsWechatSignin());
        assertFalse(result.hasInvalidSession());
        assertEquals("社区分享失败：文章不存在，请重新生成后再试", result.failureMessage());
    }

    @Test
    public void feedResponseCarriesCardMetadataAndInteractionState() throws Exception {
        String json = "{\"posts\":["
                + "{\"shareId\":\"new\",\"title\":\"新帖\",\"preview\":\"预览\","
                + "\"coverPhotoKey\":\"users/u/photos/a.jpg\",\"hasPhoto\":true,"
                + "\"firstSharedAt\":200,\"likes\":7,\"replies\":2,\"liked\":true},"
                + "{\"shareId\":\"old\",\"title\":\"旧帖\",\"firstSharedAt\":100}"
                + "],\"order\":[\"old\",\"new\"]}";

        CommunityStore.Feed feed = CommunityStore.parseFeed(json);

        assertEquals(Arrays.asList("old", "new"), feed.recommendedIds());
        assertEquals(Arrays.asList("new", "old"), feed.latestIds());
        assertEquals("预览", feed.latest.get(0).preview);
        assertEquals("users/u/photos/a.jpg", feed.latest.get(0).coverPhotoKey);
        assertTrue(feed.latest.get(0).hasPhoto);
        assertEquals(7, feed.likeCount("new"));
        assertEquals(2, feed.replyCount("new"));
        assertTrue(feed.liked.contains("new"));
    }

    @Test
    public void incompleteFeedOrderFallsBackToLatestOrder() throws Exception {
        CommunityStore.Feed feed = CommunityStore.parseFeed("{\"posts\":["
                + "{\"shareId\":\"a\",\"firstSharedAt\":2},"
                + "{\"shareId\":\"b\",\"firstSharedAt\":1}],\"order\":[\"a\"]}");

        assertEquals(Arrays.asList("a", "b"), feed.recommendedIds());
    }

    @Test
    public void promptPostKeepsCollectibleMetadata() throws Exception {
        CommunityStore.Post post = CommunityStore.Post.from(new JSONObject(
                "{\"shareId\":\"prompt\",\"kind\":\"prompt\",\"promptCode\":\"1234567\",\"appliesTo\":[\"text\",\"image\"]}"));

        assertTrue(post.isPrompt());
        assertEquals("1234567", post.promptCode);
        assertEquals(Arrays.asList("text", "image"), post.appliesTo);
    }

    @Test
    public void legacyFeedKeepsLatestOrderAndRanksWhenCoverageIsComplete() throws Exception {
        CommunityStore.Post a = CommunityStore.Post.from(new JSONObject(
                "{\"shareId\":\"a\",\"firstSharedAt\":2,\"replyTo\":\"root\"}"));
        CommunityStore.Post root = CommunityStore.Post.from(new JSONObject(
                "{\"shareId\":\"root\",\"firstSharedAt\":1}"));
        CommunityStore.Ranking ranking = CommunityStore.parseRanking(
                "{\"order\":[\"root\",\"a\"],\"liked\":[\"root\"],\"likes\":{\"root\":3}}");

        CommunityStore.Feed feed = CommunityStore.Feed.fromLegacy(Arrays.asList(a, root), ranking);

        assertEquals(Arrays.asList("root", "a"), feed.recommendedIds());
        assertEquals(Arrays.asList("a", "root"), feed.latestIds());
        assertEquals(1, feed.replyCount("root"));
        assertEquals(3, feed.likeCount("root"));
    }
}
