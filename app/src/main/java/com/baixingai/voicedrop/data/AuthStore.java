package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class AuthStore {
    private static final String PREFS = "voicedrop.auth";
    private static final String ANON = "anon";
    private static final String SESSION = "session";
    private static final String PRE_WECHAT_ANON = "pre_wechat_anon";
    private static final String LIBRARY_META_PREFIX = "library_meta_v1_";
    private static final String LIBRARY_LIST_PREFIX = "library_list_v1_";
    private static final String COMMUNITY_FEED_PREFIX = "community_feed_v1_";
    private static final String ARTICLE_DOC_CACHE_DIR = "article-doc-cache-v1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Context context;
    private final SharedPreferences prefs;

    public AuthStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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

    public String libraryMetadataCache() {
        return prefs.getString(libraryMetadataKey(), "");
    }

    public void storeLibraryMetadataCache(String json) {
        prefs.edit().putString(libraryMetadataKey(), json == null ? "" : json).apply();
    }

    /** Last successful /recordings response for the current account (SWR list snapshot). */
    public String libraryListCache() {
        return prefs.getString(libraryListKey(), "");
    }

    public void storeLibraryListCache(String json) {
        prefs.edit().putString(libraryListKey(), json == null ? "" : json).apply();
    }

    public void clearCurrentLibraryListCache() {
        prefs.edit().remove(libraryListKey()).apply();
    }

    private void clearAllLibraryListCaches() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(LIBRARY_LIST_PREFIX)) editor.remove(key);
        }
        editor.apply();
    }

    public String communityFeedCache() {
        return prefs.getString(communityFeedKey(), "");
    }

    public void storeCommunityFeedCache(String json) {
        prefs.edit().putString(communityFeedKey(), json == null ? "" : json).apply();
    }

    public String articleDocCache(String stem) {
        File file = articleDocCacheFile(stem);
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        } catch (Exception ignored) { return ""; }
    }

    public void storeArticleDocCache(String stem, String json) {
        if (stem == null || stem.isEmpty() || json == null || json.isEmpty()) return;
        File file = articleDocCacheFile(stem);
        File dir = file.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return;
        File temporary = new File(dir, file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(json.getBytes("UTF-8"));
            output.flush();
            if (!temporary.renameTo(file)) {
                try (FileOutputStream direct = new FileOutputStream(file)) { direct.write(json.getBytes("UTF-8")); }
                temporary.delete();
            }
        } catch (Exception ignored) { temporary.delete(); }
    }

    public void removeArticleDocCache(String stem) { articleDocCacheFile(stem).delete(); }
    public void clearCurrentArticleDocCaches() { deleteRecursively(articleDocCacheDirectory()); }
    public void clearAllArticleDocCaches() { deleteRecursively(new File(context.getCacheDir(), ARTICLE_DOC_CACHE_DIR)); }

    private File articleDocCacheFile(String stem) { return new File(articleDocCacheDirectory(), cacheComponent(stem) + ".json"); }
    private File articleDocCacheDirectory() {
        return new File(new File(context.getCacheDir(), ARTICLE_DOC_CACHE_DIR), cacheComponent(libraryCacheIdentity()));
    }
    private static String cacheComponent(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.valueOf(value).getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 16 && i < hash.length; i++) out.append(String.format("%02x", hash[i]));
            return out.toString();
        } catch (Exception ignored) { return Integer.toHexString(String.valueOf(value).hashCode()); }
    }
    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }

    private String libraryMetadataKey() {
        return scopedCacheKey(LIBRARY_META_PREFIX);
    }

    private String libraryListKey() {
        return scopedCacheKey(LIBRARY_LIST_PREFIX);
    }

    private String scopedCacheKey(String prefix) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(libraryCacheIdentity().getBytes("UTF-8"));
            StringBuilder key = new StringBuilder(prefix);
            for (int i = 0; i < 12 && i < hash.length; i++) key.append(String.format("%02x", hash[i]));
            return key.toString();
        } catch (Exception e) {
            return prefix + "default";
        }
    }

    private String communityFeedKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(anonymousBearer().getBytes("UTF-8"));
            StringBuilder key = new StringBuilder(COMMUNITY_FEED_PREFIX);
            for (int i = 0; i < 12 && i < hash.length; i++) key.append(String.format("%02x", hash[i]));
            return key.toString();
        } catch (Exception e) {
            return COMMUNITY_FEED_PREFIX + "default";
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

    /**
     * Stable owner identity for local recording/article caches.  A session JWT can be
     * renewed while still belonging to the same account, so it must not be used as a
     * cache key.  This matches the mini-program behavior of scoping cached metadata
     * by a durable local identity instead of the current bearer value.
     */
    public String libraryCacheIdentity() {
        return storageScope();
    }

    public void resetAnonymous() {
        clearAllArticleDocCaches();
        clearAllLibraryListCaches();
        AccountLocalState.clearPendingWork(context);
        prefs.edit().putString(ANON, newAnon())
                .remove(SESSION).remove(PRE_WECHAT_ANON).apply();
    }

    public boolean adoptToken(String token) {
        if (token == null || !token.startsWith("anon_") || token.length() < 20) return false;
        if (!token.equals(anonymousBearer())) {
            AccountLocalState.clearPendingWork(context);
            clearAllArticleDocCaches();
            clearAllLibraryListCaches();
        }
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
        AccountLocalState.clearPendingWork(context);
        clearAllArticleDocCaches();
        clearAllLibraryListCaches();
        prefs.edit()
                .putString(PRE_WECHAT_ANON, anonymousBearer())
                .putString(SESSION, token)
                .apply();
        return true;
    }

    public void signOutWechat() {
        String previous = prefs.getString(PRE_WECHAT_ANON, "");
        if (previous != null && previous.startsWith("anon_") && previous.length() >= 20) {
            AccountLocalState.clearPendingWork(context);
            clearAllArticleDocCaches();
            clearAllLibraryListCaches();
        }
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
