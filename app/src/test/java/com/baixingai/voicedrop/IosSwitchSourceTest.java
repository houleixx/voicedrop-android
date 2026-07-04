package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IosSwitchSourceTest {
    @Test
    public void settingsScreensUseProjectIosSwitch() throws Exception {
        String settings = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String wechat = readSource("src/main/java/com/baixingai/voicedrop/WechatSettingsActivity.java");

        assertFalse(settings.contains("import android.widget.Switch;"));
        assertFalse(wechat.contains("import android.widget.Switch;"));
        assertFalse(settings.contains("new Switch("));
        assertFalse(wechat.contains("new Switch("));
        assertTrue(settings.contains("new IosSwitch(this)"));
        assertTrue(wechat.contains("new IosSwitch(this)"));
    }

    @Test
    public void iosSwitchDrawsItsOwnTrackAndThumb() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/IosSwitch.java");

        assertTrue(source.contains("extends View"));
        assertTrue(source.contains("drawRoundRect"));
        assertTrue(source.contains("drawCircle"));
        assertTrue(source.contains("setOnCheckedChangeListener"));
        assertTrue(source.contains("Theme.RED"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
