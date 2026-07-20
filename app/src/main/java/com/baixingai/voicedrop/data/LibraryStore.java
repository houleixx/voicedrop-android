package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class LibraryStore {
    private static final ExecutorService META_IO = Executors.newFixedThreadPool(5);
    private final AuthStore auth;
    private final HttpClient http;
    private final Map<String, String> titleCache = new HashMap<>();
    private final Map<String, List<String>> tagsCache = new HashMap<>();
    private String metadataBearer = "";
    private String cachedScope;
    private String cachedScopeToken;

    public LibraryStore(AuthStore auth, HttpClient http) {
        this.auth = auth;
        this.http = http;
        ensureMetadataCache();
    }

    public List<Recording> load(List<String> localUploading) throws Exception {
        return load(localUploading, new HashMap<String, List<String>>());
    }

    public List<Recording> load(List<String> localUploading, Map<String, List<String>> pendingTagsByName) throws Exception {
        ensureMetadataCache();
        List<Item> items = fetchRecordingItems();

        List<Recording> recordings = new ArrayList<>();
        for (String local : localUploading) {
            Recording r = new Recording(local, "", false, false);
            r.uploading = true;
            r.tags = pendingTagsByName == null ? null : pendingTagsByName.get(local);
            recordings.add(r);
        }
        for (Item item : items) {
            String last = item.name.contains("/") ? item.name.substring(item.name.lastIndexOf('/') + 1) : item.name;
            if (!RecordingName.isRecordingFile(last)) continue;
            String stem = item.name.substring(0, item.name.length() - 4);
            Recording r = new Recording(
                    item.name,
                    item.uploaded,
                    item.hasArticles,
                    item.isEmpty);
            synchronized (this) {
                r.articleTitle = titleCache.get(r.articleKey());
                r.tags = tagsCache.containsKey(r.articleKey()) ? tagsCache.get(r.articleKey()) : null;
            }
            r.hasTagsSidecar = item.hasTags;
            if ((r.tags == null || r.tags.isEmpty()) && pendingTagsByName != null) r.tags = pendingTagsByName.get(last);
            recordings.add(r);
        }
        Collections.sort(recordings, (a, b) -> {
            if (a.uploaded.isEmpty() != b.uploaded.isEmpty()) return a.uploaded.isEmpty() ? -1 : 1;
            int byUpload = b.uploaded.compareTo(a.uploaded);
            return byUpload != 0 ? byUpload : b.audioName.compareTo(a.audioName);
        });
        return recordings;
    }

    /** Fetches titles/tags after the lightweight rows are already visible. */
    public boolean enrichMissingMetadata(List<Recording> recordings) {
        ensureMetadataCache();
        List<Callable<MetadataResult>> tasks = new ArrayList<>();
        for (Recording r : recordings == null ? Collections.<Recording>emptyList() : recordings) {
            boolean articleMetaMissing;
            synchronized (this) {
                articleMetaMissing = r.hasArticles
                        && (!titleCache.containsKey(r.articleKey()) || !tagsCache.containsKey(r.articleKey()));
            }
            if (articleMetaMissing) {
                tasks.add(() -> new MetadataResult(r, fetchDoc(r), null));
            } else if (!r.hasArticles && r.hasTagsSidecar && (r.tags == null || r.tags.isEmpty())) {
                tasks.add(() -> new MetadataResult(r, null, fetchTagsSidecar(r.stem())));
            }
        }
        if (tasks.isEmpty()) return false;
        boolean changed = false;
        try {
            for (Future<MetadataResult> future : META_IO.invokeAll(tasks)) {
                MetadataResult result = future.get();
                if (result.doc != null) {
                    String title = result.doc.articles.isEmpty() ? "" : result.doc.articles.get(0).title;
                    synchronized (this) {
                        result.recording.articleTitle = title;
                        result.recording.tags = new ArrayList<>(result.doc.tags);
                        titleCache.put(result.recording.articleKey(), title == null ? "" : title);
                        tagsCache.put(result.recording.articleKey(), new ArrayList<>(result.doc.tags));
                    }
                    changed = true;
                } else if (result.sidecarTags != null && !result.sidecarTags.isEmpty()) {
                    result.recording.tags = result.sidecarTags;
                    changed = true;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
        if (changed) persistMetadataCache();
        return changed;
    }

    private synchronized void ensureMetadataCache() {
        String bearer = auth.bearer();
        if (bearer.equals(metadataBearer)) return;
        metadataBearer = bearer;
        titleCache.clear();
        tagsCache.clear();
        try {
            JSONObject root = new JSONObject(auth.libraryMetadataCache());
            JSONObject titles = root.optJSONObject("titles");
            if (titles != null) {
                java.util.Iterator<String> keys = titles.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    titleCache.put(key, titles.optString(key, ""));
                }
            }
            JSONObject tags = root.optJSONObject("tags");
            if (tags != null) {
                java.util.Iterator<String> keys = tags.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONArray arr = tags.optJSONArray(key);
                    List<String> values = new ArrayList<>();
                    if (arr != null) for (int i = 0; i < arr.length(); i++) values.add(arr.optString(i));
                    tagsCache.put(key, values);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void persistMetadataCache() {
        try {
            JSONObject titles = new JSONObject();
            for (Map.Entry<String, String> entry : titleCache.entrySet()) titles.put(entry.getKey(), entry.getValue());
            JSONObject tags = new JSONObject();
            for (Map.Entry<String, List<String>> entry : tagsCache.entrySet()) tags.put(entry.getKey(), new JSONArray(entry.getValue()));
            auth.storeLibraryMetadataCache(new JSONObject().put("titles", titles).put("tags", tags).toString());
        } catch (Exception ignored) {
        }
    }

    private List<Item> fetchRecordingItems() throws Exception {
        try {
            HttpClient.Response response = http.get(Api.filesBase() + "/recordings", auth.bearer());
            if (response.ok()) {
                JSONArray rows = new JSONObject(response.text()).optJSONArray("recordings");
                if (rows != null) {
                    List<Item> indexed = new ArrayList<>();
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        indexed.add(new Item(
                                row.optString("name"),
                                row.optString("uploaded", ""),
                                row.optBoolean("hasArticles"),
                                row.optBoolean("isEmpty"),
                                row.optBoolean("hasTags")));
                    }
                    return indexed;
                }
            }
        } catch (Exception ignored) {
            // Older or temporarily unavailable servers fall back to the full R2 listing.
        }

        HttpClient.Response response = http.get(Api.filesBase() + "/list", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("加载失败 HTTP " + response.code);
        JSONArray files = new JSONObject(response.text()).optJSONArray("files");
        Set<String> names = new HashSet<>();
        List<JSONObject> rows = new ArrayList<>();
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject row = files.getJSONObject(i);
                names.add(row.optString("name"));
                rows.add(row);
            }
        }
        List<Item> legacy = new ArrayList<>();
        for (JSONObject row : rows) {
            String name = row.optString("name");
            String stem = name.endsWith(".m4a") ? name.substring(0, name.length() - 4) : name;
            legacy.add(new Item(
                    name,
                    row.optString("uploaded", ""),
                    names.contains(Recording.articleKey(stem)),
                    names.contains(Recording.emptyKey(stem)),
                    names.contains(Recording.tagsKey(stem))));
        }
        return legacy;
    }

    public synchronized void invalidateArticleCaches(List<String> stems) {
        ensureMetadataCache();
        if (stems == null) return;
        for (String stem : stems) {
            if (stem == null || stem.isEmpty()) continue;
            String key = Recording.articleKey(stem);
            titleCache.remove(key);
            tagsCache.remove(key);
        }
        persistMetadataCache();
    }

    private List<String> fetchTagsSidecar(String stem) {
        try {
            HttpClient.Response response = http.get(Api.filesBase() + "/download/" + Api.path(Recording.tagsKey(stem)), auth.bearer());
            if (!response.ok()) return null;
            JSONArray arr = new JSONArray(response.text());
            List<String> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String tag = arr.optString(i, "").trim();
                if (!tag.isEmpty()) out.add(tag);
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
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

    /** Saves exact article text without involving the AI edit pipeline. */
    public boolean saveArticles(Recording rec, List<MinedArticle> articles) {
        if (rec == null || !rec.hasArticles || articles == null) return false;
        try {
            String url = Api.filesBase() + "/articles/" + Api.path(rec.stem());
            HttpClient.Response current = http.get(url, auth.bearer());
            if (!current.ok()) return false;
            String merged = mergeArticlesJson(current.text(), articles);
            HttpClient.Response saved = http.putBytes(
                    url, auth.bearer(), "application/json", merged.getBytes("UTF-8"));
            return saved.ok();
        } catch (Exception e) {
            return false;
        }
    }

    public static String mergeArticlesJson(String rawJson, List<MinedArticle> articles) throws Exception {
        JSONObject root = new JSONObject(rawJson);
        JSONArray encoded = new JSONArray();
        for (MinedArticle article : articles) {
            JSONObject item = new JSONObject()
                    .put("title", article.title)
                    .put("body", article.body);
            if (article.style != null) item.put("style", article.style);
            if (article.wechatMediaId != null) item.put("wechatMediaId", article.wechatMediaId);
            encoded.put(item);
        }
        root.put("articles", encoded);
        return root.toString();
    }

    public boolean delete(Recording rec) {
        boolean audioDeleted = deleteKey(rec.audioName);
        boolean articleDeleted = deleteKey(rec.articleKey());
        boolean srtDeleted = deleteKey(rec.srtKey());
        boolean emptyDeleted = deleteKey(rec.emptyKey());
        deleteKey(rec.tagsKey());
        return recordingDeleteSucceeded(audioDeleted, articleDeleted, srtDeleted, emptyDeleted);
    }

    public static boolean recordingDeleteSucceeded(boolean audioDeleted, boolean articleDeleted, boolean srtDeleted, boolean emptyDeleted) {
        return audioDeleted;
    }

    public String shareUrl(Recording rec, int section) throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/share/" + Api.path(rec.articleKey()), auth.bearer());
        if (!response.ok()) return null;
        String url = new JSONObject(response.text()).optString("url", "");
        return url.isEmpty() ? null : url + "?s=" + section;
    }

    public LinkTarget resolveShareLink(String id) throws Exception {
        if (id == null || id.trim().isEmpty()) return null;
        HttpClient.Response response = http.get(Api.filesBase() + "/link/" + Api.path(id), auth.bearer());
        return response.ok() ? LinkTarget.fromJson(response.text()) : null;
    }

    public PublishResult publishWechat(Recording rec) throws Exception {
        HttpClient.Response response = http.postJson(Api.filesBase() + "/wechat/" + Api.path(rec.articleKey()), auth.bearer(), new byte[0]);
        if (response.code == 409) return PublishResult.notConfigured();
        if (response.ok()) return PublishResult.ok(response.text());
        JSONObject error = parseObject(response.text());
        return PublishResult.failed(
                wechatMessage(error.has("errcode") ? error.optInt("errcode") : null, error.optString("errmsg", null)),
                error.has("errcode") ? error.optInt("errcode") : null);
    }

    public static String wechatMessage(Integer errcode, String errmsg) {
        if (errcode == null && (errmsg == null || errmsg.isEmpty())) return null;
        if (errcode != null) {
            switch (errcode) {
                case 45004:
                    return "摘要太短，正文写长一点再发";
                case 40007:
                    return "草稿已失效，已重建一份";
                case 45009:
                case 45011:
                case 45110:
                    return "今天发布次数到上限了，明天再试";
                case 40164:
                case 40125:
                case 40013:
                    return "公众号配置有误，检查 AppID/Secret 或 IP 白名单";
                default:
                    break;
            }
        }
        return errmsg == null || errmsg.isEmpty() ? "发布失败" : "发布失败：" + errmsg;
    }

    private static JSONObject parseObject(String text) {
        try {
            return text == null || text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static final class PublishResult {
        public final boolean ok;
        public final boolean notConfigured;
        public final int created;
        public final int updated;
        public final Integer errcode;
        public final String message;

        private PublishResult(boolean ok, boolean notConfigured, int created, int updated, Integer errcode, String message) {
            this.ok = ok;
            this.notConfigured = notConfigured;
            this.created = created;
            this.updated = updated;
            this.errcode = errcode;
            this.message = message;
        }

        public static PublishResult ok(String json) {
            JSONObject obj = parseObject(json);
            return new PublishResult(true, false, obj.optInt("created", 0), obj.optInt("updated", 0), null, null);
        }

        public static PublishResult notConfigured() {
            return new PublishResult(false, true, 0, 0, null, "请先配置公众号");
        }

        public static PublishResult failed(String message, Integer errcode) {
            return new PublishResult(false, false, 0, 0, errcode, message);
        }

        public boolean isConfigError() {
            return notConfigured || (errcode != null && (errcode == 40164 || errcode == 40125 || errcode == 40013));
        }
    }

    public static final class LinkTarget {
        public final String type;
        public final String owner;
        public final String stem;
        public final String title;
        public final ArticleDoc doc;
        public final String rawJson;

        private LinkTarget(String type, String owner, String stem, String title, ArticleDoc doc, String rawJson) {
            this.type = type == null ? "" : type;
            this.owner = owner == null ? "" : owner;
            this.stem = stem == null ? "" : stem;
            this.title = title == null ? "" : title;
            this.doc = doc;
            this.rawJson = rawJson == null ? "" : rawJson;
        }

        public static LinkTarget fromJson(String text) throws Exception {
            JSONObject obj = new JSONObject(text == null ? "{}" : text);
            ArticleDoc doc = null;
            if (obj.has("articles") || obj.has("body")) {
                doc = ArticleDoc.fromJson(obj.toString());
            }
            return new LinkTarget(obj.optString("type", ""),
                    obj.optString("owner", ""),
                    obj.optString("stem", ""),
                    obj.optString("title", ""),
                    doc,
                    text);
        }

        public boolean isCommunity() {
            return "community".equals(type);
        }
    }

    public boolean deleteArticle(Recording rec) {
        boolean ok = deleteKey(rec.articleKey());
        deleteKey(rec.srtKey());
        deleteKey(rec.emptyKey());
        deleteKey(rec.tagsKey());
        titleCache.remove(rec.articleKey());
        tagsCache.remove(rec.articleKey());
        return ok;
    }

    public boolean restyle(Recording rec, int styleVersion) throws Exception {
        JSONObject body = restyleRequestBody(rec.stem(), styleVersion);
        HttpClient.Response response = http.postJson(
                Api.agentBase() + "/restyle",
                auth.bearer(),
                body.toString().getBytes("UTF-8"),
                new HttpClient.RequestOptions().readTimeoutMs(300_000));
        return response.ok() && new JSONObject(response.text()).optBoolean("ok", true);
    }

    public boolean remine(Recording rec) throws Exception {
        JSONObject body = restyleRequestBody(rec.stem(), null);
        HttpClient.Response response = http.postJson(
                Api.agentBase() + "/restyle",
                auth.bearer(),
                body.toString().getBytes("UTF-8"));
        return response.ok() && new JSONObject(response.text()).optBoolean("ok", true);
    }

    public static JSONObject restyleRequestBody(String stem, Integer styleVersion) throws Exception {
        JSONObject body = new JSONObject().put("stem", stem);
        if (styleVersion != null) body.put("styleV", styleVersion);
        return body;
    }

    public XhsPack xhsPack(Recording rec) throws Exception {
        JSONObject body = xhsPackRequestBody(rec.stem());
        HttpClient.Response response = http.postJson(
                Api.agentBase() + "/xhs-pack",
                auth.bearer(),
                body.toString().getBytes("UTF-8"));
        if (!response.ok()) return null;
        return XhsPack.fromJson(new JSONObject(response.text()));
    }

    public static JSONObject xhsPackRequestBody(String stem) throws Exception {
        return new JSONObject().put("stem", stem);
    }

    public boolean patchQuestion(Recording rec, String id, String status) throws Exception {
        JSONObject body = patchQuestionRequestBody(id, status);
        HttpClient.Response response = http.patchJson(
                Api.filesBase() + "/articles/" + Api.path(rec.stem()) + "/question",
                auth.bearer(),
                body.toString().getBytes("UTF-8"));
        return response.ok();
    }

    public static JSONObject patchQuestionRequestBody(String id, String status) throws Exception {
        return new JSONObject().put("id", id).put("status", status);
    }

    public boolean deleteAccount() throws Exception {
        HttpClient.Response response = http.postJson(Api.filesBase() + "/account/delete", auth.bearer(), new byte[0]);
        return response.ok();
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
        String token = auth.bearer();
        if (token.equals(cachedScopeToken) && cachedScope != null && !cachedScope.isEmpty()) return cachedScope;
        HttpClient.Response response = http.get(Api.filesBase() + "/whoami", token);
        if (!response.ok()) return null;
        cachedScope = new JSONObject(response.text()).optString("scope", null);
        cachedScopeToken = token;
        return cachedScope;
    }

    public byte[] photoData(String fullKey) throws Exception {
        return PhotoService.data(fullKey, false, http);
    }

    public Bitmap photoImage(String fullKey, boolean ignoringLocalCache) throws Exception {
        return PhotoService.image(fullKey, ignoringLocalCache);
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
        final boolean hasArticles;
        final boolean isEmpty;
        final boolean hasTags;

        Item(String name, String uploaded, boolean hasArticles, boolean isEmpty, boolean hasTags) {
            this.name = name;
            this.uploaded = uploaded == null ? "" : uploaded;
            this.hasArticles = hasArticles;
            this.isEmpty = isEmpty;
            this.hasTags = hasTags;
        }
    }

    private static final class MetadataResult {
        final Recording recording;
        final ArticleDoc doc;
        final List<String> sidecarTags;

        MetadataResult(Recording recording, ArticleDoc doc, List<String> sidecarTags) {
            this.recording = recording;
            this.doc = doc;
            this.sidecarTags = sidecarTags;
        }
    }

    public static final class XhsPack {
        public final String title;
        public final String body;
        public final List<String> tags;
        public final List<String> photoKeys;

        private XhsPack(String title, String body, List<String> tags, List<String> photoKeys) {
            this.title = title == null ? "" : title;
            this.body = body == null ? "" : body;
            this.tags = tags == null ? new ArrayList<>() : tags;
            this.photoKeys = photoKeys == null ? new ArrayList<>() : photoKeys;
        }

        public String clipboardText() {
            StringBuilder out = new StringBuilder();
            out.append(title).append("\n\n").append(body);
            if (!tags.isEmpty()) {
                out.append("\n\n");
                for (int i = 0; i < tags.size(); i++) {
                    if (i > 0) out.append(' ');
                    out.append('#').append(tags.get(i));
                }
            }
            return out.toString();
        }

        static XhsPack fromJson(JSONObject obj) {
            List<String> tags = new ArrayList<>();
            JSONArray tagArr = obj.optJSONArray("tags");
            if (tagArr != null) for (int i = 0; i < tagArr.length(); i++) {
                String tag = tagArr.optString(i, "").trim();
                if (!tag.isEmpty()) tags.add(tag);
            }
            List<String> photoKeys = new ArrayList<>();
            JSONArray photoArr = obj.optJSONArray("photoKeys");
            if (photoArr != null) for (int i = 0; i < photoArr.length(); i++) {
                String key = photoArr.optString(i, "").trim();
                if (!key.isEmpty()) photoKeys.add(key);
            }
            return new XhsPack(obj.optString("title", ""), obj.optString("body", ""), tags, photoKeys);
        }
    }
}
