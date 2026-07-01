package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;

public final class AuthStore {
    private static final String PREFS = "voicedrop.auth";
    private static final String ANON = "anon";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SharedPreferences prefs;

    public AuthStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureAnon();
    }

    public String bearer() {
        return ensureAnon();
    }

    public String anonId() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bearer().getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder("anon-");
            for (int i = 0; i < 8 && i < hash.length; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            return "anon-local";
        }
    }

    public void resetAnonymous() {
        prefs.edit().putString(ANON, newAnon()).apply();
    }

    public boolean adoptToken(String token) {
        if (token == null || !token.startsWith("anon_") || token.length() < 20) return false;
        prefs.edit().putString(ANON, token).apply();
        return true;
    }

    private String ensureAnon() {
        String existing = prefs.getString(ANON, "");
        if (existing != null && !existing.isEmpty()) return existing;
        String token = newAnon();
        prefs.edit().putString(ANON, token).apply();
        return token;
    }

    private static String newAnon() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder("anon_");
        for (byte b : bytes) builder.append(String.format("%02x", b));
        return builder.toString();
    }
}
