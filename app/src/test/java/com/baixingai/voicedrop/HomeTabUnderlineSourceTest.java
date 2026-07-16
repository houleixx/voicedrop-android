package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HomeTabUnderlineSourceTest {
    @Test
    public void underlineUsesVisibleGlyphBoundsAndRenderedLineOrigin() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String method = methodBody(source, "protected void updateUnderline");

        assertTrue(method.contains("android.text.Layout textLayout = activeTab.getLayout()"));
        assertTrue(method.contains("android.graphics.Rect glyphBounds = new android.graphics.Rect()"));
        assertTrue(method.contains("activeTab.getPaint().getTextBounds(label, 0, label.length(), glyphBounds)"));
        assertTrue(method.contains("int lineW = glyphBounds.width()"));
        assertTrue(method.contains("activeTab.getLeft() + activeTab.getTotalPaddingLeft()"));
        assertTrue(method.contains("textLayout.getLineLeft(0) + glyphBounds.left"));
        assertFalse(method.contains("measureText"));
        assertFalse(method.contains("tabW * 0.8"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(start, i + 1);
            }
        }
        fail("Unclosed method: " + signature);
        return "";
    }
}
