package com.baixingai.voicedrop;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SettingsIconTest {
    @Test
    public void everyVisibleSettingsRowHasAnIcon() {
        assertTrue(SettingsActivity.SETTING_ROW_ICON_RES_IDS.length >= 10);
        for (int resId : SettingsActivity.SETTING_ROW_ICON_RES_IDS) {
            assertTrue("Setting rows should declare a drawable icon", resId != 0);
        }
    }
}
