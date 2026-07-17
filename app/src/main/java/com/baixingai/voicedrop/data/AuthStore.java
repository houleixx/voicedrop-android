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
    private static final String PRE_WECHAT_ANON = "pre_wechat_anon";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SharedPreferences prefs;

    public AuthStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureAnon();
    }

    public String bearer() {
        String session = session();
        return session.isEmpty() ? anonymousBearer() : session;
    }

    public String anonymousBearer() {
        return ensureAnon();
    }

    public String session() {
        String existing = prefs.getString(SESSION, "");
        if (existing == null || existing.isEmpty()) return "";
        if (!isSessionToken(existing) || isJWTExpired(existing)) {
            signOutWechat();
            return "";
        }
        return existing;
    }

    public String communityBearer() {
        return bearer();
    }

    public boolean isWechatAuthenticated() {
        return !session().isEmpty();
    }

    public String anonId() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(anonymousBearer().getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder("anon-");
            for (int i = 0; i < 16 && i < hash.length; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            return "anon-local";
        }
    }

    /** Bucket prefix used by the backend's community owner field. */
    public String storageScope() {
        String signed = session();
        if (!signed.isEmpty()) {
            try {
                String[] parts = signed.split("\\.");
                byte[] data = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                String scope = new JSONObject(new String(data, "UTF-8")).optString("scope", "");
                if (scope.startsWith("users/") && scope.endsWith("/")) return scope;
            } catch (Exception ignored) {}
        }
        return "users/" + anonId() + "/";
    }

    public void resetAnonymous() {
        prefs.edit().putString(ANON, newAnon())
                .remove(SESSION).remove(PRE_WECHAT_ANON).apply();
    }

    public boolean adoptToken(String token) {
        if (token == null || !token.startsWith("anon_") || token.length() < 20) return false;
        prefs.edit().putString(ANON, token).remove(SESSION).remove(PRE_WECHAT_ANON).apply();
        return true;
    }

    public boolean storeSession(String token) {
        if (!isSessionToken(token)) return false;
        prefs.edit().putString(SESSION, token).remove(PRE_WECHAT_ANON).apply();
        return true;
    }

    public boolean switchToWechatAccount(String token) {
        if (!isSessionToken(token)) return false;
        prefs.edit()
                .putString(PRE_WECHAT_ANON, anonymousBearer())
                .putString(SESSION, token)
                .apply();
        return true;
    }

    public void signOutWechat() {
        String previous = prefs.getString(PRE_WECHAT_ANON, "");
        SharedPreferences.Editor editor = prefs.edit()
                .remove(SESSION)
                .remove(PRE_WECHAT_ANON);
        if (previous != null && previous.startsWith("anon_") && previous.length() >= 20) {
            editor.putString(ANON, previous);
        }
        editor.apply();
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

    static boolean isSessionToken(String token) {
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
