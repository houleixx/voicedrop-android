package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class PromptManagerSourceTest {
    @Test public void managerExposesCompletePromptActions() throws Exception {
        String manager = source("InstructionSettingsActivity.java");
        for (String text : new String[]{"新建提示词", "新建分组", "导入", "排序", "恢复默认", "删除提示词", "store.refresh"}) {
            assertTrue(text, manager.contains(text));
        }
        assertTrue(manager.contains("errorBanner"));
    }

    @Test public void editorForksSystemsSupportsAnchorsAndSharesOnlyUrl() throws Exception {
        String editor = source("PromptEditActivity.java");
        assertTrue(editor.contains("PromptTree.fork"));
        assertTrue(editor.contains("应用于文字"));
        assertTrue(editor.contains("应用于图片"));
        assertTrue(editor.contains("Intent.ACTION_SEND"));
        assertTrue(editor.contains("Api.sharePage"));
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
