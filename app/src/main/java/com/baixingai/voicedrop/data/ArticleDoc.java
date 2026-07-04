package com.baixingai.voicedrop.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ArticleDoc {
    public final String id;
    public final String transcript;
    public final List<MinedArticle> articles;
    public final List<String> photos;
    public final String ownerScope;

    public ArticleDoc(String id, String transcript, List<MinedArticle> articles, List<String> photos) {
        this(id, transcript, articles, photos, null);
    }

    public ArticleDoc(String id, String transcript, List<MinedArticle> articles, List<String> photos, String ownerScope) {
        this.id = id;
        this.transcript = transcript;
        this.articles = articles;
        this.photos = photos;
        this.ownerScope = ownerScope;
    }

    public static ArticleDoc fromJson(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        List<MinedArticle> articles = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("articles");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject a = arr.getJSONObject(i);
                Integer style = a.has("style") && !a.isNull("style") ? a.optInt("style") : null;
                articles.add(new MinedArticle(a.optString("title"), a.optString("body"), style, a.optString("wechatMediaId", null)));
            }
        } else if (obj.has("body")) {
            articles.add(new MinedArticle(obj.optString("title", "(无题)"), obj.optString("body"), null));
        }
        List<String> photos = new ArrayList<>();
        JSONArray ps = obj.optJSONArray("photos");
        if (ps != null) for (int i = 0; i < ps.length(); i++) photos.add(ps.optString(i));
        return new ArticleDoc(obj.optString("id", null), obj.optString("transcript", null), articles, photos,
                obj.optString("owner", null));
    }
}
