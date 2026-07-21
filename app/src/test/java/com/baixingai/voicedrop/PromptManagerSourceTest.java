package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class PromptManagerSourceTest {
    @Test public void managerExposesCompletePromptActions() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        for (String text : new String[]{"新建动作", "新建分组", "输入魔法数字导入", "恢复默认提示词", "删除提示词", "store.refresh"}) {
            assertTrue(text, manager.contains(text));
        }
        assertTrue(manager.contains("errorBanner"));
        assertTrue(manager.contains("showNewSheet"));
        assertTrue(manager.contains("expandedGroups"));
        assertTrue(manager.contains("MotionEvent.ACTION_UP"));
    }

    @Test public void managerUsesTheSharedSettingsHeader() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("FrameLayout top = new FrameLayout(this)"));
        assertTrue(manager.contains("FrameLayout backTouch = new FrameLayout(this)"));
        assertTrue(manager.contains("AliIconFont.apply(backIcon, AliIconFont.BACK, Theme.INK)"));
        assertTrue(manager.contains("TextView topTitle = text(\"提示词\", 24, Typeface.BOLD, Theme.INK)"));
        assertTrue(manager.contains("topTitle.setGravity(Gravity.CENTER)"));
        assertTrue(manager.contains("top.addView(topTitle, new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER))"));
        assertTrue(manager.contains("Gravity.RIGHT | Gravity.CENTER_VERTICAL"));
    }

    @Test public void managerKeepsTwelveDpBetweenListIconsAndCopy() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("addIconWithSpacing(line, icon, 40)"));
        assertTrue(manager.contains("addIconWithSpacing(box, icon, 44)"));
        assertTrue(manager.contains("iconLp.rightMargin = dp(12)"));
    }

    @Test public void managerUsesComfortablyCompactPromptListRows() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("line.setPadding(dp(14 + row.depth * 18), dp(8), dp(14), dp(8))"));
        assertTrue(manager.contains("line.setMinimumHeight(dp(row.group ? 52 : 56))"));
    }

    @Test public void longPressStartsDragAndReorderSaveShowsLoading() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("if (!sorting) enterSort(false)"));
        assertTrue(manager.contains("startPromptDrag(v, row.node.id)"));
        assertTrue(manager.contains("store.applyReorder(draft, baseline)"));
        assertTrue(manager.contains("\"保存中…\""));
        assertTrue(manager.contains("io.execute(() ->"));
    }

    @Test public void promptRowsRevealDeleteAndConfirmAfterAHorizontalSwipe() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("MotionEvent.ACTION_MOVE"));
        assertTrue(manager.contains("touchStart[0] = event.getRawX()"));
        assertTrue(manager.contains("float dx = event.getRawX() - touchStart[0]"));
        assertTrue(manager.contains("v.cancelLongPress()"));
        assertTrue(manager.contains("if (swiping[0]) return true"));
        assertTrue(manager.contains("if (!row.group)"));
        assertTrue(manager.contains("text(\"删除\", 14, Typeface.BOLD, Color.WHITE)"));
        assertTrue(manager.contains("deleteAction.setOnClickListener"));
        assertTrue(manager.contains("translationX(-dp(88))"));
        assertFalse(manager.contains("if (shouldDelete) confirmDelete(row.node)"));
        assertTrue(manager.contains("IosDialog.showConfirmation(this, \"删除提示词\""));
        assertTrue(manager.contains("Dialog deleteProgress = showDeleteProgress()"));
        assertTrue(manager.contains("private Dialog showDeleteProgress()"));
        assertTrue(manager.contains("rounded(0xee1f1f1f, 14)"));
        assertTrue(manager.contains("LinearLayout box = vertical()"));
        assertTrue(manager.contains("text(\"正在删除…\", 14, Typeface.BOLD, Color.WHITE)"));
        assertTrue(manager.contains("box.addView(status, margins(-2, -2, 0, 10, 0, 0))"));
        assertFalse(manager.contains("IosDialog.showProgress(this, \"正在删除提示词\""));
        assertTrue(manager.contains("deleteProgress.dismiss()"));
        assertFalse(manager.contains("new AlertDialog.Builder(this)"));
    }

    @Test public void managerUsesAnIconForTheExpandedGroupChevron() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("R.drawable.ic_prompt_chevron_down"));
        assertTrue(manager.contains("chevron.setImageResource"));
        Path icon = Paths.get("src/main/res/drawable/ic_prompt_chevron_down.xml");
        if (!Files.exists(icon)) icon = Paths.get("app/src/main/res/drawable/ic_prompt_chevron_down.xml");
        assertTrue(Files.exists(icon));
    }

    @Test public void managerUsesTheSharedSettingsPageScaffold() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("configureEdgeToEdge();"));
        assertTrue(manager.contains("FrameLayout root = new FrameLayout(this)"));
        assertTrue(manager.contains("root.setFitsSystemWindows(false)"));
        assertTrue(manager.contains("SystemBarDefaults.applyTopInsets(top, dp(12), dp(8), dp(16), dp(8))"));
        assertTrue(manager.contains("BouncyScrollView scroll = new BouncyScrollView(this)"));
        assertTrue(manager.contains("content.setPadding(dp(16), dp(6), dp(16), dp(40))"));
    }

    @Test public void managerShowsImportAsAHandleFreeBottomSheet() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("showImportSheet"));
        assertTrue(manager.contains("加入我的提示词"));
        assertTrue(manager.contains("导入后是你自己的副本"));
        assertTrue(manager.contains("IosDialog.showBottomSheet(this, \"导入提示词\", sheet, 250"));
        assertFalse(manager.contains("window.setWindowAnimations(R.style.BottomSheetDialogAnimation)"));
    }

    @Test public void managerUsesTheSharedDialogForRestoringDefaults() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("IosDialog.showAutoHeight(this, \"恢复默认提示词\""));
        assertTrue(manager.contains("false, false"));
    }

    @Test public void managerCreatesGroupsWithTheSharedNamedDialog() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertFalse(manager.contains("TextView handle = text(\"━\""));
        assertTrue(manager.contains("showCreateGroupDialog"));
        assertTrue(manager.contains("IosDialog.showRequiredChoice"));
        assertTrue(manager.contains("PromptTree.newUserId"));
    }

    @Test public void managerUsesDrawableIconsInsteadOfUnsupportedUnicodeGlyphs() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("R.drawable.ic_prompt_folder"));
        assertTrue(manager.contains("R.drawable.ic_image"));
        Path icon = Paths.get("src/main/res/drawable/ic_prompt_folder.xml");
        if (!Files.exists(icon)) icon = Paths.get("app/src/main/res/drawable/ic_prompt_folder.xml");
        assertTrue(Files.exists(icon));
    }

    @Test public void managerUsesTheIosGreenTextOnlyBadge() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        assertTrue(manager.contains("\"仅文字\".equals(row.appliesLabel) ? Theme.GREEN : Theme.SECONDARY"));
        assertTrue(manager.contains("\"仅文字\".equals(row.appliesLabel) ? Theme.GREEN_BG : TILE_NEUTRAL"));
    }

    @Test public void editorForksSystemsSupportsAnchorsAndSharesOnlyUrl() throws Exception {
        String editor = source("PromptEditActivity.java");
        assertTrue(editor.contains("PromptTree.fork"));
        assertTrue(editor.contains("applyCard(\"文字\""));
        assertTrue(editor.contains("applyCard(\"图片\""));
        assertTrue(editor.contains("Api.sharePage"));
    }

    @Test public void newPromptUsesTheReferenceTitleAndTwoApplyCards() throws Exception {
        String editor = source("PromptEditActivity.java");
        assertTrue(editor.contains(": \"提示词\")"));
        assertTrue(editor.contains("form.addView(appliesCards()"));
        assertTrue(editor.contains("applyCard(\"文字\""));
        assertTrue(editor.contains("applyCard(\"图片\""));
        assertTrue(editor.contains("node.appliesTo.add(\"text\"); node.appliesTo.add(\"image\")"));
    }

    @Test public void newPromptDoesNotRenderSharingUntilTheActionHasBeenSaved() throws Exception {
        String editor = source("PromptEditActivity.java");
        assertTrue(editor.contains("if (id != null) {"));
        assertTrue(editor.contains("loadShareState();"));
    }

    @Test public void editorSavesNewAndExistingPromptsThroughTheStore() throws Exception {
        String editor = source("PromptEditActivity.java");
        assertTrue(editor.contains("creating ? store.add(result, null) : store.replace(original.id, result)"));
    }

    @Test public void importerUsesStrictCodeMergeAndPreview() throws Exception {
        String importer = source("PromptImportActivity.java");
        assertTrue(importer.contains("mergeCodeInput"));
        assertTrue(importer.contains("store.preview"));
        assertTrue(importer.contains("store.importCode"));
    }

    private static String source(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app/src/main/java/com/baixingai/voicedrop", name);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
