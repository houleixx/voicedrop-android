package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SettingsIconTest {
    @Test
    public void everyVisibleSettingsRowHasAnIcon() {
        assertEquals(11, SettingsActivity.SETTING_ROW_ICON_RES_IDS.length);
        for (int resId : SettingsActivity.SETTING_ROW_ICON_RES_IDS) {
            assertTrue("Setting rows should declare a drawable icon", resId != 0);
        }
        assertFalse(Arrays.stream(SettingsActivity.SETTING_ROW_ICON_RES_IDS)
                .anyMatch(resId -> resId == R.drawable.ic_settings_trash));
    }

    @Test
    public void aiInstructionsAndUsageUseDistinctIcons() {
        assertEquals(R.drawable.ic_settings_ai_instruction, SettingsActivity.SETTING_ROW_ICON_RES_IDS[2]);
        assertEquals(R.drawable.ic_settings_bolt, SettingsActivity.SETTING_ROW_ICON_RES_IDS[4]);
    }

    @Test
    public void aiInstructionSettingsIconUsesMagicWandGlyph() throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get(
                "src/main/res/drawable/ic_settings_ai_instruction.xml")), StandardCharsets.UTF_8);

        assertTrue(xml.contains("M395.69,372.48"));
        assertTrue(xml.contains("M578.05,226.86"));
    }

    @Test
    public void exportSettingsIconUsesDownloadArrow() throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get(
                "src/main/res/drawable/ic_settings_export.xml")), StandardCharsets.UTF_8);

        assertTrue(xml.contains("M12,4v10"));
        assertTrue(xml.contains("M8,10l4,4l4,-4"));
        assertFalse(xml.contains("M8,8l4,-4l4,4"));
    }
}
