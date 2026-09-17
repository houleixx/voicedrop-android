package com.baixingai.voicedrop.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared shelf filtering and substring search; the shelf remains the visibility authority. */
public final class BookShelfSearch {
    public static final String ALL = "全部", MINE = "我的";
    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "商业", "投资", "AI", "科学", "人文", "身心", "生活", "故事"));
    private BookShelfSearch() {}

    public static List<String> filters(List<BookShelfIndex.Book> books) {
        List<String> result = new ArrayList<>(Arrays.asList(ALL, MINE));
        for (String category : CATEGORIES) {
            for (BookShelfIndex.Book book : books) {
                if (category.equals(book.category)) { result.add(category); break; }
            }
        }
        return result;
    }

    /** null means invalid payload (retryable), while an empty map is a valid empty index. */
    public static Map<String, Entry> parse(String raw) {
        try {
            JSONArray rows = new JSONObject(raw).optJSONArray("books");
            if (rows == null) return null;
            Map<String, Entry> result = new LinkedHashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String slug = string(row, "slug").trim();
                if (slug.isEmpty() || result.containsKey(slug)) continue;
                List<Chapter> chapters = new ArrayList<>();
                JSONArray toc = row.optJSONArray("toc");
                if (toc != null) for (int j = 0; j < toc.length(); j++) {
                    JSONObject chapter = toc.optJSONObject(j);
                    if (chapter != null) chapters.add(new Chapter(string(chapter, "t"), string(chapter, "b")));
                }
                result.put(slug, new Entry(string(row, "sub"), string(row, "intro"), chapters));
            }
            return result;
        } catch (Exception ignored) { return null; }
    }

    private static String string(JSONObject object, String key) {
        return object.opt(key) instanceof String ? object.optString(key) : "";
    }

    public static String query(String value) { return value == null ? "" : value.trim(); }

    /** null = miss, empty = metadata hit, otherwise first matching chapter title. */
    public static String hit(BookShelfIndex.Book book, String query, Entry entry) {
        String q = query(query).toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return "";
        for (String field : Arrays.asList(book.title, book.main, book.sub, book.author, book.category)) {
            if (contains(field, q)) return "";
        }
        if (entry == null) return null;
        if (contains(entry.sub, q) || contains(entry.intro, q)) return "";
        for (Chapter chapter : entry.toc) {
            if (contains(chapter.title, q) || contains(chapter.brief, q)) return chapter.title;
        }
        return null;
    }

    private static boolean contains(String field, String q) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(q);
    }

    public static List<Match> select(List<BookShelfIndex.Book> books, String filter, String query,
                                     Map<String, Entry> index) {
        List<Match> result = new ArrayList<>();
        for (BookShelfIndex.Book book : books) {
            if (MINE.equals(filter) ? !book.mine : !ALL.equals(filter) && !filter.equals(book.category)) continue;
            String hit = hit(book, query, index == null ? null : index.get(book.slug));
            if (hit != null) result.add(new Match(book, hit));
        }
        return result;
    }

    public static final class Entry {
        public final String sub, intro;
        public final List<Chapter> toc;
        Entry(String sub, String intro, List<Chapter> toc) { this.sub = sub; this.intro = intro; this.toc = toc; }
    }
    public static final class Chapter {
        public final String title, brief;
        Chapter(String title, String brief) { this.title = title; this.brief = brief; }
    }
    public static final class Match {
        public final BookShelfIndex.Book book;
        public final String chapter;
        Match(BookShelfIndex.Book book, String chapter) { this.book = book; this.chapter = chapter; }
    }
}
