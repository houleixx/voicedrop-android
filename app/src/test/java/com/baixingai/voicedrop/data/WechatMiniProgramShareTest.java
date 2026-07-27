package com.baixingai.voicedrop.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WechatMiniProgramShareTest {
    @Test
    public void acceptsOnlyWechatMiniProgramOriginalIds() {
        assertTrue(WechatMiniProgramShare.isOriginalId("gh_123Abc"));
        assertFalse(WechatMiniProgramShare.isOriginalId("wx" + "1234567890abcdef"));
        assertFalse(WechatMiniProgramShare.isOriginalId("mini-program"));
    }

    @Test
    public void buildsTheExistingMiniProgramCommunityRoute() {
        assertEquals("pages/community-detail/index?shareId=share%20id%2F1",
                WechatMiniProgramShare.communityPath("share id/1"));
    }
}
