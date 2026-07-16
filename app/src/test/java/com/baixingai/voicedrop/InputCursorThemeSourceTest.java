package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class InputCursorThemeSourceTest {
    @Test
    public void appThemeUsesVoiceDropAccentForEveryTextCursor() throws Exception {
        String styles = readSource("src/main/res/values/styles.xml");
        String colors = readSource("src/main/res/values/colors.xml");
        String cursor = readSource("src/main/res/drawable/text_cursor_accent.xml");

        assertTrue(colors.contains("<color name=\"theme_accent\">#D8593B</color>"));
        assertTrue(styles.contains("<item name=\"android:colorAccent\">@color/theme_accent</item>"));
        assertTrue(styles.contains("<item name=\"android:textCursorDrawable\">@drawable/text_cursor_accent</item>"));
        assertTrue(cursor.contains("<solid android:color=\"@color/theme_accent\""));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
