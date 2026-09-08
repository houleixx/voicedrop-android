package com.baixingai.voicedrop.core;

import com.baixingai.voicedrop.data.ArticleDoc;

/**
 * Prevents equivalent cache, HTTP, and WebSocket snapshots from rebuilding the
 * entire detail view while still allowing any document change to render.
 */
public final class ArticleRenderPolicy {
    private ArticleRenderPolicy() {}

    /** Tracks what was painted, independently of the latest received document.
     * A value snapshot also detects in-place edits to a document's article list. */
    public static final class RenderedState {
        private String snapshot;
        private int articleIndex = -1;

        public boolean needsRender(ArticleDoc doc, int index) {
            return snapshot == null || articleIndex != index || !snapshot.equals(serialize(doc));
        }

        public void didRender(ArticleDoc doc, int index) {
            snapshot = serialize(doc);
            articleIndex = index;
        }

        public void clear() {
            snapshot = null;
            articleIndex = -1;
        }

        private String serialize(ArticleDoc doc) {
            try { return doc == null ? null : doc.toJson(); }
            catch (Exception ignored) { return null; }
        }
    }

    public static boolean shouldRebuild(ArticleDoc current, ArticleDoc updated) {
        if (current == updated) return false;
        if (current == null || updated == null) return true;
        try {
            return !current.toJson().equals(updated.toJson());
        } catch (Exception ignored) {
            return true;
        }
    }
}
