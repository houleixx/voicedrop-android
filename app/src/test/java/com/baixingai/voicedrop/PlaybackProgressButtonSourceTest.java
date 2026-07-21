package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackProgressButtonSourceTest {
    @Test
    public void playVectorHasEnoughSpaceToRenderWithoutClipping() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/PlaybackProgressButton.java");

        assertTrue(source.contains("icon.setScaleType(ImageView.ScaleType.CENTER)"));
        assertTrue(source.contains(
                "button.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))"));
        assertFalse(source.contains(
                "button.addView(icon, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER))"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
