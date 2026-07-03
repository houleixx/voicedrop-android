package com.baixingai.voicedrop.update;

import android.content.Context;
import android.content.SharedPreferences;

public final class UpdatePrefs {
    private static final String PREFS = "voicedrop.update";
    private static final String KEY_IGNORED_TAG = "ignoredTag";
    private static final String KEY_LAST_AUTO_CHECK_MS = "lastAutoCheckMs";
    private static final String KEY_LAST_AUTO_CHECK_VERSION = "lastAutoCheckVersion";
    private static final long AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private final SharedPreferences prefs;

    public UpdatePrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean shouldAutoCheck(long nowMs, String currentVersion) {
        long last = prefs.getLong(KEY_LAST_AUTO_CHECK_MS, 0L);
        String lastVersion = prefs.getString(KEY_LAST_AUTO_CHECK_VERSION, "");
        return shouldAutoCheck(nowMs, last, currentVersion, lastVersion);
    }

    public void markAutoChecked(long nowMs, String currentVersion) {
        prefs.edit()
                .putLong(KEY_LAST_AUTO_CHECK_MS, nowMs)
                .putString(KEY_LAST_AUTO_CHECK_VERSION, currentVersion == null ? "" : currentVersion)
                .apply();
    }

    public static boolean shouldAutoCheck(long nowMs, long lastAutoCheckMs,
            String currentVersion, String lastAutoCheckVersion) {
        if (lastAutoCheckMs <= 0L) return true;
        String current = currentVersion == null ? "" : currentVersion;
        String lastVersion = lastAutoCheckVersion == null ? "" : lastAutoCheckVersion;
        if (!current.equals(lastVersion)) return true;
        return nowMs - lastAutoCheckMs >= AUTO_CHECK_INTERVAL_MS;
    }

    public boolean isIgnored(String tag) {
        return tag != null && !tag.isEmpty() && tag.equals(prefs.getString(KEY_IGNORED_TAG, ""));
    }

    public void ignore(String tag) {
        if (tag == null || tag.isEmpty()) return;
        prefs.edit().putString(KEY_IGNORED_TAG, tag).apply();
    }
}
