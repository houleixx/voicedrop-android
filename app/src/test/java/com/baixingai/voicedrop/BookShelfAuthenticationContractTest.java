package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level guard for the home book-shelf panel. */
public final class BookShelfAuthenticationContractTest {
    @Test public void shelfRequestsUseTheCurrentBearerAndRejectStaleAccountResponses() throws Exception {
        String panel = read("ui/BooksShelfPanel.java");

        assertTrue(panel.contains("String bearer = auth.bearer()"));
        assertTrue(panel.contains("/books/?format=json\", bearer"));
        assertTrue(panel.contains("requestCache.matches(auth.libraryCacheIdentity())"));
    }

    @Test public void shelfCacheIsScopedAndLegacySharedSnapshotIsNotMigrated() throws Exception {
        String cache = read("data/BookShelfCache.java");
        String key = read("core/AccountScopedCacheKey.java");

        assertTrue(cache.contains("AccountScopedCacheKey.create"));
        assertTrue(cache.contains("remove(LEGACY_UNSCOPED_KEY)"));
        assertFalse(cache.contains("putString(LEGACY_UNSCOPED_KEY"));
        assertTrue(key.contains("MessageDigest.getInstance(\"SHA-256\")"));
    }

    @Test public void shelfCoversRenderTheHiddenBadgeAboveCoverArt() throws Exception {
        String panel = read("ui/BooksShelfPanel.java");

        assertTrue(panel.contains("if (book.hidden) addHiddenBadge(cover)"));
        assertTrue(panel.contains("text(\"隐藏\", 10"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
