package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsNavigationChevronSourceTest {
    @Test
    public void aboutRowsUseVectorTrailingChevrons() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/AboutActivity.java");

        assertFalse(source.contains("text(\"›\""));
        assertTrue(source.contains("private ImageView trailingChevron()"));
        assertTrue(source.contains("chevron.setImageResource(R.drawable.ic_chevron_right_flat)"));
        assertTrue(source.contains("row.addView(trailingChevron())"));
    }

    @Test
    public void promptRowsUseVectorTrailingChevrons() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/InstructionSettingsActivity.java");

        assertFalse(source.contains("text(\"›\""));
        assertTrue(source.contains("private ImageView trailingChevron()"));
        assertTrue(source.contains("chevron.setImageResource(R.drawable.ic_chevron_right_flat)"));
        assertTrue(source.contains("line.addView(trailingChevron())"));
        assertTrue(source.contains("box.addView(trailingChevron())"));
        assertTrue(source.contains("row.addView(trailingChevron())"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
