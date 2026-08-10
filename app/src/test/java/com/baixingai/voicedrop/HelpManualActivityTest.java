package com.baixingai.voicedrop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public final class HelpManualActivityTest {
    @Test
    public void readsTheBundledMarkdownManualOffline() {
        assertEquals("help_manual.md", HelpManualActivity.MANUAL_ASSET);
    }

    @Test
    public void exposesAllEightManualChaptersInTheNativeAnchorBar() {
        assertArrayEquals(new String[]{
                        "1 上手", "2 录音", "3 改稿", "4 发布",
                        "5 社区", "6 文风", "7 账号", "8 FAQ"
                }, HelpManualActivity.SECTION_LABELS);
    }
}
