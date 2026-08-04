package com.baixingai.voicedrop.core;

/** Storage limits for the persistent article-photo cache. */
public final class PhotoCachePolicy {
    public static final int MAX_FILES = 300;
    public static final long MAX_BYTES = 512L * 1024 * 1024;

    private PhotoCachePolicy() {}

    public static boolean shouldEvict(int fileCount, long totalBytes) {
        return fileCount > MAX_FILES || totalBytes > MAX_BYTES;
    }
}
