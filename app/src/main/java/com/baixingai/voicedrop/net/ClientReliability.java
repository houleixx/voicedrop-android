package com.baixingai.voicedrop.net;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public final class ClientReliability {
    private ClientReliability() {}

    public static boolean commandMessageRequiresRefresh(String type) {
        // A connection snapshot only restores command-queue state.  It carries no
        // affected stems, so treating it as an article update would evict every
        // cached title and cover on each cold start.
        return "updated".equals(type);
    }

    public static boolean shouldInvalidateAllArticleCaches(List<String> stems) {
        return stems == null || stems.isEmpty();
    }

    public static OkHttpClient newLongLivedWebSocketClient() {
        return new OkHttpClient.Builder()
                .pingInterval(25, TimeUnit.SECONDS)
                .build();
    }

    public static boolean isCurrentGeneration(int activeGeneration, int callbackGeneration) {
        return activeGeneration == callbackGeneration;
    }

    public static boolean accountIdentityChanged(String connectedIdentity, String currentIdentity) {
        if (connectedIdentity == null) connectedIdentity = "";
        if (currentIdentity == null) currentIdentity = "";
        return !connectedIdentity.equals(currentIdentity);
    }
}
