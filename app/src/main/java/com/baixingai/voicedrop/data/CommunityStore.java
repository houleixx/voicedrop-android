package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Load the community card feed from the D1 materialized index. A missing, empty,
     * unauthorized or temporarily unavailable index falls back to the R2 source and
     * the legacy ranking endpoint, matching the iOS fail-open behavior.
     */
    public Feed feed() throws Exception {
        try {
            HttpClient.RequestOptions options = new HttpClient.RequestOptions().readTimeoutMs(5_000);
            HttpClient.Response response = http.get(Api.recoBase() + "/feed", auth.anonymousBearer(), options);
            if (response.ok()) {
                Feed parsed = parseFeed(response.text());
                if (!parsed.latest.isEmpty()) {
                    auth.storeCommunityFeedCache(response.text());
                    return parsed;
                }
            }
        } catch (Exception ignored) {
            // The display index is expendable. R2 below remains the source of truth.
        }

        List<Post> latest = list();
        Ranking ranking;
        try {
            ranking = rank(latest);
        } catch (Exception ignored) {
            ranking = Ranking.identity(latest);
        }
        return Feed.fromLegacy(latest, ranking);
    }

    /** Last successful unified feed, used as an instant cold-start snapshot while refreshing. */
    public Feed cachedFeed() {
        try {
            String json = auth.communityFeedCache();
            return json == null || json.isEmpty() ? Feed.empty() : parseFeed(json);
        } catch (Exception ignored) {
            return Feed.empty();
        }
    }

    public Ranking rank(List<Post> posts) throws Exception {
        if (posts == null || posts.isEmpty()) return new Ranking(new ArrayList<>(), new ArrayList<>(), new HashMap<>());
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
        HttpClient.Response response = http.postJson(Api.recoBase() + "/rank", auth.anonymousBearer(),
                body.toString().getBytes("UTF-8"));
        if (!response.ok()) throw new IllegalStateException("rank HTTP " + response.code);

        return parseRanking(response.text());
    }

    public static Ranking parseRanking(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        List<String> order = new ArrayList<>();
        JSONArray orderArr = root.optJSONArray("order");
        if (orderArr != null) for (int i = 0; i < orderArr.length(); i++) order.add(orderArr.optString(i));
        List<String> liked = new ArrayList<>();
        JSONArray likedArr = root.optJSONArray("liked");
        if (likedArr != null) for (int i = 0; i < likedArr.length(); i++) liked.add(likedArr.optString(i));
        Map<String, Integer> likes = new HashMap<>();
        JSONObject likesObject = root.optJSONObject("likes");
        if (likesObject != null) {
            JSONArray names = likesObject.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String id = names.optString(i);
                likes.put(id, likesObject.optInt(id, 0));
            }
        }
        return new Ranking(order, liked, likes);
    }

    public static Feed parseFeed(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray rows = root.optJSONArray("posts");
        List<Post> latest = new ArrayList<>();
        Map<String, Integer> likes = new HashMap<>();
        Map<String, Integer> replies = new HashMap<>();
        Set<String> liked = new HashSet<>();
        if (rows != null) for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            Post post = Post.from(row);
            latest.add(post);
            likes.put(post.shareId, row.optInt("likes", 0));
            if (row.optInt("replies", 0) > 0) replies.put(post.shareId, row.optInt("replies"));
            if (row.optBoolean("liked", false)) liked.add(post.shareId);
        }

        List<String> order = new ArrayList<>();
        JSONArray orderRows = root.optJSONArray("order");
        if (orderRows != null) for (int i = 0; i < orderRows.length(); i++) {
            order.add(orderRows.optString(i));
        }
        List<Post> recommended = reorder(latest, order);
        return new Feed(recommended, latest, likes, replies, liked, true);
    }

    private static List<Post> reorder(List<Post> latest, List<String> order) {
        if (order == null || order.size() != latest.size()) return new ArrayList<>(latest);
        Map<String, Post> byId = new LinkedHashMap<>();
        for (Post post : latest) byId.put(post.shareId, post);
        List<Post> result = new ArrayList<>();
        for (String id : order) {
            Post post = byId.get(id);
            if (post != null && !result.contains(post)) result.add(post);
        }
        return result.size() == latest.size() ? result : new ArrayList<>(latest);
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
            http.postJson(url, auth.anonymousBearer(), body.toString().getBytes("UTF-8"));
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
        public final String owner;
        public final String articleKey;
        public final double firstSharedAt;
        public final String title;
        public final String replyTo;
        public final ArticleDoc doc;
        public final double updatedAt;
        public final int count;
        public final boolean mine;
        public final boolean hasPhoto;
        public final String coverPhotoKey;
        public final String preview;
        public final String kind;
        public final String promptCode;
        public final List<String> appliesTo;

        Post(String shareId, String author, String owner, String articleKey, double firstSharedAt, String title, String replyTo,
             ArticleDoc doc, double updatedAt, int count, boolean mine, boolean hasPhoto,
             String coverPhotoKey, String preview, String kind, String promptCode, List<String> appliesTo) {
            this.shareId = shareId;
            this.author = author;
            this.owner = owner;
            this.articleKey = articleKey;
            this.firstSharedAt = firstSharedAt;
            this.title = title;
            this.replyTo = replyTo;
            this.doc = doc;
            this.updatedAt = updatedAt;
            this.count = count;
            this.mine = mine;
            this.hasPhoto = hasPhoto;
            this.coverPhotoKey = coverPhotoKey;
            this.preview = preview;
            this.kind = kind;
            this.promptCode = promptCode;
            this.appliesTo = Collections.unmodifiableList(new ArrayList<>(appliesTo));
        }

        // The lightweight /reco/feed omits promptCode; detail fetch supplies it.
        public boolean isPrompt() { return "prompt".equals(kind); }

        public static Post from(JSONObject obj) {
            return new Post(trim(obj.optString("shareId")),
                    trim(obj.optString("author", obj.optString("authorName"))),
                    trim(obj.optString("owner")),
                    trim(obj.optString("articleKey")), obj.optDouble("firstSharedAt", obj.optDouble("sharedAt")),
                    trim(obj.optString("title")),
                    trim(obj.optString("replyTo")),
                    docFrom(obj),
                    obj.optDouble("updatedAt", obj.optDouble("firstSharedAt", obj.optDouble("sharedAt"))),
                    obj.optInt("count", 0), obj.optBoolean("mine", false), obj.optBoolean("hasPhoto", false),
                    trim(obj.optString("coverPhotoKey")), trim(obj.optString("preview")),
                    trim(obj.optString("kind")), trim(obj.optString("promptCode")), stringList(obj.optJSONArray("appliesTo")));
        }

        private static List<String> stringList(JSONArray values) {
            List<String> result = new ArrayList<>();
            if (values != null) for (int i = 0; i < values.length(); i++) {
                String value = trim(values.optString(i));
                if (!value.isEmpty()) result.add(value);
            }
            return result;
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
        public final Map<String, Integer> likes;

        public Ranking(List<String> order, List<String> liked, Map<String, Integer> likes) {
            this.order = order;
            this.liked = liked;
            this.likes = likes == null ? Collections.emptyMap() : new HashMap<>(likes);
        }

        static Ranking identity(List<Post> posts) {
            List<String> order = new ArrayList<>();
            for (Post post : posts) order.add(post.shareId);
            return new Ranking(order, Collections.emptyList(), Collections.emptyMap());
        }
    }

    public interface PostFilter {
        boolean keep(Post post);
    }

    public static final class Feed {
        public final List<Post> recommended;
        public final List<Post> latest;
        public final Map<String, Integer> likes;
        public final Map<String, Integer> replies;
        public final Set<String> liked;
        public final boolean unified;

        Feed(List<Post> recommended, List<Post> latest, Map<String, Integer> likes,
             Map<String, Integer> replies, Set<String> liked, boolean unified) {
            this.recommended = Collections.unmodifiableList(new ArrayList<>(recommended));
            this.latest = Collections.unmodifiableList(new ArrayList<>(latest));
            this.likes = Collections.unmodifiableMap(new HashMap<>(likes));
            this.replies = Collections.unmodifiableMap(new HashMap<>(replies));
            this.liked = Collections.unmodifiableSet(new HashSet<>(liked));
            this.unified = unified;
        }

        public static Feed empty() {
            return new Feed(Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptySet(), false);
        }

        public static Feed fromLegacy(List<Post> latest, Ranking ranking) {
            Map<String, Integer> replies = new HashMap<>();
            for (Post post : latest) {
                if (post.replyTo != null && !post.replyTo.isEmpty()) {
                    replies.put(post.replyTo, replies.containsKey(post.replyTo) ? replies.get(post.replyTo) + 1 : 1);
                }
            }
            return new Feed(reorder(latest, ranking.order), latest, ranking.likes, replies,
                    new HashSet<>(ranking.liked), false);
        }

        public Feed filtered(PostFilter filter) {
            List<Post> filteredLatest = new ArrayList<>();
            List<Post> filteredRecommended = new ArrayList<>();
            for (Post post : latest) if (filter.keep(post)) filteredLatest.add(post);
            for (Post post : recommended) if (filter.keep(post)) filteredRecommended.add(post);
            return new Feed(filteredRecommended, filteredLatest, likes, replies, liked, unified);
        }

        public int likeCount(String shareId) { return likes.containsKey(shareId) ? likes.get(shareId) : 0; }
        public int replyCount(String shareId) { return replies.containsKey(shareId) ? replies.get(shareId) : 0; }

        public List<String> recommendedIds() { return ids(recommended); }
        public List<String> latestIds() { return ids(latest); }

        private static List<String> ids(List<Post> posts) {
            List<String> ids = new ArrayList<>();
            for (Post post : posts) ids.add(post.shareId);
            return ids;
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
            return hasInvalidSession()
                    || (code == 403 && ("needs_wechat_signin".equals(error)
                    || "needs_apple_signin".equals(error)));
        }

        public boolean hasInvalidSession() {
            return code == 401 && "unauthorized".equals(error);
        }

        public String failureMessage() {
            if (hasInvalidSession()) return "微信登录已失效，请重新登录";
            if (needsWechatSignin()) return "请先微信登录后再分享到社区";
            if ("article not found".equals(error)) return "社区分享失败：文章不存在，请重新生成后再试";
            if ("not shareable".equals(error)) return "社区分享失败：这篇内容无法分享";
            if ("empty article".equals(error)) return "社区分享失败：文章内容为空";
            if ("content_flagged".equals(error)) return "社区分享失败：内容未通过社区审核";
            if (error != null && !error.isEmpty()) return "社区分享失败：" + error;
            return "社区分享失败，请稍后再试";
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
