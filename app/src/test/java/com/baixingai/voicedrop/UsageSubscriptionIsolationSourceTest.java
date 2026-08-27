package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;

/** Guards the failure boundary: a payment-status outage must not erase the usage ledger. */
public final class UsageSubscriptionIsolationSourceTest {
    @Test public void initialUsageLoadDoesNotQuerySubscriptionStatus() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/UsageActivity.java");
        int start = source.indexOf("    private void load() {");
        int end = source.indexOf("    private void renderLoading()", start);
        String loadMethod = source.substring(start, end);

        assertFalse("Subscription status must have its own failure boundary, not clear the ledger.",
                loadMethod.contains("subscriptionStore.status()"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
