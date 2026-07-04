package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class IosDialogSourceTest {
    @Test
    public void bottomSheetButtonsUseLargeRoundedActionStyleWithoutTopDivider() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/IosDialog.java");

        assertTrue(source.contains("if (!bottomSheet)"));
        assertTrue(source.contains("makeBottomSheetButton(ctx, positiveText, Theme.ACCENT, 0xffffffff"));
        assertTrue(source.contains("btnRow.setOrientation(bottomSheet ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL)"));
        assertTrue(source.contains("new LinearLayout.LayoutParams(-1, dp(ctx, bottomSheet ? 56 : 50))"));
        assertTrue(source.contains("setCornerRadius(dp(ctx, 12))"));
        assertFalse(source.contains("card.addView(btnDivider, new LinearLayout.LayoutParams(-1, dp(ctx, 1)));\n\n            btnRow = new LinearLayout(ctx);"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
