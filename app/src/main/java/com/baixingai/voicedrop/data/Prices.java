package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONObject;

/**
 * Server-owned book prices. The cache is deliberately only a display and local
 * upsell hint: a POST that returns 402 remains the authority for charging.
 */
public final class Prices {
    public static final long TTL_MS = 24L * 60 * 60 * 1000;
    public static final Table FALLBACK = new Table(160, 40, 0);
    private static final String PREFS = "voicedrop.prices.v1";
    private static final String BOOK = "book";
    private static final String BOOK_REVISE = "book_revise";
    private static final String FETCHED_AT = "fetched_at";

    private Prices() {}

    public static Table current(Context context) {
        Table cached = load(context);
        return cached == null ? FALLBACK : cached;
    }

    /** Fetches only when the local value is older than a day; callers run this off the UI thread. */
    public static Table refreshIfNeeded(Context context, HttpClient http) {
        long now = System.currentTimeMillis();
        Table cached = load(context);
        Table previous = cached == null ? FALLBACK : cached;
        if (!isStale(cached, now)) return previous;
        try {
            // This endpoint is public. Do not create an anonymous identity just to read prices.
            HttpClient.Response response = http.get(Api.agentBase() + "/usage/prices", "");
            if (!response.ok()) return previous;
            Table next = merge(response.text(), previous, now);
            if (next != null) save(context, next);
            return next == null ? previous : next;
        } catch (Exception ignored) {
            return previous;
        }
    }

    static boolean isStale(Table table, long now) {
        return table == null || now - table.fetchedAtMs > TTL_MS;
    }

    /** Mirrors iOS: book is required; a malformed optional revise price keeps the prior valid value. */
    static Table merge(String json, Table previous, long now) {
        try {
            JSONObject remote = new JSONObject(json == null ? "{}" : json);
            int book = remote.optInt(BOOK, 0);
            if (book <= 0) return null;
            int revise = remote.optInt(BOOK_REVISE, 0);
            return new Table(book, revise > 0 ? revise : previous.bookRevise, now);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Table load(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int book = prefs.getInt(BOOK, 0);
        int revise = prefs.getInt(BOOK_REVISE, 0);
        if (book <= 0 || revise <= 0) return null;
        return new Table(book, revise, prefs.getLong(FETCHED_AT, 0));
    }

    private static void save(Context context, Table table) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(BOOK, table.book).putInt(BOOK_REVISE, table.bookRevise)
                .putLong(FETCHED_AT, table.fetchedAtMs).apply();
    }

    public static final class Table {
        public final int book;
        public final int bookRevise;
        public final long fetchedAtMs;

        public Table(int book, int bookRevise, long fetchedAtMs) {
            this.book = book;
            this.bookRevise = bookRevise;
            this.fetchedAtMs = fetchedAtMs;
        }
    }
}
