package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemBarInsetsSourceTest {
    @Test
    public void sharedInsetsIncludeStatusBarAndDisplayCutoutWithoutAccumulatingPadding() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/SystemBarDefaults.java");

        assertTrue(source.contains("WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()"));
        assertTrue(source.contains("WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.displayCutout()"));
        assertTrue(source.contains("baseLeft + Math.max(topSafe.left, bottomSafe.left)"));
        assertTrue(source.contains("baseTop + topSafe.top"));
        assertTrue(source.contains("baseRight + Math.max(topSafe.right, bottomSafe.right)"));
        assertTrue(source.contains("baseBottom + bottomSafe.bottom"));
        assertTrue(source.contains("ViewCompat.requestApplyInsets"));
    }

    @Test
    public void everyEdgeToEdgePageUsesLiveTopInsetsInsteadOfAndroidInternalDimensions() throws Exception {
        String[] pages = {
                "AccountActivity.java",
                "AboutActivity.java",
                "CommunityActivity.java",
                "CommunityDetailActivity.java",
                "InstructionSettingsActivity.java",
                "PrivacyPolicyActivity.java",
                "PromptEditActivity.java",
                "PromptImportActivity.java",
                "RecordingDetailActivity.java",
                "RecordingsActivity.java",
                "SettingsActivity.java",
                "SharedArticleActivity.java",
                "UsageActivity.java",
                "WechatSettingsActivity.java"
        };

        for (String page : pages) {
            String source = readSource("src/main/java/com/baixingai/voicedrop/" + page);
            assertTrue(page + " must apply live top insets",
                    source.contains("SystemBarDefaults.applyTopInsets(")
                            || source.contains("SystemBarDefaults.applyTopAndBottomInsets("));
            assertFalse(page + " must not read the internal status bar dimension",
                    source.contains("getIdentifier(\"status_bar_height\""));
        }
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
