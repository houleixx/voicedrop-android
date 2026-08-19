package com.baixingai.voicedrop.net;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent CN/CF route selection behind a deliberately small interface.
 * Callers only read {@link #currentHost()}; probing, throttling and concurrency stay here.
 */
public final class ApiRoute {
    static final long PROBE_MAX_AGE_MS = 30L * 60L * 1000L;
    private static final int PROBE_TIMEOUT_MS = 6_000;
    private static final String PREFS = "voicedrop.api-route";
    private static final String HOST_KEY = "host";
    private static final String PROBED_AT_KEY = "probedAt";
    private static final ExecutorService CONTROL = Executors.newSingleThreadExecutor();
    private static final ExecutorService MEASURE = Executors.newFixedThreadPool(2);
    private static final AtomicBoolean PROBING = new AtomicBoolean();

    private static volatile SharedPreferences prefs;
    private static volatile String currentHost = Api.CN_HOST;

    private ApiRoute() {}

    public static synchronized void initialize(Context context) {
        if (prefs != null) return;
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        currentHost = Api.CF_HOST.equals(prefs.getString(HOST_KEY, null))
                ? Api.CF_HOST : Api.CN_HOST;
    }

    public static String currentHost() {
        return currentHost;
    }

    /** Cold start passes force=true; foreground resumes use the persisted 30-minute throttle. */
    public static void probe(boolean force) {
        SharedPreferences store = prefs;
        if (store == null) return;
        long now = System.currentTimeMillis();
        if (!force && now - store.getLong(PROBED_AT_KEY, 0L) <= PROBE_MAX_AGE_MS) return;
        if (!PROBING.compareAndSet(false, true)) return;
        CONTROL.execute(() -> runProbe(store));
    }

    private static void runProbe(SharedPreferences store) {
        try {
            Future<Long> cn = MEASURE.submit(() -> measure("https://" + Api.CN_HOST + "/"));
            Future<Long> cf = MEASURE.submit(() -> measure("https://" + Api.CF_HOST + "/voicedrop/"));
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROBE_TIMEOUT_MS);
            Long cnMs = result(cn, deadline);
            Long cfMs = result(cf, deadline);
            String winner = ApiRoutePolicy.pick(currentHost, cnMs, cfMs);
            currentHost = winner;
            store.edit()
                    .putString(HOST_KEY, winner)
                    .putLong(PROBED_AT_KEY, System.currentTimeMillis())
                    .apply();
        } finally {
            PROBING.set(false);
        }
    }

    private static Long result(Future<Long> future, long deadline) {
        try {
            long remaining = Math.max(1L, deadline - System.nanoTime());
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (Exception ignored) {
            future.cancel(true);
            return null;
        }
    }

    private static Long measure(String value) {
        HttpURLConnection connection = null;
        long startedAt = System.nanoTime();
        try {
            connection = (HttpURLConnection) new URL(value).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(PROBE_TIMEOUT_MS);
            connection.setReadTimeout(PROBE_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("X-VD-Platform", "android");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;
            return (System.nanoTime() - startedAt) / 1_000_000L;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
