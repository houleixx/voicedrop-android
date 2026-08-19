package com.baixingai.voicedrop.core;

/** Pure policy shared by Android book-cover loading and its JVM tests. */
public final class BookCoverPolicy {
    private static final long[] RETRY_DELAYS_MS = {0L, 2_000L, 4_000L, 8_000L};

    private BookCoverPolicy() {}

    public static String cacheKey(String slug, long coverAt) {
        return (slug == null ? "" : slug) + "-" + Math.max(0L, coverAt);
    }

    public static long[] retryDelaysMs() {
        return RETRY_DELAYS_MS.clone();
    }
}
