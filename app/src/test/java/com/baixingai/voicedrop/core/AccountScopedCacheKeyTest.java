package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class AccountScopedCacheKeyTest {
    @Test public void stableIdentityGetsStableOpaqueBucket() {
        String identity = "users/account-owner-123/";
        String first = AccountScopedCacheKey.create("index_v2_", identity);
        String second = AccountScopedCacheKey.create("index_v2_", identity);

        assertEquals(first, second);
        assertTrue(first.startsWith("index_v2_"));
        assertEquals("index_v2_".length() + 32, first.length());
        assertFalse(first.contains(identity));
    }

    @Test public void differentAccountsCannotShareShelfBucket() {
        assertNotEquals(
                AccountScopedCacheKey.create("index_v2_", "users/alice/"),
                AccountScopedCacheKey.create("index_v2_", "users/bob/"));
    }

    @Test public void tokenLikeIdentityIsNeverWrittenVerbatim() {
        String bearer = "anon_secret-bearer-material-that-must-not-leak";
        String key = AccountScopedCacheKey.create("index_v2_", bearer);
        assertFalse(key.contains("anon_"));
        assertFalse(key.contains(bearer));
    }
}
