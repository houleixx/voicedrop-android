package com.baixingai.voicedrop.core;

/** Image-read policy: generated/unknown keys tolerate asynchronous availability;
 * previously displayed images get a short network-recovery window. */
public final class PhotoLoadPolicy {
    public enum Intent { ORIGINAL, GENERATED, UNKNOWN }
    public enum FailureState { LOAD_FAILED, GENERATING }

    private PhotoLoadPolicy() {}

    public static FailureState failureState(Intent intent) {
        return intent == Intent.ORIGINAL ? FailureState.LOAD_FAILED : FailureState.GENERATING;
    }

    public static boolean shouldPoll(Intent intent) {
        return true; // Ordinary images retry briefly; unknown/generated files get a longer window.
    }

    public static Intent restoredIntent(boolean generated, boolean displayed) {
        return displayed ? Intent.ORIGINAL : generated ? Intent.GENERATED : Intent.UNKNOWN;
    }

    public static long timeoutMs(Intent intent) {
        return intent == Intent.ORIGINAL ? 15_000L : 300_000L;
    }

    public static long startedAt(long saved, long now, boolean explicitRetry) {
        return !explicitRetry && saved > 0 && saved <= now ? saved : now;
    }

    public static int concurrentLoads() {
        return 3;
    }
}
