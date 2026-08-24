package com.baixingai.voicedrop.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Builds opaque local-cache keys without embedding bearer tokens or account scopes. */
public final class AccountScopedCacheKey {
    private AccountScopedCacheKey() {}

    public static String create(String prefix, String stableIdentity) {
        String safePrefix = prefix == null ? "" : prefix;
        String identity = stableIdentity == null ? "" : stableIdentity;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(safePrefix);
            for (int i = 0; i < 16 && i < hash.length; i++) {
                key.append(String.format("%02x", hash[i] & 0xff));
            }
            return key.toString();
        } catch (Exception ignored) {
            // SHA-256 is mandatory on Android. Keep the fallback opaque if a broken
            // provider is encountered rather than putting the identity in the key.
            return safePrefix + Integer.toHexString(identity.hashCode());
        }
    }
}
