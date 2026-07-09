package com.baixingai.voicedrop.data;

import org.json.JSONObject;
import org.junit.Test;

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
}
