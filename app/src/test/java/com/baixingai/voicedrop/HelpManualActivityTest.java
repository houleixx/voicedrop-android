package com.baixingai.voicedrop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    @Test
    public void bundledManualUsesAndroidAccountAndPlatformInstructions() throws Exception {
        String manual = new String(Files.readAllBytes(
                Paths.get("src/main/assets/help_manual.md")), StandardCharsets.UTF_8);

        assertTrue(manual.contains("匿名 ID 保存在本机"));
        assertTrue(manual.contains("登录微信账号"));
        assertTrue(manual.contains("Android 系统分享面板"));
        assertTrue(manual.contains("向服务端取得专用邀请链接"));
        assertFalse(manual.matches("(?s).*(iOS|iPhone|iPad|iCloud|Apple ID|Apple 登录|Safari|Siri|App Store).*"));
    }
}
