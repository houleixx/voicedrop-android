package com.baixingai.voicedrop.net;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public final class ClientReliability {
    private ClientReliability() {}

    public static boolean commandMessageRequiresRefresh(String type) {
        return "updated".equals(type) || "snapshot".equals(type);
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

    public static boolean accountIdentityChanged(String connectedBearer, String currentBearer) {
        if (connectedBearer == null) connectedBearer = "";
        if (currentBearer == null) currentBearer = "";
        return !connectedBearer.equals(currentBearer);
    }
}
