package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommunityStore {
    private final AuthStore auth;
    private final HttpClient http;

    public CommunityStore(AuthStore auth, HttpClient http) {
        this.auth = auth;
        this.http = http;
    }

    public List<Post> list() throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/community/list", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("community HTTP " + response.code);
        JSONArray arr = new JSONObject(response.text()).optJSONArray("posts");
        List<Post> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(Post.from(arr.getJSONObject(i)));
        return out;
    }

    public Ranking rank(List<Post> posts) throws Exception {
        if (posts == null || posts.isEmpty()) return new Ranking(new ArrayList<>(), new ArrayList<>());
        Map<String, Integer> replyCounts = new HashMap<>();
        for (Post post : posts) {
            if (post.replyTo != null && !post.replyTo.isEmpty()) {
                Integer count = replyCounts.get(post.replyTo);
                replyCounts.put(post.replyTo, count == null ? 1 : count + 1);
            }
        }

        JSONArray payloadPosts = new JSONArray();
        for (Post post : posts) {
            JSONObject obj = new JSONObject();
            obj.put("shareId", post.shareId);
            obj.put("firstSharedAt", post.firstSharedAt);
            obj.put("author", post.author == null ? "" : post.author);
            obj.put("replyCount", replyCounts.containsKey(post.shareId) ? replyCounts.get(post.shareId) : 0);
            payloadPosts.put(obj);
        }
        JSONObject body = new JSONObject().put("posts", payloadPosts);
        HttpClient.Response response = http.postJson(Api.recoBase() + "/rank", auth.bearer(),
                body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("rank HTTP " + response.code);

        JSONObject root = new JSONObject(response.text());
        List<String> order = new ArrayList<>();
        JSONArray orderArr = root.optJSONArray("order");
        if (orderArr != null) for (int i = 0; i < orderArr.length(); i++) order.add(orderArr.optString(i));
        List<String> liked = new ArrayList<>();
        JSONArray likedArr = root.optJSONArray("liked");
        if (likedArr != null) for (int i = 0; i < likedArr.length(); i++) liked.add(likedArr.optString(i));
        return new Ranking(order, liked);
    }

    public Post get(String shareId) throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/community/get/" + Api.path(shareId), auth.bearer());
        if (!response.ok()) return null;
        JSONObject root = new JSONObject(response.text());
        JSONObject post = root.optJSONObject("post");
        return Post.from(post == null ? root : post);
    }

    public String sharedShareId(Recording rec) throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/community/shared/" + Api.path(rec.articleKey()), auth.bearer());
        if (!response.ok()) return null;
        return new JSONObject(response.text()).optString("shareId", null);
    }

    public String share(Recording rec, String replyTo) throws Exception {
        String url = Api.filesBase() + "/community/share/" + Api.path(rec.articleKey());
        byte[] body = replyTo == null ? new byte[0] : new JSONObject().put("replyTo", replyTo).toString().getBytes("UTF-8");
        HttpClient.Response response = http.postJson(url, auth.bearer(), body);
        if (!response.ok()) return null;
        return new JSONObject(response.text()).optString("shareId", null);
    }

    public boolean unshare(String shareId) throws Exception {
        return http.postJson(Api.filesBase() + "/community/unshare/" + Api.path(shareId), auth.bearer(), new byte[0]).ok();
    }

    public boolean report(String shareId) throws Exception {
        return http.postJson(Api.filesBase() + "/community/report/" + Api.path(shareId), auth.bearer(), new byte[0]).ok();
    }

    public void engage(String shareId, String action) throws Exception {
        engage(shareId, action, null);
    }

    /**
     * Report an engagement action (view, like, finish) to the reco service.
     * Best-effort: failures are silently ignored.
     */
    public void engage(String shareId, String action, Boolean on) {
        try {
            String url = Api.recoBase() + "/engage/" + Api.path(shareId);
            JSONObject body = new JSONObject();
            body.put("action", action);
            if (on != null) body.put("on", on);
            http.postJson(url, auth.bearer(), body.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    public List<Post> replies(String shareId) throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/community/replies/" + Api.path(shareId), auth.bearer());
        JSONArray arr = response.ok() ? new JSONObject(response.text()).optJSONArray("posts") : null;
        List<Post> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(Post.from(arr.getJSONObject(i)));
        return out;
    }

    public static final class Post {
        public final String shareId;
        public final String author;
        public final String articleKey;
        public final double firstSharedAt;
        public final String title;
        public final String replyTo;

        Post(String shareId, String author, String articleKey, double firstSharedAt, String title, String replyTo) {
            this.shareId = shareId;
            this.author = author;
            this.articleKey = articleKey;
            this.firstSharedAt = firstSharedAt;
            this.title = title;
            this.replyTo = replyTo;
        }

        static Post from(JSONObject obj) {
            return new Post(trim(obj.optString("shareId")),
                    trim(obj.optString("author", obj.optString("authorName"))),
                    trim(obj.optString("articleKey")), obj.optDouble("firstSharedAt", obj.optDouble("sharedAt")),
                    trim(obj.optString("title")),
                    trim(obj.optString("replyTo")));
        }

        private static String trim(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public static final class Ranking {
        public final List<String> order;
        public final List<String> liked;

        Ranking(List<String> order, List<String> liked) {
            this.order = order;
            this.liked = liked;
        }
    }
}
