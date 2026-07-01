package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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

    public Post get(String shareId) throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/community/get/" + Api.path(shareId), auth.bearer());
        return response.ok() ? Post.from(new JSONObject(response.text())) : null;
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

        Post(String shareId, String author, String articleKey, double firstSharedAt, String title) {
            this.shareId = shareId;
            this.author = author;
            this.articleKey = articleKey;
            this.firstSharedAt = firstSharedAt;
            this.title = title;
        }

        static Post from(JSONObject obj) {
            return new Post(obj.optString("shareId"), obj.optString("author"),
                    obj.optString("articleKey"), obj.optDouble("firstSharedAt"),
                    obj.optString("title"));
        }
    }
}
