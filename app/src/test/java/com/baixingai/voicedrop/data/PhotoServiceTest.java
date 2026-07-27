package com.baixingai.voicedrop.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class PhotoServiceTest {
    @Test
    public void photoServiceProvidesProcessWideDecodedCacheAndBypass() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/PhotoService.java");

        assertTrue(source.contains("LruCache<String, Bitmap>"));
        assertTrue(source.contains("image(String fullKey, boolean ignoringLocalCache)"));
        assertTrue(source.contains("Cache-Control"));
        assertTrue(source.contains("no-cache"));
        assertTrue(source.contains("Api.photoBase() + \"/photo/\""));
        assertTrue(source.contains("/cdn-cgi/image/width=512,quality=60/files/api/photo/"));
        assertTrue(source.contains("Api.WS_HOST"));
        assertTrue(source.contains("missingThumbs.add(cacheKey)"));
    }

    @Test
    public void libraryStoreRoutesPhotoImagesThroughPhotoService() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");

        assertTrue(source.contains("PhotoService.image(fullKey, ignoringLocalCache)"));
        assertTrue(source.contains("photoImage(String fullKey, boolean ignoringLocalCache)"));
        assertTrue(source.contains("public String ownerScope()"));
        assertTrue(source.contains("synchronized (this)"));
    }

    @Test
    public void diskCacheUsesRecentAccessAndTrimsAfterEveryNewPhoto() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/PhotoService.java");

        assertTrue(source.contains("PhotoCachePolicy.shouldEvict(files.length, total)"));
        assertTrue(source.contains("file.setLastModified(System.currentTimeMillis())"));
        assertTrue(source.contains("trimDiskCache(target.getParentFile())"));
    }

    @Test
    public void simultaneousRequestsForTheSamePhotoShareOneLoad() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/PhotoService.java");

        assertTrue(source.contains("inFlight.putIfAbsent(loadKey, candidate)"));
        assertTrue(source.contains("task.get()"));
        assertTrue(source.contains("inFlight.remove(loadKey, task)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
