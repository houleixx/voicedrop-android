package com.baixingai.voicedrop.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Permanent owner-only conversation returned by GET /api/book/history. */
public final class BookReviseThread {
    public final String slug;
    public final String author;
    public final boolean running;
    public final List<Entry> entries;

    private BookReviseThread(String slug, String author, boolean running, List<Entry> entries) {
        this.slug = slug;
        this.author = author;
        this.running = running;
        this.entries = Collections.unmodifiableList(entries);
    }

    public static BookReviseThread parse(String raw) {
        try {
            JSONObject body = new JSONObject(raw == null ? "{}" : raw);
            List<Entry> entries = new ArrayList<>();
            JSONArray thread = body.optJSONArray("thread");
            if (thread != null) {
                for (int index = 0; index < thread.length(); index++) {
                    JSONObject item = thread.optJSONObject(index);
                    if (item == null) continue;
                    String instruction = item.optString("instruction", "").trim();
                    if (instruction.isEmpty()) continue;
                    entries.add(new Entry(
                            item.optDouble("ts", 0),
                            item.optString("kind", "revise"),
                            instruction,
                            item.optString("status", "done"),
                            nullableString(item, "reply"),
                            nullableString(item, "error")));
                }
            }
            boolean running = body.optBoolean("running", false);
            if (!running) {
                for (Entry entry : entries) {
                    if (entry.running()) { running = true; break; }
                }
            }
            return new BookReviseThread(body.optString("slug", ""),
                    nullableString(body, "author"), running, entries);
        } catch (Exception ignored) {
            return new BookReviseThread("", null, false, new ArrayList<>());
        }
    }

    private static String nullableString(JSONObject object, String key) {
        if (object.isNull(key)) return null;
        String value = object.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    public static final class Entry {
        public final double timestampMs;
        public final String kind;
        public final String instruction;
        public final String status;
        public final String reply;
        public final String error;

        public Entry(double timestampMs, String kind, String instruction, String status,
                     String reply, String error) {
            this.timestampMs = timestampMs;
            this.kind = kind;
            this.instruction = instruction;
            this.status = status;
            this.reply = reply;
            this.error = error;
        }

        public boolean creation() { return "create".equals(kind); }
        public boolean running() { return "running".equals(status); }
        public boolean failed() { return "failed".equals(status); }
    }
}
