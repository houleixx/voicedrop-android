package com.baixingai.voicedrop.data;

public final class CommunityShareResume {
    public static final String EXTRA_RESUME_COMMUNITY_SHARE = "resumeCommunityShare";

    private CommunityShareResume() {
    }

    public static String detailAudioNameAfterLogin(PendingCommunityShareStore.Pending pending) {
        if (pending == null || isBlank(pending.audioName)) return null;
        return pending.audioName;
    }

    public static PendingCommunityShareStore.Pending consumeForDetailIfRequested(
            PendingCommunityShareStore store, String audioName, boolean resumeRequested) {
        if (store == null || !resumeRequested || isBlank(audioName)) return null;
        return store.consume(audioName);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
