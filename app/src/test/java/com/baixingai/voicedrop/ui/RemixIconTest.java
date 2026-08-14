package com.baixingai.voicedrop.ui;

import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemixIconTest {
    @Test
    public void glyphCatalogMatchesMiniProgramAndHarmonyOsVersion25() {
        assertEquals("\uEA64", RemixIconGlyph.ARROW_LEFT);
        assertEquals("\uEB7B", RemixIconGlyph.CHECK);
        assertEquals("\uEE0E", RemixIconGlyph.HEART_FILL);
        assertEquals("\uEF50", RemixIconGlyph.MIC);
        assertEquals("\uF0E8", RemixIconGlyph.SETTINGS);
        assertEquals("\uF2B6", RemixIconGlyph.WECHAT);
    }

    @Test
    public void bundledFontIsTheSameAssetAsHarmonyOs() throws Exception {
        Path font = Paths.get("src/main/res/font/remixicon.ttf");
        if (!Files.exists(font)) font = Paths.get("app", font.toString());
        assertTrue("Remix Icon font should be bundled", Files.exists(font));

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(font)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        assertEquals("0ac8bc3ccea26ed47e96844ef26675969c669714d723c081101e51799d501520",
                hex(digest.digest()));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
