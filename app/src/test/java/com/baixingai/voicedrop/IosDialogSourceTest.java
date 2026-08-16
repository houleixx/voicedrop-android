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
        assertTrue(source.contains("DialogWindowDefaults.applyEdgeToEdgeModal(window, Theme.CARD, true)"));
        assertTrue(source.contains("DialogWindowDefaults.applyModal(window, SCRIM_COLOR, SCRIM_COLOR, false)"));
        assertTrue(source.contains("DialogWindowDefaults.applyFullscreen(getWindow())"));
        assertFalse(source.contains("card.addView(btnDivider, new LinearLayout.LayoutParams(-1, dp(ctx, 1)));\n\n            btnRow = new LinearLayout(ctx);"));
    }

    @Test
    public void bottomSheetDismissesWithSlideAndFadeBeforeCompletion() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/IosDialog.java");

        assertTrue(source.contains("public void dismissAnimated(Runnable completion)"));
        assertTrue(source.contains("bottomSheetRoot.animate()"));
        assertTrue(source.contains(".alpha(0f)"));
        assertTrue(source.contains(".translationY(bottomSheetCard.getHeight())"));
        assertTrue(source.contains("new AccelerateInterpolator()"));
        assertTrue(source.contains(".withEndAction(this::finishBottomSheetDismiss)"));
        assertTrue(source.contains("if (completion != null) completion.run()"));
    }

    @Test
    public void bottomSheetKeepsTransparentEdgeToEdgeStatusBarAcrossDismissal() throws Exception {
        String dialog = readSource("src/main/java/com/baixingai/voicedrop/ui/IosDialog.java");
        String bars = readSource("src/main/java/com/baixingai/voicedrop/ui/SystemBarDefaults.java");
        String defaults = readSource("src/main/java/com/baixingai/voicedrop/ui/DialogWindowDefaults.java");

        assertTrue(dialog.contains("DialogWindowDefaults.applyEdgeToEdgeModal(window,"));
        assertTrue(defaults.contains("SystemBarDefaults.applyEdgeToEdgeModal"));
        assertTrue(bars.contains("public static void applyEdgeToEdgeModal"));
        assertTrue(bars.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"));
        assertTrue(bars.contains("window.setStatusBarColor(Color.TRANSPARENT)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
