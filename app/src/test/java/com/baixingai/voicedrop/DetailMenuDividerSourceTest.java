package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class DetailMenuDividerSourceTest {
    @Test
    public void articleAndAudioMenusInsetDividersFromBothEdges() throws Exception {
        String article = readSource("src/main/java/com/baixingai/voicedrop/CommunityDetailActivity.java");
        String audio = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertInsetDivider(article);
        assertInsetDivider(audio);
        assertTrue(audio.contains("new LinearLayout.LayoutParams(-1, dp(1));\n        thickDividerLp.setMargins(dp(16), 0, dp(16), 0)"));
    }

    private static void assertInsetDivider(String source) {
        assertTrue(source.contains("LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));"));
        assertTrue(source.contains("lp.setMargins(dp(16), 0, dp(16), 0)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
