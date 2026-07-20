package com.baixingai.voicedrop.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PrivacyConsentTest {
    private final FakeStorage storage = new FakeStorage();

    @Test
    public void currentVersionStartsUnaccepted() {
        assertFalse(new PrivacyConsent(storage, () -> 1234L).isAccepted());
    }

    @Test
    public void policyUsesBundledAssetAndNewDisclosureVersion() {
        assertEquals("privacy.html", PrivacyConsent.POLICY_ASSET);
        assertEquals("2026-07-17", PrivacyConsent.CURRENT_VERSION);
    }

    @Test
    public void acceptingStoresCurrentVersionAndTimestamp() {
        PrivacyConsent consent = new PrivacyConsent(storage, () -> 1234L);

        consent.accept();

        assertTrue(consent.isAccepted());
        assertEquals(PrivacyConsent.CURRENT_VERSION, storage.version);
        assertEquals(1234L, storage.acceptedAt);
    }

    @Test
    public void oldAcceptedVersionDoesNotAcceptCurrentPolicy() {
        storage.version = "2026-01-01";

        assertFalse(new PrivacyConsent(storage, () -> 1234L).isAccepted());
    }

    private static final class FakeStorage implements PrivacyConsent.Storage {
        String version;
        long acceptedAt;

        @Override
        public String acceptedVersion() {
            return version;
        }

        @Override
        public void saveAcceptance(String version, long acceptedAt) {
            this.version = version;
            this.acceptedAt = acceptedAt;
        }
    }
}
