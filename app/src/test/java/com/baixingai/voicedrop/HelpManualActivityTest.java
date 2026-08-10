package com.baixingai.voicedrop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public final class HelpManualActivityTest {
    @Test
    public void opensTheFullManualInsteadOfTheHelpCenterIndex() {
        assertEquals("https://voicedrop.cn/help/manual/", HelpManualActivity.HELP_MANUAL_URL);
    }

    @Test
    public void exposesAllEightManualChaptersInTheNativeAnchorBar() {
        assertArrayEquals(
                new String[]{"ch1", "ch2", "ch3", "ch4", "ch5", "ch6", "ch7", "ch8"},
                HelpManualActivity.SECTION_IDS);
        assertEquals(8, HelpManualActivity.SECTION_LABELS.length);
    }
}
