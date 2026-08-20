package com.baixingai.voicedrop.core;

/** Decides when the shelf must hide all content behind its initial loading state. */
public final class BookShelfLoadingPolicy {
    private BookShelfLoadingPolicy() {}

    public static boolean shouldShowExclusiveLoading(boolean loading, int cachedBookCount) {
        return loading && cachedBookCount <= 0;
    }
}
