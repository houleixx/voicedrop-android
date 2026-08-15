package com.baixingai.voicedrop.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

/** Sizes and clears the explicit set of disposable VoiceDrop content caches. */
public final class CacheManager {
    static final String[] CACHE_DIRECTORY_NAMES = {
            "photo-cache",
            "article-doc-cache-v1",
            "community-post-cache-v1"
    };

    private final File cacheRoot;
    private final Runnable memoryCacheClearer;

    public CacheManager(File cacheRoot, Runnable memoryCacheClearer) {
        if (cacheRoot == null) throw new IllegalArgumentException("cacheRoot == null");
        this.cacheRoot = canonical(cacheRoot);
        this.memoryCacheClearer = memoryCacheClearer == null ? () -> { } : memoryCacheClearer;
    }

    public long sizeBytes() {
        long total = 0L;
        for (String name : CACHE_DIRECTORY_NAMES) {
            File directory = allowedDirectory(name);
            if (directory != null) total += sizeWithin(directory, directory);
        }
        return Math.max(0L, total);
    }

    public ClearResult clear() {
        long before = sizeBytes();
        int failures = 0;
        for (String name : CACHE_DIRECTORY_NAMES) {
            File directory = allowedDirectory(name);
            if (directory != null && directory.exists()) {
                failures += clearChildren(directory, directory);
            }
        }
        memoryCacheClearer.run();
        long after = sizeBytes();
        return new ClearResult(before, after, failures);
    }

    public static String formatBytes(long bytes) {
        long safeBytes = Math.max(0L, bytes);
        if (safeBytes < 1024L) return safeBytes + " B";
        double value = safeBytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do {
            value /= 1024d;
            unit++;
        } while (value >= 1024d && unit < units.length - 1);
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    private File allowedDirectory(String name) {
        File lexicalCandidate = new File(cacheRoot, name).getAbsoluteFile();
        if (isSymbolicLink(lexicalCandidate)) return null;
        File candidate = canonical(lexicalCandidate);
        if (!isWithin(cacheRoot, candidate)) return null;
        File parent = candidate.getParentFile();
        return parent != null && canonical(parent).equals(cacheRoot) ? candidate : null;
    }

    private static long sizeWithin(File allowedRoot, File file) {
        if (!file.exists() || !isWithin(allowedRoot, file) || isSymbolicLink(file)) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        File[] children = file.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) {
            long childSize = sizeWithin(allowedRoot, child);
            if (Long.MAX_VALUE - total < childSize) return Long.MAX_VALUE;
            total += childSize;
        }
        return total;
    }

    /** Clears contents but preserves each cache root so active cache writers remain valid. */
    private static int clearChildren(File allowedRoot, File directory) {
        if (!isWithin(allowedRoot, directory) || isSymbolicLink(directory)) return 1;
        File[] children = directory.listFiles();
        if (children == null) return directory.isDirectory() ? 0 : 1;
        int failures = 0;
        for (File child : children) failures += deleteWithin(allowedRoot, child);
        return failures;
    }

    private static int deleteWithin(File allowedRoot, File file) {
        if (!isWithin(allowedRoot, file)) return 1;
        if (isSymbolicLink(file)) return file.delete() ? 0 : 1;
        int failures = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return 1;
            for (File child : children) failures += deleteWithin(allowedRoot, child);
        }
        if (!file.delete()) failures++;
        return failures;
    }

    private static boolean isWithin(File root, File candidate) {
        File safeRoot = canonical(root);
        File safeCandidate = canonical(candidate);
        String prefix = safeRoot.getPath() + File.separator;
        return safeCandidate.equals(safeRoot) || safeCandidate.getPath().startsWith(prefix);
    }

    private static boolean isSymbolicLink(File file) {
        try {
            return Files.isSymbolicLink(file.toPath());
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException ignored) {
            return file.getAbsoluteFile();
        }
    }

    public static final class ClearResult {
        public final long beforeBytes;
        public final long afterBytes;
        public final int failureCount;

        ClearResult(long beforeBytes, long afterBytes, int failureCount) {
            this.beforeBytes = beforeBytes;
            this.afterBytes = afterBytes;
            this.failureCount = failureCount;
        }

        public long clearedBytes() {
            return Math.max(0L, beforeBytes - afterBytes);
        }

        public boolean succeeded() {
            return failureCount == 0;
        }
    }
}
