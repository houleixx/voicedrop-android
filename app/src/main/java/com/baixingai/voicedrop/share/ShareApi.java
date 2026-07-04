package com.baixingai.voicedrop.share;

import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ShareApi {
    private final AuthStore auth;
    private final HttpClient http;

    public ShareApi(AuthStore auth, HttpClient http) {
        this.auth = auth;
        this.http = http;
    }

    public boolean collectStyle(String type, String title, String text, String source) throws Exception {
        HttpClient.Response response = http.postJson(
                Api.filesBase() + "/style/collect",
                auth.bearer(),
                collectStyleBody(type, title, text, source).toString().getBytes("UTF-8"));
        return response.ok();
    }

    public List<DatasetItem> fetchDataset() throws Exception {
        HttpClient.Response response = http.get(Api.filesBase() + "/style/dataset", auth.bearer());
        List<DatasetItem> out = new ArrayList<>();
        if (!response.ok()) return out;
        JSONArray items = new JSONObject(response.text()).optJSONArray("items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            out.add(new DatasetItem(
                    item.optString("id"),
                    item.optString("type"),
                    item.optString("title"),
                    item.optString("source"),
                    item.optString("collectedAt"),
                    item.optInt("chars")));
        }
        return out;
    }

    public boolean deleteDataset() throws Exception {
        return http.delete(Api.filesBase() + "/style/dataset", auth.bearer()).ok();
    }

    public void triggerMine() {
        try {
            http.postJson(Api.agentBase() + "/mine/trigger", auth.bearer(), new byte[0]);
        } catch (Exception ignored) {
        }
    }

    public static JSONObject collectStyleBody(String type, String title, String text, String source) throws Exception {
        return new JSONObject()
                .put("type", type)
                .put("title", title)
                .put("text", text)
                .put("source", source);
    }

    public static String styleExtractTaskName(boolean clearAfter, ZonedDateTime now) {
        String base = RecordingName.make(now, 0, null);
        String tag = clearAfter ? "TaskStyleExtract" : "TaskStyleExtractKeep";
        return base.substring(0, base.length() - ".m4a".length()) + "-" + tag + ".m4a";
    }
}
