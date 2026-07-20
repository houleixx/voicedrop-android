package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.core.PromptTree;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PromptStore {
    public static final String CACHE_KEY = "promptsCache.v1";
    public static final String BUSY = "正在保存，请稍候";
    public static final String CONFLICT = "列表已更新，请重新排序";
    private static final String PREFS = "voicedrop.prompts";

    public interface Transport {
        HttpClient.Response get(String url, String bearer) throws Exception;
        HttpClient.Response put(String url, String bearer, byte[] json) throws Exception;
        HttpClient.Response post(String url, String bearer, byte[] json) throws Exception;
        HttpClient.Response delete(String url, String bearer) throws Exception;
    }

    public interface Cache {
        String get(String key);
        void put(String key, String value);
    }

    private final Object lock = new Object();
    private final Transport transport;
    private final Cache cache;
    private final String bearer;
    private final String shareBearer;
    private final String baseUrl;
    private final List<PromptNode> builtin;
    private List<PromptNode> items;
    private boolean mutating;

    public PromptStore(Context context, AuthStore auth, HttpClient http) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.transport = new Transport() {
            @Override public HttpClient.Response get(String url, String bearer) throws Exception { return http.get(url, bearer); }
            @Override public HttpClient.Response put(String url, String bearer, byte[] json) throws Exception { return http.putBytes(url, bearer, "application/json", json); }
            @Override public HttpClient.Response post(String url, String bearer, byte[] json) throws Exception { return http.postJson(url, bearer, json); }
            @Override public HttpClient.Response delete(String url, String bearer) throws Exception { return http.delete(url, bearer); }
        };
        this.cache = new Cache() {
            @Override public String get(String key) { return prefs.getString(key, null); }
            @Override public void put(String key, String value) { prefs.edit().putString(key, value).apply(); }
        };
        this.bearer = auth.bearer();
        this.shareBearer = auth.session();
        this.baseUrl = Api.agentBase();
        this.builtin = PromptDefaults.items();
        this.items = loadCached();
    }

    public PromptStore(Transport transport, Cache cache, String bearer, String baseUrl, List<PromptNode> builtin) {
        this(transport, cache, bearer, bearer, baseUrl, builtin);
    }

    public PromptStore(Transport transport, Cache cache, String bearer, String shareBearer,
                       String baseUrl, List<PromptNode> builtin) {
        this.transport = transport;
        this.cache = cache;
        this.bearer = bearer;
        this.shareBearer = shareBearer;
        this.baseUrl = baseUrl;
        this.builtin = PromptTree.copy(builtin);
        this.items = loadCached();
    }

    public List<PromptNode> items() {
        synchronized (lock) { return PromptTree.copy(items); }
    }

    public boolean isMutating() {
        synchronized (lock) { return mutating; }
    }

    public UIConfigStore.MenuConfig textMenu() {
        synchronized (lock) { return PromptTree.menu(items, "text"); }
    }

    public UIConfigStore.MenuConfig imageMenu() {
        synchronized (lock) { return PromptTree.menu(items, "image"); }
    }

    public String refresh() {
        try {
            HttpClient.Response response = transport.get(baseUrl + "/prompts", bearer);
            if (!response.ok()) return "刷新失败，正在显示上次内容";
            List<PromptNode> fresh = PromptTree.decode(response.text());
            synchronized (lock) { items = fresh; }
            cache.put(CACHE_KEY, response.text());
            return null;
        } catch (Exception error) {
            return "刷新失败，正在显示上次内容";
        }
    }

    public String saveDraft(List<PromptNode> draft) {
        List<PromptNode> snapshot;
        synchronized (lock) {
            if (mutating) return BUSY;
            mutating = true;
            snapshot = PromptTree.copy(items);
            items = PromptTree.copy(draft);
        }
        try {
            byte[] body = PromptTree.encodeRaw(draft).getBytes(StandardCharsets.UTF_8);
            HttpClient.Response response = transport.put(baseUrl + "/prompts", bearer, body);
            if (!response.ok()) throw new IllegalStateException("prompt save HTTP " + response.code);
            List<PromptNode> fresh = PromptTree.decode(response.text());
            synchronized (lock) { items = fresh; }
            cache.put(CACHE_KEY, response.text());
            return null;
        } catch (Exception error) {
            synchronized (lock) { items = snapshot; }
            return "保存失败，请重试";
        } finally {
            synchronized (lock) { mutating = false; }
        }
    }

    public String replace(String id, PromptNode replacement) {
        List<PromptNode> current = items();
        PromptTree.MutationResult result = PromptTree.replace(current, id, replacement);
        return result.changed ? saveDraft(result.items) : "提示词不存在";
    }

    public String remove(String id) {
        PromptTree.MutationResult result = PromptTree.remove(items(), id);
        return result.changed ? saveDraft(result.items) : "提示词不存在";
    }

    public String add(PromptNode node, String groupId) {
        List<PromptNode> before = items();
        List<PromptNode> after = PromptTree.append(before, node, groupId);
        return PromptTree.flattenIds(before).equals(PromptTree.flattenIds(after)) ? "分组不存在" : saveDraft(after);
    }

    public String applyReorder(List<PromptNode> draft, List<String> baseline) {
        synchronized (lock) {
            if (!PromptTree.flattenIds(items).equals(baseline)) return CONFLICT;
        }
        return saveDraft(draft);
    }

    public String restoreDefaults() {
        return responseMutation("/prompts/restore-defaults", new JSONObject());
    }

    public String importCode(String code) {
        if (PromptTree.extractShareCode(code) == null || code.length() != 7) return "请输入有效的 7 位分享码";
        try {
            String error = responseMutation("/prompts/import", new JSONObject().put("code", code));
            if (error == null) refresh(); // 服务端可能按 groupPath 放进分组；失败时保留已导入的本地回退。
            return error;
        } catch (Exception error) {
            return "操作失败，请重试";
        }
    }

    private String responseMutation(String path, JSONObject body) {
        synchronized (lock) {
            if (mutating) return BUSY;
            mutating = true;
        }
        try {
            HttpClient.Response response = transport.post(baseUrl + path, bearer, body.toString().getBytes(StandardCharsets.UTF_8));
            if (!response.ok()) throw new IllegalStateException("prompt mutation HTTP " + response.code);
            if (path.endsWith("/import")) {
                JSONObject imported = new JSONObject(response.text()).optJSONObject("item");
                if (imported == null) throw new IllegalStateException("missing imported item");
                PromptNode node = PromptNode.fromResolved(imported);
                synchronized (lock) {
                    List<PromptNode> next = PromptTree.copy(items);
                    if (!PromptTree.flattenIds(next).contains(node.id)) next.add(node);
                    items = next;
                    cache.put(CACHE_KEY, PromptTree.encodeResolved(items));
                }
                return null;
            }
            List<PromptNode> fresh = PromptTree.decode(response.text());
            synchronized (lock) { items = fresh; }
            cache.put(CACHE_KEY, response.text());
            return null;
        } catch (Exception error) {
            return "操作失败，请重试";
        } finally {
            synchronized (lock) { mutating = false; }
        }
    }

    public Preview preview(String code) {
        try {
            HttpClient.Response response = transport.get(baseUrl + "/prompt-share/" + code, "");
            if (!response.ok()) return null;
            JSONObject json = new JSONObject(response.text());
            Preview preview = new Preview();
            preview.label = json.optString("label", "");
            preview.prompt = json.optString("prompt", "");
            preview.author = json.optString("author", "");
            preview.importCount = json.optInt("importCount", 0);
            preview.kind = json.optString("kind", null);
            JSONArray applies = json.optJSONArray("appliesTo");
            if (applies != null) for (int i = 0; i < applies.length(); i++) preview.appliesTo.add(applies.optString(i));
            return preview;
        } catch (Exception error) {
            return null;
        }
    }

    public Map<String, ShareState> shareStates() {
        Map<String, ShareState> result = new HashMap<>();
        try {
            HttpClient.Response response = transport.get(baseUrl + "/prompt-shares", bearer);
            if (!response.ok()) return result;
            JSONObject byItem = new JSONObject(response.text()).optJSONObject("byItem");
            if (byItem == null || byItem.names() == null) return result;
            JSONArray names = byItem.names();
            for (int i = 0; i < names.length(); i++) {
                String id = names.optString(i);
                JSONObject value = byItem.optJSONObject(id);
                if (value != null) result.put(id, new ShareState(value.optString("code", ""), value.optBoolean("sharing", false)));
            }
        } catch (Exception ignored) {}
        return result;
    }

    public ShareState setSharing(String id, boolean sharing) {
        if (shareBearer == null || shareBearer.isEmpty()) {
            return new ShareState("", false, "请先登录微信后分享提示词");
        }
        try {
            HttpClient.Response response = sharing
                    ? transport.post(baseUrl + "/prompt-share", shareBearer, new JSONObject().put("id", id).toString().getBytes(StandardCharsets.UTF_8))
                    : transport.delete(baseUrl + "/prompt-share/" + Uri.encode(id), shareBearer);
            if (!response.ok()) {
                String remote = "";
                try { remote = new JSONObject(response.text()).optString("error", ""); } catch (Exception ignored) {}
                if (response.code == 429) remote = "今天生成分享码的次数已达上限，明天再试";
                else if ("needs_apple_signin".equals(remote) || "needs_wechat_signin".equals(remote)) remote = "请先登录微信后分享提示词";
                else if ("content_flagged".equals(remote)) remote = "提示词未通过社区审核，暂时不能分享";
                else if (remote.isEmpty()) remote = "提示词分享操作失败";
                return new ShareState("", false, remote);
            }
            JSONObject body = new JSONObject(response.text());
            return new ShareState(body.optString("code", ""), body.optBoolean("sharing", sharing));
        } catch (Exception error) {
            return new ShareState("", false, "提示词分享操作失败");
        }
    }

    private List<PromptNode> loadCached() {
        String raw = cache.get(CACHE_KEY);
        if (raw != null && !raw.isEmpty()) {
            try { return PromptTree.decode(raw); } catch (Exception ignored) {}
        }
        return PromptTree.copy(builtin);
    }

    public static final class Preview {
        public String label;
        public String prompt;
        public String author;
        public String kind;
        public int importCount;
        public final List<String> appliesTo = new ArrayList<>();
    }

    public static final class ShareState {
        public final String code;
        public final boolean sharing;
        public final String error;
        public ShareState(String code, boolean sharing) { this(code, sharing, null); }
        public ShareState(String code, boolean sharing, String error) {
            this.code = code == null ? "" : code;
            this.sharing = sharing;
            this.error = error;
        }
    }
}
