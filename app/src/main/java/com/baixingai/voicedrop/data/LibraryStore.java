package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LibraryStore {
    private final AuthStore auth;
    private final HttpClient http;
    private final Map<String, String> titleCache = new HashMap<>();
    private String cachedScope;

    public LibraryStore(AuthStore auth, HttpClient http) {
        this.auth = auth;
        this.http = http;
    }

    public List<Recording> load(List<String> localUploading) throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/list", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("加载失败 HTTP " + response.code);
        JSONObject root = new JSONObject(response.text());
        JSONArray files = root.optJSONArray("files");
        Set<String> names = new HashSet<>();
        List<Item> items = new ArrayList<>();
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject item = files.getJSONObject(i);
                String name = item.optString("name");
                names.add(name);
                items.add(new Item(name, item.optString("uploaded", "")));
            }
        }

        List<Recording> recordings = new ArrayList<>();
        for (String local : localUploading) {
            Recording r = new Recording(local, "", false, false);
            r.uploading = true;
            recordings.add(r);
        }
        for (Item item : items) {
            String last = item.name.contains("/") ? item.name.substring(item.name.lastIndexOf('/') + 1) : item.name;
            if (!RecordingName.isRecordingFile(last)) continue;
            String stem = item.name.substring(0, item.name.length() - 4);
            Recording r = new Recording(
                    item.name,
                    item.uploaded,
                    names.contains(Recording.articleKey(stem)),
                    names.contains(Recording.emptyKey(stem)));
            r.articleTitle = titleCache.get(r.articleKey());
            recordings.add(r);
        }
        Collections.sort(recordings, (a, b) -> {
            if (a.uploaded.isEmpty() != b.uploaded.isEmpty()) return a.uploaded.isEmpty() ? -1 : 1;
            int byUpload = b.uploaded.compareTo(a.uploaded);
            return byUpload != 0 ? byUpload : b.audioName.compareTo(a.audioName);
        });
        for (Recording r : recordings) {
            if (r.hasArticles && r.articleTitle == null) {
                ArticleDoc doc = fetchDoc(r);
                if (doc != null && !doc.articles.isEmpty()) {
                    r.articleTitle = doc.articles.get(0).title;
                    titleCache.put(r.articleKey(), r.articleTitle);
                }
            }
        }
        return recordings;
    }

    public ArticleDoc fetchDoc(Recording rec) {
        if (!rec.hasArticles) return null;
        try {
            HttpClient.Response response = http.get(Api.filesBase() + "/articles/" + Api.path(rec.stem()), auth.bearer());
            return response.ok() ? ArticleDoc.fromJson(response.text()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public ArticleDoc fetchDocByArticleKey(String articleKey) {
        if (articleKey == null || articleKey.isEmpty()) return null;
        String stem = articleKey;
        if (stem.endsWith(".json")) stem = stem.substring(0, stem.length() - 5);
        try {
            HttpClient.Response response = http.get(Api.filesBase() + "/articles/" + Api.path(stem), auth.bearer());
            return response.ok() ? ArticleDoc.fromJson(response.text()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean delete(Recording rec) {
        return deleteKey(rec.audioName)
                && deleteKey(rec.articleKey())
                && deleteKey(rec.srtKey())
                && deleteKey(rec.emptyKey());
    }

    public String shareUrl(Recording rec, int section) throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/share/" + Api.path(rec.articleKey()), auth.bearer());
        if (!response.ok()) return null;
        String url = new JSONObject(response.text()).optString("url", "");
        return url.isEmpty() ? null : url + "?s=" + section;
    }

    public boolean publishWechat(Recording rec) throws Exception {
        HttpClient.Response response = http.postJson(Api.filesBase() + "/wechat/" + Api.path(rec.articleKey()), auth.bearer(), new byte[0]);
        return response.ok();
    }

    public boolean deleteArticle(Recording rec) {
        boolean ok = deleteKey(rec.articleKey());
        deleteKey(rec.srtKey());
        deleteKey(rec.emptyKey());
        return ok;
    }

    public boolean restyle(Recording rec, int styleVersion) throws Exception {
        JSONObject body = new JSONObject()
                .put("stem", rec.stem())
                .put("styleV", styleVersion);
        HttpClient.Response response = http.postJson(
                Api.agentBase() + "/restyle",
                auth.bearer(),
                body.toString().getBytes("UTF-8"));
        return response.ok() && new JSONObject(response.text()).optBoolean("ok", true);
    }

    public JSONObject versionHistory(Recording rec) throws Exception {
        HttpClient.Response response = http.get(
                Api.filesBase() + "/articles/" + Api.path(rec.stem()) + "/history",
                auth.bearer());
        return response.ok() ? new JSONObject(response.text()) : new JSONObject().put("versions", new JSONArray()).put("head", 0);
    }

    public boolean patchHead(Recording rec, int head) throws Exception {
        JSONObject body = new JSONObject().put("head", head);
        HttpClient.Response response = http.patchJson(
                Api.filesBase() + "/articles/" + Api.path(rec.stem()) + "/head",
                auth.bearer(),
                body.toString().getBytes("UTF-8"));
        return response.ok();
    }

    public String ownerScope() throws Exception {
        if (cachedScope != null && !cachedScope.isEmpty()) return cachedScope;
        HttpClient.Response response = http.get(Api.filesBase() + "/whoami", auth.bearer());
        if (!response.ok()) return null;
        cachedScope = new JSONObject(response.text()).optString("scope", null);
        return cachedScope;
    }

    public byte[] photoData(String fullKey) throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/photo/" + Api.path(fullKey), "");
        return response.ok() ? response.body : null;
    }

    public byte[] download(String key) throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/download/" + Api.path(key), auth.bearer());
        return response.ok() ? response.body : null;
    }

    private boolean deleteKey(String key) {
        try {
            HttpClient.Response response = http.delete(Api.filesBase() + "/file/" + Api.path(key), auth.bearer());
            return response.ok() || response.code == 404;
        } catch (Exception e) {
            return false;
        }
    }

    private static final class Item {
        final String name;
        final String uploaded;
        Item(String name, String uploaded) {
            this.name = name;
            this.uploaded = uploaded == null ? "" : uploaded;
        }
    }
}
