package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.baixingai.voicedrop.core.AccountScopedCacheKey;

/** Account-scoped last-successful response for the authenticated books shelf. */
public final class BookShelfCache {
    private static final String PREFS = "voicedrop.books";
    private static final String LEGACY_UNSCOPED_KEY = "index";
    private static final String SCOPED_PREFIX = "index_v2_";

    private final SharedPreferences preferences;
    private final String identity;
    private final String key;

    public BookShelfCache(Context context, String stableIdentity) {
        identity = stableIdentity == null ? "" : stableIdentity;
        key = AccountScopedCacheKey.create(SCOPED_PREFIX, identity);
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // The former shared key may contain another account's hidden books. It is
        // deliberately not migrated into any account bucket.
        preferences.edit().remove(LEGACY_UNSCOPED_KEY).apply();
    }

    public boolean matches(String stableIdentity) {
        return identity.equals(stableIdentity == null ? "" : stableIdentity);
    }

    public String read() {
        return preferences.getString(key, "");
    }

    public void store(String raw) {
        if (raw == null || raw.isEmpty()) return;
        preferences.edit().putString(key, raw).apply();
    }
}
