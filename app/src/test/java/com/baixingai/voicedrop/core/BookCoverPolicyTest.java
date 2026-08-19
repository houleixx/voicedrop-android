package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class BookCoverPolicyTest {
    @Test public void cacheKeyChangesWhenCoverVersionChanges() {
        assertEquals("a-book-456", BookCoverPolicy.cacheKey("a-book", 456));
        assertEquals("a-book-789", BookCoverPolicy.cacheKey("a-book", 789));
    }

    @Test public void retryScheduleIsFiniteAndExponential() {
        assertArrayEquals(new long[]{0L, 2_000L, 4_000L, 8_000L},
                BookCoverPolicy.retryDelaysMs());
    }
}
