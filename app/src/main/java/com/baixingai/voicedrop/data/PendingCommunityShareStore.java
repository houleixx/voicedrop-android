package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public final class PendingCommunityShareStore {
    private static final String PREFS = "voicedrop.pending_community_share";
    private static final String KEY_PENDING = "vd.pendingCommunityShare";
    private static final long EXPIRE_MS = 15L * 60L * 1000L;

    private final Storage storage;
    private final Clock clock;

    public PendingCommunityShareStore(Context context) {
        this(new SharedPreferencesStorage(context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)), new Clock() {
            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }
        });
    }

    public PendingCommunityShareStore(Storage storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }

    public void save(String audioName, String replyToShareId) {
        if (isBlank(audioName)) return;
        try {
            JSONObject json = new JSONObject();
            json.put("audioName", audioName);
            json.put("replyToShareId", isBlank(replyToShareId) ? JSONObject.NULL : replyToShareId);
            json.put("createdAt", clock.nowMs());
            storage.put(KEY_PENDING, json.toString());
        } catch (Exception ignored) {
        }
    }

    public Pending peek() {
        String raw = storage.get(KEY_PENDING);
        if (isBlank(raw)) return null;
        StoredPending pending = parse(raw);
        if (pending == null || isExpired(pending.createdAtMs)) {
            storage.remove(KEY_PENDING);
            return null;
        }
        return new Pending(pending.audioName, pending.replyToShareId);
    }

    public Pending consume(String audioName) {
        Pending pending = peek();
        if (pending == null || isBlank(audioName) || !audioName.equals(pending.audioName)) return null;
        storage.remove(KEY_PENDING);
        return pending;
    }

    public void clear() {
        storage.remove(KEY_PENDING);
    }

    private StoredPending parse(String raw) {
        try {
            JSONObject json = new JSONObject(raw);
            String audioName = json.optString("audioName", "");
            long createdAtMs = json.optLong("createdAt", 0L);
            if (isBlank(audioName) || createdAtMs <= 0L) return null;
            String replyToShareId = json.isNull("replyToShareId") ? null : json.optString("replyToShareId", null);
            if (isBlank(replyToShareId)) replyToShareId = null;
            return new StoredPending(audioName, replyToShareId, createdAtMs);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isExpired(long createdAtMs) {
        return clock.nowMs() - createdAtMs >= EXPIRE_MS;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface Storage {
        String get(String key);
        void put(String key, String value);
        void remove(String key);
    }

    public interface Clock {
        long nowMs();
    }

    public static final class Pending {
        public final String audioName;
        public final String replyToShareId;

        public Pending(String audioName, String replyToShareId) {
            this.audioName = audioName;
            this.replyToShareId = replyToShareId;
        }
    }

    private static final class StoredPending {
        final String audioName;
        final String replyToShareId;
        final long createdAtMs;

        StoredPending(String audioName, String replyToShareId, long createdAtMs) {
            this.audioName = audioName;
            this.replyToShareId = replyToShareId;
            this.createdAtMs = createdAtMs;
        }
    }

    private static final class SharedPreferencesStorage implements Storage {
        private final SharedPreferences prefs;

        SharedPreferencesStorage(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public String get(String key) {
            return prefs.getString(key, null);
        }

        @Override
        public void put(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }

        @Override
        public void remove(String key) {
            prefs.edit().remove(key).apply();
        }
    }
}
