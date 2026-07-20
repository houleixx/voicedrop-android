package com.baixingai.voicedrop.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
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
    private static volatile File diskDir;

    private PhotoService() {}

    /** Mirrors iOS Caches/photo-cache so immutable community covers survive cold starts. */
    public static synchronized void configure(Context context) {
        if (diskDir != null || context == null) return;
        File dir = new File(context.getApplicationContext().getCacheDir(), "photo-cache");
        if (!dir.exists()) dir.mkdirs();
        diskDir = dir;
        trimDiskCache(dir);
    }

    public static Bitmap image(String fullKey, boolean ignoringLocalCache) throws Exception {
        return image(fullKey, ignoringLocalCache, new HttpClient());
    }

    public static Bitmap image(String fullKey, boolean ignoringLocalCache, HttpClient http) throws Exception {
        if (fullKey == null || fullKey.isEmpty()) return null;
        if (!ignoringLocalCache) {
            Bitmap hit = cache.get(fullKey);
            if (hit != null) return hit;
        }
        if (!ignoringLocalCache) {
            byte[] cached = readDisk(fullKey);
            if (cached != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(cached, 0, cached.length);
                if (bitmap != null) {
                    cache.put(fullKey, bitmap);
                    return bitmap;
                }
                deleteDisk(fullKey);
            }
        }
        byte[] data = data(fullKey, ignoringLocalCache, http);
        if (data == null) return null;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap != null) {
            writeDisk(fullKey, data);
            cache.put(fullKey, bitmap);
        }
        return bitmap;
    }

    /** Fetch a 512px Cloudflare edge thumbnail, then transparently fall back to the original. */
    public static Bitmap thumbnail(String fullKey) throws Exception {
        if (fullKey == null || fullKey.isEmpty()) return null;
        String cacheKey = fullKey + "#w512";
        Bitmap hit = cache.get(cacheKey);
        if (hit != null) return hit;
        byte[] cached = readDisk(cacheKey);
        if (cached != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(cached, 0, cached.length);
            if (bitmap != null) {
                cache.put(cacheKey, bitmap);
                return bitmap;
            }
            deleteDisk(cacheKey);
        }
        if (!missingThumbs.contains(cacheKey)) {
            try {
                String url = "https://" + Api.HOST
                        + "/cdn-cgi/image/width=512,quality=60/files/api/photo/" + Api.path(fullKey);
                HttpClient.Response response = new HttpClient().get(url, "",
                        new HttpClient.RequestOptions().readTimeoutMs(20_000));
                if (response.ok()) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(response.body, 0, response.body.length);
                    if (bitmap != null) {
                        writeDisk(cacheKey, response.body);
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

    private static byte[] readDisk(String cacheKey) {
        File file = diskFile(cacheKey);
        if (file == null || !file.isFile()) return null;
        try {
            return Files.readAllBytes(file.toPath());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static synchronized void writeDisk(String cacheKey, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        File target = diskFile(cacheKey);
        if (target == null || target.isFile()) return;
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            Files.write(temp.toPath(), bytes);
            if (!temp.renameTo(target) && !target.isFile()) Files.move(temp.toPath(), target.toPath());
        } catch (Exception ignored) {
            // Cache writes are best-effort; network rendering remains the fallback.
        } finally {
            if (temp.isFile()) temp.delete();
        }
    }

    private static void deleteDisk(String cacheKey) {
        File file = diskFile(cacheKey);
        if (file != null && file.isFile()) file.delete();
    }

    private static File diskFile(String cacheKey) {
        File dir = diskDir;
        if (dir == null || cacheKey == null || cacheKey.isEmpty()) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(cacheKey.getBytes("UTF-8"));
            StringBuilder name = new StringBuilder();
            for (byte value : hash) name.append(String.format("%02x", value));
            return new File(dir, name + ".img");
        } catch (Exception ignored) {
            return new File(dir, Integer.toHexString(cacheKey.hashCode()) + ".img");
        }
    }

    private static void trimDiskCache(File dir) {
        File[] files = dir.listFiles(file -> file.isFile() && file.getName().endsWith(".img"));
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= 512L * 1024 * 1024) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long target = 256L * 1024 * 1024;
        for (File file : files) {
            long size = file.length();
            if (file.delete()) total -= size;
            if (total <= target) break;
        }
    }
}
