package com.baixingai.voicedrop.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

public final class PhotoService {
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(128 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : value.getByteCount();
        }
    };

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

    public static byte[] data(String fullKey, boolean ignoringLocalCache, HttpClient http) throws Exception {
        if (fullKey == null || fullKey.isEmpty()) return null;
        HttpClient.RequestOptions options = ignoringLocalCache
                ? new HttpClient.RequestOptions().header("Cache-Control", "no-cache")
                : null;
        HttpClient.Response response = http.get(Api.filesBase() + "/photo/" + Api.path(fullKey), "", options);
        return response.ok() ? response.body : null;
    }
}
