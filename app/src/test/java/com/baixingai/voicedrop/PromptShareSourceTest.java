package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class PromptShareSourceTest {
    @Test
    public void promptSettingsSupportSharingCodesAndLandingPageUrls() throws Exception {
        String store = readSource("src/main/java/com/baixingai/voicedrop/data/UIConfigStore.java");
        String activity = readSource("src/main/java/com/baixingai/voicedrop/InstructionSettingsActivity.java");

        assertTrue(store.contains("/prompt-share"));
        assertTrue(store.contains("shareCode"));
        assertTrue(store.contains("sharing"));
        assertTrue(activity.contains("分享这条提示词"));
        assertTrue(activity.contains("Api.sharePage"));
        assertTrue(activity.contains("Intent.ACTION_SEND"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
