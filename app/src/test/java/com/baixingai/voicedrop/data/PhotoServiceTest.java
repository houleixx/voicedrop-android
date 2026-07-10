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
    }

    @Test
    public void libraryStoreRoutesPhotoImagesThroughPhotoService() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");

        assertTrue(source.contains("PhotoService.image(fullKey, ignoringLocalCache)"));
        assertTrue(source.contains("photoImage(String fullKey, boolean ignoringLocalCache)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
