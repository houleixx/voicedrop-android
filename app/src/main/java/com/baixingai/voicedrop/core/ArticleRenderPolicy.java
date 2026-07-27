package com.baixingai.voicedrop.core;

import com.baixingai.voicedrop.data.ArticleDoc;

/**
 * Prevents equivalent cache, HTTP, and WebSocket snapshots from rebuilding the
 * entire detail view while still allowing any document change to render.
 */
public final class ArticleRenderPolicy {
    private ArticleRenderPolicy() {}

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
