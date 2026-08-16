package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BookReviseResultTest {
    @Test public void acceptsAsyncRevisionAndKeepsServerTimestamp() {
        BookReviseResult result = BookReviseResult.from(202, "{\"ts\":123456}");
        assertTrue(result.accepted);
        assertEquals(123456, result.timestampMs, 0);
    }

    @Test public void insufficientBalanceUsesAuthoritativeServerPriceAndBalance() {
        BookReviseResult result = BookReviseResult.from(402,
                "{\"need_suanli\":48.5,\"suanli\":12.2}");
        assertFalse(result.accepted);
        assertTrue(result.message.contains("48.5"));
        assertTrue(result.message.contains("12.2"));
    }

    @Test public void mapsOwnerLegacyBusyAndAuthFailures() {
        assertTrue(BookReviseResult.from(401, "{}").message.contains("身份"));
        assertTrue(BookReviseResult.from(403, "{}").message.contains("主人"));
        assertTrue(BookReviseResult.from(404, "{}").message.contains("早期"));
        assertTrue(BookReviseResult.from(409, "{}").message.contains("进行"));
    }
}
