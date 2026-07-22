package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfiguredMenuSourceTest {
    @Test
    public void submenuReopensPopupAtTheOriginalImageAnchor() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("showConfiguredPopup(sub, anchor)"));
        assertTrue(source.contains("if (popup[0] != null) popup[0].dismiss();"));
        assertFalse(source.contains("popup[0].setContentView(sub)"));
    }

    @Test
    public void configuredRowsUseTextOnlyHierarchyWithoutIcons() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("configuredSubmenuRow(node.label)"));
        assertTrue(source.contains("configuredMenuRow(node.label, Theme.INK, Typeface.NORMAL)"));
        assertTrue(source.contains("configuredMenuRow(\"返回\", Theme.SECONDARY, Typeface.BOLD)"));
        assertFalse(source.contains("menuRow(node.label, AliIconFont"));
        assertFalse(source.contains("configuredInstructionIcon"));
    }

    @Test
    public void onlyRowsThatOpenSubmenusShowTheProjectRightChevron() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("chevron.setImageResource(R.drawable.ic_chevron_right_flat)"));
        assertTrue(source.contains("chevron.setColorFilter(Theme.FAINT)"));
        assertTrue(source.contains("configuredSubmenuRow(node.label)"));
    }

    @Test
    public void onlyCustomPromptRowsAreInsideTheHeightLimitedScrollView() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("isCustomMenuNode(node) ? menu.scrollable : menu.fixedTop"));
        assertTrue(source.contains("addConfiguredMenuRow(menu.fixedBottom, item)"));
        assertTrue(source.contains("scroll.addView(menu.scrollable"));
        assertTrue(source.contains("BouncyScrollView scroll = new BouncyScrollView(this)"));
        assertTrue(source.contains("scroll.setVerticalScrollBarEnabled(scrollContentHeight > scrollViewportHeight)"));
        assertTrue(source.contains("scroll.setScrollbarFadingEnabled(false)"));
        assertTrue(source.contains("surface.setBackground(round(0xf9ffffff, 14))"));
        assertTrue(source.contains("surface.setClipToOutline(true)"));
        assertTrue(source.contains("popup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT"));
    }

    @Test
    public void everySecondLevelMenuScrollsWhileItsBackActionStaysFixed() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("addConfiguredMenuRow(sub.fixedTop, back)"));
        assertTrue(source.contains("addConfiguredNode(sub.scrollable, child"));
    }

    @Test
    public void popupUsesUniformFourSidedShadowInsteadOfNativeBottomWeightedElevation() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("new SoftRoundedShadowFrameLayout(this, 14, 8)"));
        assertTrue(source.contains("popup.setElevation(0)"));
    }

    @Test
    public void everyConfiguredMenuRowIsSeparatedIncludingImportedPrompts() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("addConfiguredMenuRow(menu, row)"));
        assertTrue(source.contains("addConfiguredMenuRow(menu.fixedBottom, item)"));
        assertTrue(source.contains("if (menu.getChildCount() > 0) menu.addView(configuredMenuDivider())"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
