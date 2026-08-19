package com.baixingai.voicedrop.net;

/** Pure route-selection policy shared by the Android adapter and JVM tests. */
public final class ApiRoutePolicy {
    public static final long HYSTERESIS_MS = 150L;

    private ApiRoutePolicy() {}

    /** A missing latency means that entry failed or timed out. */
    public static String pick(String incumbent, Long cnMs, Long cfMs) {
        if (cnMs == null && cfMs == null) return incumbent;
        if (cnMs != null && cfMs == null) return Api.CN_HOST;
        if (cnMs == null) return Api.CF_HOST;

        long incumbentMs = Api.CF_HOST.equals(incumbent) ? cfMs : cnMs;
        long challengerMs = Api.CF_HOST.equals(incumbent) ? cnMs : cfMs;
        if (challengerMs + HYSTERESIS_MS >= incumbentMs) return incumbent;
        return Api.CF_HOST.equals(incumbent) ? Api.CN_HOST : Api.CF_HOST;
    }
}
