package com.baixingai.voicedrop.ui;

import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.data.CommunityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure presentation rules shared by the Android community feed UI and JVM tests. */
public final class CommunityFeedPresentation {
    public enum Tab { RECOMMENDED, LATEST, REPLIES }

    private CommunityFeedPresentation() {}

    public static List<CommunityStore.Post> posts(CommunityStore.Feed feed, Tab tab) {
        if (feed == null) return new ArrayList<>();
        if (tab == Tab.LATEST) return new ArrayList<>(feed.latest);
        if (tab == Tab.REPLIES) {
            List<CommunityStore.Post> replies = new ArrayList<>();
            for (CommunityStore.Post post : feed.recommended) {
                if (post.replyTo != null && !post.replyTo.isEmpty()) replies.add(post);
            }
            return replies;
        }
        return new ArrayList<>(feed.recommended);
    }

    /** Local search over the already-loaded feed, matching title/author/preview. */
    public static List<CommunityStore.Post> search(List<CommunityStore.Post> posts, String query) {
        List<CommunityStore.Post> source = posts == null ? new ArrayList<>() : posts;
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return new ArrayList<>(source);
        List<CommunityStore.Post> result = new ArrayList<>();
        for (CommunityStore.Post post : source) {
            if (contains(post.title, needle) || contains(post.author, needle) || contains(post.preview, needle)) {
                result.add(post);
            }
        }
        return result;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    public static int paletteIndex(String shareId) {
        int hash = 0;
        String value = shareId == null ? "" : shareId;
        for (int i = 0; i < value.length(); i++) hash = (hash * 31 + value.charAt(i)) & 0xffff;
        return hash % 3;
    }

    public static List<String> ids(List<CommunityStore.Post> posts) {
        List<String> ids = new ArrayList<>();
        for (CommunityStore.Post post : posts) ids.add(post.shareId);
        return ids;
    }

    /**
     * The recommendation feed also contains published books.  They do not have a
     * community-post snapshot to open: their share id is the stable book route.
     */
    public static BookShelfIndex.Book book(CommunityStore.Post post) {
        if (post == null || !"book".equals(post.kind) || post.shareId == null
                || !post.shareId.startsWith("book-")) return null;
        String slug = post.shareId.substring("book-".length());
        if (!slug.matches("[A-Za-z0-9_-]+")) return null;
        String title = post.title == null || post.title.isEmpty() ? slug : post.title;
        return new BookShelfIndex.Book(slug, title, title,
                post.preview == null ? "" : post.preview, "#8A7A5A", "#6E5F44",
                post.coverPhotoKey != null && !post.coverPhotoKey.isEmpty(), Math.max(0, post.count),
                post.author, 0L, 0L, false, false);
    }
}
