package com.baixingai.voicedrop;

public final class ResumeRefreshPolicy {
    private ResumeRefreshPolicy() {
    }

    public static boolean shouldRedrawOnResume(boolean detailActivity, boolean topLevelUiRendered) {
        return !detailActivity && !topLevelUiRendered;
    }
}
