package com.baixingai.voicedrop.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Mirrors the iOS daily price-cache rules without requiring Android storage or a network. */
public final class PricesTest {
    @Test public void fallbackMatchesServerDefaults() {
        assertEquals(160, Prices.FALLBACK.book);
        assertEquals(40, Prices.FALLBACK.bookRevise);
    }

    @Test public void stalenessUsesTwentyFourHours() {
        Prices.Table table = new Prices.Table(160, 40, 1_000);
        assertFalse(Prices.isStale(table, 1_000 + Prices.TTL_MS));
        assertTrue(Prices.isStale(table, 1_000 + Prices.TTL_MS + 1));
    }

    @Test public void remotePriceMustHavePositiveBookAndKeepsValidPreviousRevise() {
        Prices.Table previous = new Prices.Table(160, 40, 0);
        Prices.Table merged = Prices.merge("{\"book\":123,\"book_revise\":45}", previous, 777);
        assertEquals(123, merged.book);
        assertEquals(45, merged.bookRevise);
        assertEquals(40, Prices.merge("{\"book\":200,\"book_revise\":0}", previous, 1).bookRevise);
        assertNull(Prices.merge("{\"book\":0,\"book_revise\":40}", previous, 1));
        assertNull(Prices.merge("{\"book\":-1,\"book_revise\":40}", previous, 1));
    }
}
