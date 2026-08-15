package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Small account-scoped stale-while-revalidate cache for writing-style history. */
public final class WritingStyleHistoryCache {
    public static final String PREFS = "voicedrop.writing_style_history";
    private static final String KEY_PREFIX = "history.v1.";
    private static final int MAX_VERSIONS = 10;

    private final SharedPreferences preferences;
    private final String key;

    public WritingStyleHistoryCache(Context context, String accountIdentity) {
        Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        key = cacheKeyFor(accountIdentity);
    }

    public JSONObject read() {
        String raw = preferences.getString(key, "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject value = new JSONObject(raw);
            return value.optJSONArray("versions") == null ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void write(JSONObject history) {
        if (history == null || history.optJSONArray("versions") == null) return;
        preferences.edit().putString(key, history.toString()).apply();
    }

    public void moveHead(int head) {
        write(withHead(read(), head));
    }

    public void appendVersion(int head, String style) {
        write(withAppendedVersion(read(), head, style, System.currentTimeMillis()));
    }

    public static JSONObject withHead(JSONObject source, int head) {
        if (source == null || source.optJSONArray("versions") == null) return null;
        try {
            JSONObject current = new JSONObject(source.toString());
            current.put("head", head);
            return current;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static JSONObject withAppendedVersion(JSONObject source, int head, String style,
                                                  long savedAt) {
        JSONObject current;
        try {
            current = source == null ? new JSONObject() : new JSONObject(source.toString());
            JSONArray sourceVersions = current.optJSONArray("versions");
            JSONArray next = sourceVersions == null
                    ? new JSONArray() : new JSONArray(sourceVersions.toString());
            next.put(new JSONObject()
                    .put("v", head)
                    .put("savedAt", savedAt)
                    .put("source", "android")
                    .put("style", style));
            while (next.length() > MAX_VERSIONS) next.remove(0);
            current.put("head", head);
            current.put("versions", next);
            return current;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String cacheKeyFor(String accountIdentity) {
        String value = accountIdentity == null ? "" : accountIdentity;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(KEY_PREFIX);
            for (int i = 0; i < 12; i++) key.append(String.format("%02x", digest[i]));
            return key.toString();
        } catch (Exception ignored) {
            return KEY_PREFIX + Integer.toHexString(value.hashCode());
        }
    }
}
