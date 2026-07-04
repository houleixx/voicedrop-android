package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class AppUpdateManagerSourceTest {
    @Test
    public void updatePromptUsesAdaptiveHeightShortIgnoreLabelAndCloseInsteadOfCancel() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/update/AppUpdateManager.java");
        String dialogSource = readSource("src/main/java/com/baixingai/voicedrop/ui/IosDialog.java");

        assertTrue(source.contains("IosDialog.showAutoHeight(activity, \"发现新版本\", message,"));
        assertTrue(source.contains("\"忽略\", () -> {"));
        assertTrue(source.contains("}, true, false);"));
        assertFalse(source.contains("IosDialog.show(activity, \"发现新版本\", message, 60,"));
        assertFalse(source.contains("\"忽略此版本\", () -> {"));
        assertTrue(dialogSource.contains("boolean showCloseButton, boolean includeDefaultCancelButton"));
        assertTrue(dialogSource.contains("showCloseButton, false, true, includeDefaultCancelButton"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
