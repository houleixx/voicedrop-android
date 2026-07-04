package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class SettingsActivitySourceTest {
    @Test
    public void settingsScreenUsesBouncyScrollView() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");
        String bouncySource = readSource("src/main/java/com/baixingai/voicedrop/ui/BouncyScrollView.java");

        assertTrue(source.contains("import com.baixingai.voicedrop.ui.BouncyScrollView;"));
        assertTrue(source.contains("BouncyScrollView scroll = new BouncyScrollView(this);"));
        assertFalse(source.contains("import android.widget.ScrollView;"));
        assertFalse(source.contains("ScrollView scroll = new ScrollView(this);"));
        assertTrue(bouncySource.contains("extends ScrollView"));
        assertTrue(bouncySource.contains("setOverScrollMode(OVER_SCROLL_NEVER)"));
        assertTrue(bouncySource.contains("setTranslationY"));
        assertTrue(bouncySource.contains("DecelerateInterpolator"));
    }

    @Test
    public void writingStyleSheetExposesMultiStyleSelection() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/SettingsActivity.java");

        assertTrue(source.contains("多风格对比"));
        assertTrue(source.contains("settingsStore.loadStyleHistory()"));
        assertTrue(source.contains("settingsStore.saveStyleSelection(snapshot)"));
        assertTrue(source.contains("最多选择 3 个风格版本"));
        assertTrue(source.contains("selectedStyles.clear()"));
        assertTrue(source.contains("IosDialog.showBottomSheet(this, \"写作风格\", form, 560"));
        assertTrue(source.contains("FrameLayout editorFrame = new FrameLayout(this)"));
        assertTrue(source.contains("frameLp.setMargins(0, dp(12), 0, 0)"));
        assertTrue(source.contains("final boolean[] listOpen = {true}"));
        assertTrue(source.contains("final boolean[] styleLoading = {true}"));
        assertTrue(source.contains("new LoadingStateView(this, \"正在加载写作风格...\""));
        assertTrue(source.contains("toast(\"写作风格加载失败：\" + e.getMessage())"));
        assertTrue(source.contains("listOpen[0] = !listOpen[0]"));
        assertTrue(source.contains("pill.setBackground(round(Theme.ACCENT, 7))"));
        assertTrue(source.contains("bg.setStroke(dp(1), 0xffb9b0a6)"));
        assertTrue(source.contains("card.setElevation(dp(8))"));
        assertTrue(source.contains("new LinearLayout.LayoutParams(-1, dp(56))"));
        assertTrue(source.contains("rowIndex == 0 ? dp(8) : dp(2)"));
        assertTrue(source.contains("rowIndex == validRows - 1 ? dp(8) : dp(2)"));
        assertFalse(source.contains("if (selected) rowLp.setMargins"));
        assertTrue(source.contains("R.drawable.ic_chevron_up_flat"));
        assertTrue(source.contains("R.drawable.ic_chevron_down_flat"));
        assertTrue(source.contains("R.drawable.ic_checkbox_checked_flat"));
        assertTrue(source.contains("R.drawable.ic_checkbox_unchecked_flat"));
        assertTrue(source.contains("new LinearLayout.LayoutParams(dp(28), dp(28))"));
        assertTrue(source.contains("R.drawable.ic_check_flat"));
        assertFalse(source.contains("☑"));
        assertFalse(source.contains("☐"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
