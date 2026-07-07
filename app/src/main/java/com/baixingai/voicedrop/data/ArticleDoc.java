package com.baixingai.voicedrop.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ArticleDoc {
    public final String id;
    public final String transcript;
    public final List<MinedArticle> articles;
    public final List<String> tags;
    public final List<FollowupQuestion> questions;
    public final List<String> photos;
    public final String ownerScope;

    public ArticleDoc(String id, String transcript, List<MinedArticle> articles, List<String> photos) {
        this(id, transcript, articles, null, null, photos, null);
    }

    public ArticleDoc(String id, String transcript, List<MinedArticle> articles, List<String> photos, String ownerScope) {
        this(id, transcript, articles, null, null, photos, ownerScope);
    }

    public ArticleDoc(String id, String transcript, List<MinedArticle> articles, List<String> tags,
                      List<String> photos, String ownerScope) {
        this(id, transcript, articles, tags, null, photos, ownerScope);
    }

    public ArticleDoc(String id, String transcript, List<MinedArticle> articles, List<String> tags,
                      List<FollowupQuestion> questions, List<String> photos, String ownerScope) {
        this.id = id;
        this.transcript = transcript;
        this.articles = articles;
        this.tags = tags == null ? new ArrayList<>() : tags;
        this.questions = questions == null ? new ArrayList<>() : questions;
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
        List<String> tags = new ArrayList<>();
        JSONArray ts = obj.optJSONArray("tags");
        if (ts != null) {
            for (int i = 0; i < ts.length(); i++) {
                String tag = ts.optString(i, "").trim();
                if (!tag.isEmpty()) tags.add(tag);
            }
        }
        List<FollowupQuestion> questions = new ArrayList<>();
        JSONArray qs = obj.optJSONArray("questions");
        if (qs != null) {
            for (int i = 0; i < qs.length(); i++) {
                JSONObject q = qs.optJSONObject(i);
                if (q == null) continue;
                String id = q.optString("id", "").trim();
                String text = q.optString("text", "").trim();
                if (id.isEmpty() || text.isEmpty()) continue;
                Integer articleIndex = q.has("articleIndex") && !q.isNull("articleIndex") ? q.optInt("articleIndex") : null;
                Double createdAt = q.has("createdAt") && !q.isNull("createdAt") ? q.optDouble("createdAt") : null;
                questions.add(new FollowupQuestion(id, articleIndex, text, q.optString("status", "pending"), createdAt));
            }
        }
        return new ArticleDoc(obj.optString("id", null), obj.optString("transcript", null), articles, tags, questions,
                photos, obj.optString("owner", null));
    }

    public static final class FollowupQuestion {
        public final String id;
        public final Integer articleIndex;
        public final String text;
        public final String status;
        public final Double createdAt;

        public FollowupQuestion(String id, Integer articleIndex, String text, String status, Double createdAt) {
            this.id = id == null ? "" : id;
            this.articleIndex = articleIndex;
            this.text = text == null ? "" : text;
            this.status = status == null || status.isEmpty() ? "pending" : status;
            this.createdAt = createdAt;
        }
    }
}
