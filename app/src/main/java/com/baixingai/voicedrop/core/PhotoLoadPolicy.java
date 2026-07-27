package com.baixingai.voicedrop.core;

/**
 * Maps photo provenance to user-visible failure semantics.
 *
 * Original recording photos already exist before article generation, so a read
 * failure is a load error. Only keys introduced by an article edit may use the
 * asynchronous image-generation polling state.
 */
public final class PhotoLoadPolicy {
    public enum Intent { ORIGINAL, GENERATED }
    public enum FailureState { LOAD_FAILED, GENERATING }

    private PhotoLoadPolicy() {}

    public static FailureState failureState(Intent intent) {
        return intent == Intent.GENERATED ? FailureState.GENERATING : FailureState.LOAD_FAILED;
    }

    public static boolean shouldPoll(Intent intent) {
        return failureState(intent) == FailureState.GENERATING;
    }

    public static int concurrentLoads() {
        return 3;
    }
}
