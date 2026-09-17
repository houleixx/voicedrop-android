package com.baixingai.voicedrop.core;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public final class BookShelfSearchStateTest {
    @Test public void inFlightTypingAndSuccessfulCacheDoNotFetchAgain() {
        BookShelfSearchState state = new BookShelfSearchState();
        long request = state.begin(100);
        assertTrue(state.loading());
        assertEquals(-1, state.begin(200));
        assertTrue(state.complete(request, Collections.emptyMap(), 300));
        assertFalse(state.loading());
        assertEquals(-1, state.begin(400));
        assertNotNull(state.index());
    }

    @Test public void failedOrMalformedResponseRemainsRetryableWithBackoff() {
        BookShelfSearchState state = new BookShelfSearchState();
        long request = state.begin(100);
        assertTrue(state.complete(request, null, 200));
        assertTrue(state.failed());
        assertNull(state.index());
        assertEquals(-1, state.begin(300));
        assertEquals(1900, state.retryDelay(300));
        long retry = state.begin(2200);
        assertTrue(retry >= 0);
        assertFalse(state.failed());
        assertTrue(state.complete(retry, Collections.emptyMap(), 2300));
    }

    @Test public void accountSwitchAndRefreshRejectOldCallbacksWithoutClearingCurrentRequest() {
        BookShelfSearchState state = new BookShelfSearchState();
        long accountA = state.begin(100);
        state.invalidate();
        long accountB = state.begin(200);
        assertFalse(state.complete(accountA, Collections.emptyMap(), 300));
        assertTrue(state.loading());
        assertNull(state.index());
        assertTrue(state.complete(accountB, Collections.emptyMap(), 400));
        state.invalidate();
        assertNull(state.index());
        assertFalse(state.isCurrent(accountB));
        assertTrue(state.begin(500) >= 0);
    }

    @Test public void switchAwayAndBackStillRejectsOriginalAccountRequest() {
        BookShelfSearchState state = new BookShelfSearchState();
        long firstA = state.begin(0);
        state.invalidate();
        state.begin(1);
        state.invalidate();
        long secondA = state.begin(2);
        assertFalse(state.complete(firstA, Collections.emptyMap(), 3));
        assertTrue(state.complete(secondA, Collections.emptyMap(), 4));
    }
}
