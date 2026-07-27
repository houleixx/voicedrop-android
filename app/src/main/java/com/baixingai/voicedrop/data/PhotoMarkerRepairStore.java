package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persists the photo keys that must survive a recording's initial article mine.
 *
 * The miner asks the model to place every photo marker, but model output is not a
 * durable contract. Once the article is ready, this store deterministically appends
 * any omitted marker so detail pages never lose an already-uploaded recording photo.
 */
public final class PhotoMarkerRepairStore {
    public static final String PREFS = "voicedrop.photo_marker_repairs";
    private static final String ITEMS = "items";
    private static final Pattern PHOTO_KEY =
            Pattern.compile("^photos/.+\\.(?:jpe?g|png)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHOTO_MARKER =
            Pattern.compile("\\[\\[photo:([^\\]]+)\\]\\]");

    private final SharedPreferences prefs;

    public PhotoMarkerRepairStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized boolean remember(String recordingName, List<String> photoKeys) {
        String name = recordingName == null ? "" : recordingName.trim();
        List<String> cleanKeys = uniquePhotoKeys(photoKeys);
        if (name.isEmpty() || cleanKeys.isEmpty()) return false;
        List<Plan> plans = pending();
        Plan existing = null;
        for (Plan plan : plans) {
            if (name.equals(plan.recordingName)) {
                existing = plan;
                break;
            }
        }
        if (existing == null) {
            plans.add(new Plan(name, cleanKeys));
        } else {
            existing.photoKeys = uniquePhotoKeys(join(existing.photoKeys, cleanKeys));
        }
        persist(plans);
        return true;
    }

    public synchronized int repairReady(List<Recording> recordings, LibraryStore library) {
        if (library == null) return 0;
        List<Plan> plans = pending();
        Set<String> completed = new HashSet<>();
        int repaired = 0;
        for (Plan plan : plans) {
            Recording recording = readyRecording(recordings, plan.recordingName);
            if (recording == null) continue;
            ArticleDoc doc = library.fetchDoc(recording);
            if (doc == null || doc.articles == null || doc.articles.isEmpty()) continue;
            RepairResult result = ensurePhotoMarkers(doc.articles, plan.photoKeys);
            if (!result.changed) {
                completed.add(plan.recordingName);
            } else if (library.saveArticles(recording, result.articles)) {
                completed.add(plan.recordingName);
                repaired++;
            }
        }
        if (!completed.isEmpty()) {
            List<Plan> remaining = new ArrayList<>();
            for (Plan plan : plans) {
                if (!completed.contains(plan.recordingName)) remaining.add(plan);
            }
            persist(remaining);
        }
        return repaired;
    }

    public static RepairResult ensurePhotoMarkers(List<MinedArticle> articles, List<String> photoKeys) {
        List<MinedArticle> result = new ArrayList<>();
        if (articles != null) result.addAll(articles);
        if (result.isEmpty()) return new RepairResult(result, false);

        Set<String> existing = new HashSet<>();
        for (MinedArticle article : result) {
            Matcher marker = PHOTO_MARKER.matcher(article == null ? "" : article.body);
            while (marker.find()) existing.add(marker.group(1).trim());
        }
        List<String> missing = new ArrayList<>();
        for (String key : uniquePhotoKeys(photoKeys)) {
            if (!existing.contains(key)) missing.add(key);
        }
        if (missing.isEmpty()) return new RepairResult(result, false);

        int lastIndex = result.size() - 1;
        MinedArticle last = result.get(lastIndex);
        StringBuilder body = new StringBuilder(last.body == null ? "" : last.body.trim());
        for (String key : missing) {
            if (body.length() > 0) body.append("\n\n");
            body.append("[[photo:").append(key).append("]]");
        }
        result.set(lastIndex, new MinedArticle(
                last.title, body.toString(), last.style, last.wechatMediaId));
        return new RepairResult(result, true);
    }

    private List<Plan> pending() {
        List<Plan> plans = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(prefs.getString(ITEMS, "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("name", "").trim();
                JSONArray keysJson = item.optJSONArray("photoKeys");
                List<String> keys = new ArrayList<>();
                if (keysJson != null) {
                    for (int k = 0; k < keysJson.length(); k++) keys.add(keysJson.optString(k));
                }
                keys = uniquePhotoKeys(keys);
                if (!name.isEmpty() && !keys.isEmpty()) plans.add(new Plan(name, keys));
            }
        } catch (Exception ignored) {
        }
        return plans;
    }

    private void persist(List<Plan> plans) {
        JSONArray items = new JSONArray();
        try {
            for (Plan plan : plans) {
                items.put(new JSONObject()
                        .put("name", plan.recordingName)
                        .put("photoKeys", new JSONArray(plan.photoKeys)));
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString(ITEMS, items.toString()).commit();
    }

    private static Recording readyRecording(List<Recording> recordings, String name) {
        if (recordings == null) return null;
        for (Recording recording : recordings) {
            if (recording != null && recording.hasArticles && name.equals(recording.audioName)) {
                return recording;
            }
        }
        return null;
    }

    private static List<String> uniquePhotoKeys(List<String> keys) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (keys != null) {
            for (String value : keys) {
                String key = value == null ? "" : value.trim();
                if (PHOTO_KEY.matcher(key).matches()) unique.add(key);
            }
        }
        return new ArrayList<>(unique);
    }

    private static List<String> join(List<String> first, List<String> second) {
        List<String> joined = new ArrayList<>();
        if (first != null) joined.addAll(first);
        if (second != null) joined.addAll(second);
        return joined;
    }

    private static final class Plan {
        final String recordingName;
        List<String> photoKeys;

        Plan(String recordingName, List<String> photoKeys) {
            this.recordingName = recordingName;
            this.photoKeys = photoKeys;
        }
    }

    public static final class RepairResult {
        public final List<MinedArticle> articles;
        public final boolean changed;

        RepairResult(List<MinedArticle> articles, boolean changed) {
            this.articles = articles;
            this.changed = changed;
        }
    }
}
