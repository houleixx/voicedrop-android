package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class WechatSettingsActivitySourceTest {
    @Test
    public void credentialHelpUsesProvidedIconsAndOfficialConsoleFlow() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/WechatSettingsActivity.java");

        assertTrue(source.contains("R.drawable.ic_wechat_help_compass"));
        assertTrue(source.contains("R.drawable.ic_arrow_up_right_flat"));
        assertTrue(source.contains("https://developers.weixin.qq.com/console/"));
        assertTrue(source.contains("扫一扫 → 右上角相册"));
        assertTrue(source.contains("IP 白名单"));
        assertFalse(source.contains("⊘ 去哪里找 AppID / AppSecret?  ↗"));
        assertFalse(source.contains("Get_access_token.html"));
    }

    @Test
    public void providedSvgPathsAreStoredAsVectorDrawables() throws Exception {
        String compass = readSource("src/main/res/drawable/ic_wechat_help_compass.xml");
        String arrow = readSource("src/main/res/drawable/ic_arrow_up_right_flat.xml");

        assertTrue(compass.contains("android:viewportWidth=\"1024\""));
        assertTrue(compass.contains("M580.27,580.27"));
        assertTrue(arrow.contains("android:viewportWidth=\"1042\""));
        assertTrue(arrow.contains("M383.05,659.91"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
