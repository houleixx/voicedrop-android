package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;

public final class AuthStore {
    private static final String PREFS = "voicedrop.auth";
    private static final String ANON = "anon";
    private static final String SESSION = "session";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SharedPreferences prefs;

    public AuthStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureAnon();
    }

    public String bearer() {
        return ensureAnon();
    }

    public String session() {
        String existing = prefs.getString(SESSION, "");
        if (existing == null || existing.isEmpty()) return "";
        if (!isSessionToken(existing) || isJWTExpired(existing)) {
            prefs.edit().remove(SESSION).apply();
            return "";
        }
        return existing;
    }

    public String communityBearer() {
        String session = session();
        return session.isEmpty() ? bearer() : session;
    }

    public boolean isWechatAuthenticated() {
        return !session().isEmpty();
    }

    public String anonId() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bearer().getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder("anon-");
            for (int i = 0; i < 16 && i < hash.length; i++) {
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
        prefs.edit().putString(ANON, token).remove(SESSION).apply();
        return true;
    }

    public boolean storeSession(String token) {
        if (!isSessionToken(token)) return false;
        prefs.edit().putString(SESSION, token).apply();
        return true;
    }

    public void signOutWechat() {
        prefs.edit().remove(SESSION).apply();
    }

    private String ensureAnon() {
        String existing = prefs.getString(ANON, "");
        if (existing != null && !existing.isEmpty()) {
            if (isSessionToken(existing)) {
                SharedPreferences.Editor editor = prefs.edit();
                if (prefs.getString(SESSION, "").isEmpty()) editor.putString(SESSION, existing);
                String token = newAnon();
                editor.putString(ANON, token).apply();
                return token;
            }
            return existing;
        }
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

    private static boolean isSessionToken(String token) {
        if (token == null) return false;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;
        for (String part : parts) {
            if (part.length() < 8 || !part.matches("[A-Za-z0-9_-]+")) return false;
        }
        return true;
    }

    private static boolean isJWTExpired(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return true;
            byte[] data = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject payload = new JSONObject(new String(data, "UTF-8"));
            long exp = payload.optLong("exp", 0);
            return exp <= 0 || System.currentTimeMillis() / 1000L >= exp;
        } catch (Exception e) {
            return true;
        }
    }
}
