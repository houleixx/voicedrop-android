package com.baixingai.voicedrop.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class WritingStyleHistoryCacheTest {
    @Test public void cacheKeyIsStableAndAccountScoped() {
        assertEquals(WritingStyleHistoryCache.cacheKeyFor("users/a/"),
                WritingStyleHistoryCache.cacheKeyFor("users/a/"));
        assertNotEquals(WritingStyleHistoryCache.cacheKeyFor("users/a/"),
                WritingStyleHistoryCache.cacheKeyFor("users/b/"));
    }

    @Test public void movingHeadDoesNotRewriteVersions() throws Exception {
        JSONObject original = history(2, 1, 2);
        JSONObject moved = WritingStyleHistoryCache.withHead(original, 1);

        assertEquals(1, moved.getInt("head"));
        assertEquals(original.getJSONArray("versions").toString(),
                moved.getJSONArray("versions").toString());
        assertEquals(2, original.getInt("head"));
    }

    @Test public void appendingVersionUpdatesHeadAndKeepsOnlyLatestTen() throws Exception {
        JSONObject original = history(10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        JSONObject updated = WritingStyleHistoryCache.withAppendedVersion(
                original, 11, "新风格", 1234L);

        JSONArray versions = updated.getJSONArray("versions");
        assertEquals(11, updated.getInt("head"));
        assertEquals(10, versions.length());
        assertEquals(2, versions.getJSONObject(0).getInt("v"));
        assertEquals(11, versions.getJSONObject(9).getInt("v"));
        assertEquals("新风格", versions.getJSONObject(9).getString("style"));
        assertEquals(1234L, versions.getJSONObject(9).getLong("savedAt"));
    }

    private static JSONObject history(int head, int... versions) throws Exception {
        JSONArray items = new JSONArray();
        for (int version : versions) {
            items.put(new JSONObject().put("v", version).put("style", "风格 " + version));
        }
        return new JSONObject().put("head", head).put("versions", items);
    }
}
