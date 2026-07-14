package com.baixingai.voicedrop.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PhotoService {
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(128 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : value.getByteCount();
        }
    };
    private static final Set<String> missingThumbs = Collections.synchronizedSet(new HashSet<>());

    private PhotoService() {}

    public static Bitmap image(String fullKey, boolean ignoringLocalCache) throws Exception {
        return image(fullKey, ignoringLocalCache, new HttpClient());
    }

    public static Bitmap image(String fullKey, boolean ignoringLocalCache, HttpClient http) throws Exception {
        if (fullKey == null || fullKey.isEmpty()) return null;
        if (!ignoringLocalCache) {
            Bitmap hit = cache.get(fullKey);
            if (hit != null) return hit;
        }
        byte[] data = data(fullKey, ignoringLocalCache, http);
        if (data == null) return null;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap != null) cache.put(fullKey, bitmap);
        return bitmap;
    }

    /** Fetch a 512px Cloudflare edge thumbnail, then transparently fall back to the original. */
    public static Bitmap thumbnail(String fullKey) throws Exception {
        if (fullKey == null || fullKey.isEmpty()) return null;
        String cacheKey = fullKey + "#w512";
        Bitmap hit = cache.get(cacheKey);
        if (hit != null) return hit;
        if (!missingThumbs.contains(cacheKey)) {
            try {
                String url = "https://" + Api.HOST
                        + "/cdn-cgi/image/width=512,quality=60/files/api/photo/" + Api.path(fullKey);
                HttpClient.Response response = new HttpClient().get(url, "",
                        new HttpClient.RequestOptions().readTimeoutMs(20_000));
                if (response.ok()) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(response.body, 0, response.body.length);
                    if (bitmap != null) {
                        cache.put(cacheKey, bitmap);
                        return bitmap;
                    }
                }
            } catch (Exception ignored) {
                // A transform outage must never suppress the original public photo.
            }
            missingThumbs.add(cacheKey);
        }
        return image(fullKey, false);
    }

    public static byte[] data(String fullKey, boolean ignoringLocalCache, HttpClient http) throws Exception {
        if (fullKey == null || fullKey.isEmpty()) return null;
        HttpClient.RequestOptions options = ignoringLocalCache
                ? new HttpClient.RequestOptions().header("Cache-Control", "no-cache")
                : null;
        HttpClient.Response response = http.get(Api.filesBase() + "/photo/" + Api.path(fullKey), "", options);
        return response.ok() ? response.body : null;
    }
}
