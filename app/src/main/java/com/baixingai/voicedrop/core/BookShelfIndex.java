package com.baixingai.voicedrop.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Public /books/?format=json contract shared with the iOS shelf. */
public final class BookShelfIndex {
    private BookShelfIndex() {}

    public static List<Book> parse(String raw) {
        List<Book> out = new ArrayList<>();
        try {
            JSONArray books = new JSONObject(raw == null ? "{}" : raw).optJSONArray("books");
            if (books == null) return out;
            for (int i = 0; i < books.length(); i++) {
                JSONObject item = books.optJSONObject(i);
                if (item == null) continue;
                String slug = item.optString("slug", "").trim();
                if (slug.isEmpty()) continue;
                String title = item.optString("title", "");
                out.add(new Book(slug, title, item.optString("main", title.isEmpty() ? "未命名" : title),
                        item.optString("sub", ""), item.optString("c", "#8B6652"),
                        item.optString("c2", "#4B342C"), item.optBoolean("cover", false),
                        Math.max(0, item.optInt("chapters", 0)), item.optString("author", ""),
                        Math.max(0L, item.optLong("createdAt", 0L))));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static final class Book {
        public final String slug, title, main, sub, c, c2, author;
        public final boolean cover;
        public final int chapters;
        public final long createdAt;
        public Book(String slug, String title, String main, String sub, String c, String c2,
                    boolean cover, int chapters, String author, long createdAt) {
            this.slug = slug; this.title = title; this.main = main; this.sub = sub;
            this.c = c; this.c2 = c2; this.cover = cover; this.chapters = chapters;
            this.author = author == null ? "" : author; this.createdAt = createdAt;
        }
        public String readerUrl() { return "https://voicedrop.cn/books/" + slug + "/"; }
        public String coverUrl() { return readerUrl() + "cover.jpg"; }
    }
}
