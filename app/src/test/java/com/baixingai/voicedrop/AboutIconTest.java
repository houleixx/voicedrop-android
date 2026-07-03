package com.baixingai.voicedrop;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AboutIconTest {
    @Test
    public void everyAboutRowHasAnIcon() {
        assertEquals(4, AboutActivity.ABOUT_ROW_ICON_RES_IDS.length);
        for (int resId : AboutActivity.ABOUT_ROW_ICON_RES_IDS) {
            assertTrue("About rows should declare a drawable icon", resId != 0);
        }
    }
}
