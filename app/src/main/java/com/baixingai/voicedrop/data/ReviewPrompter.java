package com.baixingai.voicedrop.data;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public final class ReviewPrompter {
    private static final String PREFS = "voicedrop.review";
    private static final String ARTICLE_OPENS = "articleOpens";

    private ReviewPrompter() {}

    public static void articleOpened(Activity activity) {
        if (activity == null) return;
        android.content.SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(ARTICLE_OPENS, 0) + 1;
        prefs.edit().putInt(ARTICLE_OPENS, count).apply();
        if (!shouldPrompt(count)) return;
        try {
            Uri uri = Uri.parse("market://details?id=" + activity.getPackageName());
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
    }

    public static boolean shouldPrompt(int articleOpenCount) {
        return articleOpenCount == 3 || articleOpenCount == 10 || articleOpenCount == 30;
    }
}
