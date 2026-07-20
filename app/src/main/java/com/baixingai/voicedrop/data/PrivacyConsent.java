package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class PrivacyConsent {
    public static final String POLICY_URL = "https://voicedrop.cn/privacy/";
    public static final String POLICY_ASSET = "privacy.html";
    public static final String CURRENT_VERSION = "2026-07-17";

    private static final String PREFS = "voicedrop.privacy";
    private static final String ACCEPTED_VERSION = "acceptedVersion";
    private static final String ACCEPTED_AT = "acceptedAt";

    interface Storage {
        String acceptedVersion();
        void saveAcceptance(String version, long acceptedAt);
    }

    interface Clock {
        long now();
    }

    private final Storage storage;
    private final Clock clock;

    public PrivacyConsent(Context context) {
        this(new SharedPreferencesStorage(context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)), System::currentTimeMillis);
    }

    PrivacyConsent(Storage storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }

    public boolean isAccepted() {
        return CURRENT_VERSION.equals(storage.acceptedVersion());
    }

    public void accept() {
        storage.saveAcceptance(CURRENT_VERSION, clock.now());
    }

    private static final class SharedPreferencesStorage implements Storage {
        private final SharedPreferences prefs;

        private SharedPreferencesStorage(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public String acceptedVersion() {
            return prefs.getString(ACCEPTED_VERSION, null);
        }

        @Override
        public void saveAcceptance(String version, long acceptedAt) {
            prefs.edit()
                    .putString(ACCEPTED_VERSION, version)
                    .putLong(ACCEPTED_AT, acceptedAt)
                    .apply();
        }
    }
}
