package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class MiniProgramShareLoadingSourceTest {
    @Test
    public void cardSharingShowsAndDismissesACenteredLoadingDialog() throws Exception {
        assertLoading(readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java"));
        assertLoading(readSource("src/main/java/com/baixingai/voicedrop/CommunityDetailActivity.java"));
    }

    private static void assertLoading(String source) {
        assertTrue(source.contains("WechatShareLoadingDialog.show"));
        assertTrue(source.contains("loading.dismiss()"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
