package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String PREFS = "voicedrop.prefs";
    private static final String DELETE_LOCAL = "deleteLocalAfterUpload";
    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean deleteLocalAfterUpload() {
        return prefs.getBoolean(DELETE_LOCAL, true);
    }

    public void setDeleteLocalAfterUpload(boolean value) {
        prefs.edit().putBoolean(DELETE_LOCAL, value).apply();
    }
}
