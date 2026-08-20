package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BookShelfLoadingPolicyTest {
    @Test public void firstLoadWithoutCacheUsesExclusiveLoadingState() {
        assertTrue(BookShelfLoadingPolicy.shouldShowExclusiveLoading(true, 0));
    }

    @Test public void cachedBooksRemainVisibleDuringBackgroundRefresh() {
        assertFalse(BookShelfLoadingPolicy.shouldShowExclusiveLoading(true, 2));
    }

    @Test public void completedEmptyLoadShowsNormalShelf() {
        assertFalse(BookShelfLoadingPolicy.shouldShowExclusiveLoading(false, 0));
    }
}
