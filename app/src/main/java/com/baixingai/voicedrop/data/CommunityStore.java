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
        ShareResult result = shareResult(rec, replyTo);
        return result.ok ? result.shareId : null;
    }

    public ShareResult shareResult(Recording rec, String replyTo) throws Exception {
        String url = Api.filesBase() + "/community/share/" + Api.path(rec.articleKey());
        byte[] body = replyTo == null ? new byte[0] : new JSONObject().put("replyTo", replyTo).toString().getBytes("UTF-8");
        HttpClient.Response response = http.postJson(url, auth.communityBearer(), body);
        JSONObject json = response.text().isEmpty() ? new JSONObject() : new JSONObject(response.text());
        if (!response.ok()) return ShareResult.error(response.code, json.optString("error", ""));
        return ShareResult.ok(json.optString("shareId", null));
    }

    public boolean unshare(String shareId) throws Exception {
        return http.postJson(Api.filesBase() + "/community/unshare/" + Api.path(shareId), auth.communityBearer(), new byte[0]).ok();
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

    public Map<String, FeedState> feedStates(List<String> shareIds) throws Exception {
        if (shareIds == null || shareIds.isEmpty()) return new HashMap<>();
        JSONArray arr = new JSONArray();
        for (String id : shareIds) if (id != null && !id.isEmpty()) arr.put(id);
        JSONObject body = new JSONObject().put("share_ids", arr);
        HttpClient.Response response = http.postJson(Api.agentBase() + "/feed/state", auth.bearer(),
                body.toString().getBytes("UTF-8"));
        if (!response.ok()) return new HashMap<>();
        JSONObject states = new JSONObject(response.text()).optJSONObject("states");
        Map<String, FeedState> out = new HashMap<>();
        if (states == null) return out;
        JSONArray names = states.names();
        if (names == null) return out;
        for (int i = 0; i < names.length(); i++) {
            String id = names.optString(i);
            JSONObject s = states.optJSONObject(id);
            if (s != null) out.put(id, new FeedState(s.optInt("count", 0), s.optBoolean("fed", false)));
        }
        return out;
    }

    public FeedResult feed(String shareId) throws Exception {
        JSONObject body = new JSONObject().put("share_id", shareId);
        HttpClient.Response response = http.postJson(Api.agentBase() + "/feed", auth.communityBearer(),
                body.toString().getBytes("UTF-8"));
        JSONObject json = response.text().isEmpty() ? new JSONObject() : new JSONObject(response.text());
        return FeedResult.from(response.code, json);
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
        public final ArticleDoc doc;

        Post(String shareId, String author, String articleKey, double firstSharedAt, String title, String replyTo,
             ArticleDoc doc) {
            this.shareId = shareId;
            this.author = author;
            this.articleKey = articleKey;
            this.firstSharedAt = firstSharedAt;
            this.title = title;
            this.replyTo = replyTo;
            this.doc = doc;
        }

        static Post from(JSONObject obj) {
            return new Post(trim(obj.optString("shareId")),
                    trim(obj.optString("author", obj.optString("authorName"))),
                    trim(obj.optString("articleKey")), obj.optDouble("firstSharedAt", obj.optDouble("sharedAt")),
                    trim(obj.optString("title")),
                    trim(obj.optString("replyTo")),
                    docFrom(obj));
        }

        private static ArticleDoc docFrom(JSONObject obj) {
            if (!obj.has("articles") && !obj.has("body")) return null;
            try {
                return ArticleDoc.fromJson(obj.toString());
            } catch (Exception e) {
                return null;
            }
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

    public static final class ShareResult {
        public final boolean ok;
        public final String shareId;
        public final int code;
        public final String error;

        private ShareResult(boolean ok, String shareId, int code, String error) {
            this.ok = ok;
            this.shareId = shareId;
            this.code = code;
            this.error = error;
        }

        static ShareResult ok(String shareId) {
            return new ShareResult(shareId != null && !shareId.isEmpty(), shareId, 200, null);
        }

        static ShareResult error(int code, String error) {
            return new ShareResult(false, null, code, error == null ? "" : error);
        }

        public boolean needsWechatSignin() {
            return code == 403 && "needs_wechat_signin".equals(error);
        }
    }

    public static final class FeedState {
        public final int count;
        public final boolean fed;

        public FeedState(int count, boolean fed) {
            this.count = count;
            this.fed = fed;
        }
    }

    public static final class FeedResult {
        public final boolean ok;
        public final boolean already;
        public final String error;
        public final double authorSuanli;
        public final double feederSuanli;

        private FeedResult(boolean ok, boolean already, String error, double authorSuanli, double feederSuanli) {
            this.ok = ok;
            this.already = already;
            this.error = error == null ? "" : error;
            this.authorSuanli = authorSuanli;
            this.feederSuanli = feederSuanli;
        }

        static FeedResult from(int code, JSONObject json) {
            JSONObject suanli = json.optJSONObject("suanli");
            return new FeedResult(json.optBoolean("ok", code >= 200 && code < 300),
                    json.optBoolean("already", false),
                    json.optString("error", ""),
                    suanli == null ? 0 : suanli.optDouble("author", 0),
                    suanli == null ? 0 : suanli.optDouble("feeder", 0));
        }

        public boolean needsWechatSignin() {
            return "needs_wechat_signin".equals(error) || "needs_apple_signin".equals(error);
        }
    }
}
