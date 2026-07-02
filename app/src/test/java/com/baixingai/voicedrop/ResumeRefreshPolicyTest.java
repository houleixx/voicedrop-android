package com.baixingai.voicedrop;

import org.junit.Test;

import static org.junit.Assert.*;

public class ResumeRefreshPolicyTest {
    @Test public void renderedTopLevelPageRefreshesSilentlyOnResume() {
        assertFalse(ResumeRefreshPolicy.shouldRedrawOnResume(false, true));
    }

    @Test public void firstTopLevelResumeCanRedraw() {
        assertTrue(ResumeRefreshPolicy.shouldRedrawOnResume(false, false));
    }

    @Test public void detailPageNeverRunsTopLevelResumeRedraw() {
        assertFalse(ResumeRefreshPolicy.shouldRedrawOnResume(true, false));
        assertFalse(ResumeRefreshPolicy.shouldRedrawOnResume(true, true));
    }
}
