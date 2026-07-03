package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

public final class PendingReplyStore {
    private static final String PREFS = "voicedrop.pending_replies";
    private static final String KEY_PREFIX = "vd.pendingReply.";

    private final Storage storage;

    public PendingReplyStore(Context context) {
        this(new SharedPreferencesStorage(context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)));
    }

    public PendingReplyStore(Storage storage) {
        this.storage = storage;
    }

    public void put(String audioName, String replyToShareId) {
        if (isBlank(audioName) || isBlank(replyToShareId)) return;
        storage.put(key(audioName), replyToShareId);
    }

    public String replyTo(String audioName) {
        if (isBlank(audioName)) return null;
        String value = storage.get(key(audioName));
        return isBlank(value) ? null : value;
    }

    public void remove(String audioName) {
        if (!isBlank(audioName)) storage.remove(key(audioName));
    }

    public int publishReadyReplies(List<Recording> recordings, Publisher publisher) throws Exception {
        int published = 0;
        if (recordings == null || publisher == null) return published;
        for (Recording recording : recordings) {
            if (recording == null || !recording.hasArticles) continue;
            String replyTo = replyTo(recording.audioName);
            if (replyTo == null) continue;
            if (publisher.publish(recording, replyTo)) {
                remove(recording.audioName);
                published++;
            }
        }
        return published;
    }

    private static String key(String audioName) {
        return KEY_PREFIX + audioName;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface Publisher {
        boolean publish(Recording recording, String replyToShareId) throws Exception;
    }

    public interface Storage {
        String get(String key);
        void put(String key, String value);
        void remove(String key);
    }

    private static final class SharedPreferencesStorage implements Storage {
        private final SharedPreferences prefs;

        SharedPreferencesStorage(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override public String get(String key) {
            return prefs.getString(key, null);
        }

        @Override public void put(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }

        @Override public void remove(String key) {
            prefs.edit().remove(key).apply();
        }
    }
}
