package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PromptMarketSourceTest {
    @Test public void settingsExposeMarketFiltersDetailsAndImportWithoutFakeRating() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/InstructionSettingsActivity.java");

        assertTrue(source.contains("社区提示词"));
        assertTrue(source.contains("marketFilter(\"热门\""));
        assertTrue(source.contains("marketFilter(\"最新\""));
        assertTrue(source.contains("marketFilter(\"文字\""));
        assertTrue(source.contains("marketFilter(\"配图\""));
        assertTrue(source.contains("被导入"));
        assertTrue(source.contains("适用于"));
        assertTrue(source.contains("提示词全文"));
        assertTrue(source.contains("配图提示词"));
        assertTrue(source.contains("DialogWindowDefaults.applyModal"));
        assertTrue(source.contains("footer.addView(importButton"));
        assertTrue(source.contains("android.R.drawable.ic_menu_close_clear_cancel"));
        assertTrue(source.contains("closeButton.setContentDescription(\"关闭\")"));
        assertTrue(source.contains("sheetParams.topMargin = dp(220)"));
        assertTrue(source.contains("sheetContainer.setTranslationY"));
        assertTrue(source.contains(".translationY(0)"));
        assertTrue(source.contains(".withEndAction(dialog::dismiss)"));
        assertFalse(source.contains("closeButton.setBackground(rounded"));
        assertFalse(source.contains("handleBox"));
        assertTrue(source.contains("加入我的提示词"));
        assertTrue(source.contains("效果示例"));
        assertTrue(source.contains("ProgressBar spinner = new ProgressBar(this)"));
        assertTrue(source.contains("spinner.setIndeterminateTintList"));
        assertTrue(source.contains("LinearLayout loading = vertical()"));
        assertTrue(source.contains("loading.addView(state, margins(-2, -2, 0, 8, 0, 0))"));
        assertTrue(source.contains("marketIconTile(isImageOnly(item.appliesTo))"));
        assertTrue(source.contains("outlined(Color.WHITE, 16, 0xffebc4b7, 1, 0, 0)"));
        assertTrue(source.contains("row.setMinimumHeight(dp(52))"));
        assertTrue(source.contains("addIconWithSpacing(row, icon, 32)"));
        assertTrue(source.contains("new LinearLayout.LayoutParams(0, dp(32), 1)"));
        assertTrue(source.contains("SimpleToast.show(this, message)"));
        assertFalse(source.contains("Toast.makeText"));
        assertFalse(source.contains("好评"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
