package com.baixingai.voicedrop.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class LibraryStoreArticleSaveTest {
    @Test
    public void mergeArticlesJsonPreservesUnknownDocumentFields() throws Exception {
        Method merge = LibraryStore.class.getMethod("mergeArticlesJson", String.class, java.util.List.class);
        String raw = "{\"id\":\"r1\",\"future\":{\"keep\":true},\"questions\":[{\"id\":\"q1\"}],"
                + "\"articles\":[{\"title\":\"旧\",\"body\":\"旧正文\"}]}";

        String merged = (String) merge.invoke(null, raw, Arrays.asList(
                new MinedArticle("新标题", "新正文", 4, "wx-1")));

        JSONObject object = new JSONObject(merged);
        assertEquals(true, object.getJSONObject("future").getBoolean("keep"));
        assertEquals("q1", object.getJSONArray("questions").getJSONObject(0).getString("id"));
        JSONArray articles = object.getJSONArray("articles");
        assertEquals("新标题", articles.getJSONObject(0).getString("title"));
        assertEquals("新正文", articles.getJSONObject(0).getString("body"));
        assertEquals(4, articles.getJSONObject(0).getInt("style"));
        assertEquals("wx-1", articles.getJSONObject(0).getString("wechatMediaId"));
    }
}
