package com.baixingai.voicedrop.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Auth-aware /books/?format=json contract shared with the iOS shelf. */
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
                        Math.max(0L, item.optLong("createdAt", 0L)),
                        Math.max(0L, item.optLong("coverAt", 0L)),
                        item.optBoolean("hidden", false), item.optBoolean("mine", false)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static final class Book {
        public final String slug, title, main, sub, c, c2, author;
        public final boolean cover, hidden, mine;
        public final int chapters;
        public final long createdAt, coverAt;
        public Book(String slug, String title, String main, String sub, String c, String c2,
                    boolean cover, int chapters, String author, long createdAt, long coverAt,
                    boolean hidden, boolean mine) {
            this.slug = slug; this.title = title; this.main = main; this.sub = sub;
            this.c = c; this.c2 = c2; this.cover = cover; this.chapters = chapters;
            this.author = author == null ? "" : author; this.createdAt = createdAt;
            this.coverAt = coverAt; this.hidden = hidden; this.mine = mine;
        }
        public String readerUrl(String publicWebBase) {
            return publicWebBase + "/books/" + slug + "/";
        }
        public String coverUrl(String publicWebBase) {
            return readerUrl(publicWebBase) + "cover.jpg" + (coverAt > 0 ? "?v=" + coverAt : "");
        }
    }
}
