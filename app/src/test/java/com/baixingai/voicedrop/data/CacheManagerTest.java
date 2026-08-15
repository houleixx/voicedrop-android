package com.baixingai.voicedrop.data;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CacheManagerTest {
    private Path temporaryRoot;

    @Before
    public void setUp() throws Exception {
        temporaryRoot = Files.createTempDirectory("voicedrop-cache-manager-test");
    }

    @After
    public void tearDown() throws Exception {
        deleteTemporaryTree(temporaryRoot);
    }

    @Test
    public void sizesAndClearsOnlyWhitelistedContentCaches() throws Exception {
        write("photo-cache/a.img", "photo");
        write("article-doc-cache-v1/account/article.json", "article");
        write("community-post-cache-v1/post.json", "post");
        write("exports/export.zip", "keep-export");
        write("updates/update.apk", "keep-update");
        write("insert_photos/pending.jpg", "keep-pending");

        AtomicInteger memoryClears = new AtomicInteger();
        CacheManager manager = new CacheManager(temporaryRoot.toFile(), memoryClears::incrementAndGet);
        long expected = bytes("photo") + bytes("article") + bytes("post");

        assertEquals(expected, manager.sizeBytes());
        CacheManager.ClearResult result = manager.clear();

        assertTrue(result.succeeded());
        assertEquals(expected, result.clearedBytes());
        assertEquals(0L, manager.sizeBytes());
        assertEquals(1, memoryClears.get());
        assertTrue(Files.exists(temporaryRoot.resolve("photo-cache")));
        assertTrue(Files.exists(temporaryRoot.resolve("exports/export.zip")));
        assertTrue(Files.exists(temporaryRoot.resolve("updates/update.apk")));
        assertTrue(Files.exists(temporaryRoot.resolve("insert_photos/pending.jpg")));
    }

    @Test
    public void symlinkCannotEscapeTheCacheRoot() throws Exception {
        Path outside = Files.createTempDirectory("voicedrop-cache-manager-outside");
        try {
            Path protectedFile = outside.resolve("recording.m4a");
            Files.write(protectedFile, "recording".getBytes(StandardCharsets.UTF_8));
            try {
                Files.createSymbolicLink(temporaryRoot.resolve("photo-cache"), outside);
            } catch (UnsupportedOperationException | SecurityException e) {
                return;
            }

            CacheManager manager = new CacheManager(temporaryRoot.toFile(), null);
            assertEquals(0L, manager.sizeBytes());
            manager.clear();
            assertTrue(Files.exists(protectedFile));
        } finally {
            deleteTemporaryTree(outside);
        }
    }

    @Test
    public void whitelistedNameCannotSymlinkToAnExcludedCacheSibling() throws Exception {
        write("exports/export.zip", "keep-export");
        try {
            Files.createSymbolicLink(temporaryRoot.resolve("photo-cache"),
                    temporaryRoot.resolve("exports"));
        } catch (UnsupportedOperationException | SecurityException e) {
            return;
        }

        CacheManager manager = new CacheManager(temporaryRoot.toFile(), null);
        assertEquals(0L, manager.sizeBytes());
        manager.clear();

        assertTrue(Files.exists(temporaryRoot.resolve("exports/export.zip")));
    }

    @Test
    public void formatsByteCountsForSettings() {
        assertEquals("0 B", CacheManager.formatBytes(-1));
        assertEquals("1023 B", CacheManager.formatBytes(1023));
        assertEquals("1.0 KB", CacheManager.formatBytes(1024));
        assertEquals("1.5 MB", CacheManager.formatBytes(1572864));
        assertEquals("2.0 GB", CacheManager.formatBytes(2147483648L));
    }

    @Test
    public void whitelistCannotBeExpandedByFilesystemContents() throws Exception {
        write("filesDir/recording.m4a", "recording");
        write("shared_prefs/auth.xml", "token");
        CacheManager manager = new CacheManager(temporaryRoot.toFile(), null);

        manager.clear();

        assertFalse(Files.notExists(temporaryRoot.resolve("filesDir/recording.m4a")));
        assertFalse(Files.notExists(temporaryRoot.resolve("shared_prefs/auth.xml")));
    }

    private void write(String relative, String value) throws Exception {
        Path target = temporaryRoot.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.write(target, value.getBytes(StandardCharsets.UTF_8));
    }

    private static long bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void deleteTemporaryTree(Path path) throws Exception {
        if (path == null) return;
        if (Files.isSymbolicLink(path)) {
            Files.deleteIfExists(path);
            return;
        }
        if (Files.notExists(path)) return;
        if (!Files.isDirectory(path)) {
            Files.deleteIfExists(path);
            return;
        }
        try (java.util.stream.Stream<Path> children = Files.list(path)) {
            for (Path child : (Iterable<Path>) children::iterator) deleteTemporaryTree(child);
        }
        Files.deleteIfExists(path);
    }
}
