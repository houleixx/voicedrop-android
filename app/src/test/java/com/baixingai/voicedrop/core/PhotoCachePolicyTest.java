package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhotoCachePolicyTest {
    @Test
    public void diskCacheKeepsUpToThreeHundredRecentlyUsedPhotos() {
        assertFalse(PhotoCachePolicy.shouldEvict(300, 100L * 1024 * 1024));
        assertTrue(PhotoCachePolicy.shouldEvict(301, 100L * 1024 * 1024));
    }

    @Test
    public void diskCacheStillHasAStorageSafetyLimit() {
        assertFalse(PhotoCachePolicy.shouldEvict(80, 512L * 1024 * 1024));
        assertTrue(PhotoCachePolicy.shouldEvict(80, 512L * 1024 * 1024 + 1));
    }
}
