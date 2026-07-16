package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class RecordingDetailInlineEditSourceTest {
    @Test
    public void longPressMenuOffersVerbatimInlineEditing() throws Exception {
        String detail = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String store = readSource("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");

        assertTrue(detail.contains("new LocalMenuRow(\"编辑\""));
        assertTrue(detail.contains("startInlineParagraphEdit"));
        assertTrue(detail.contains("finishInlineParagraphEdit"));
        assertTrue(detail.contains("cancelInlineParagraphEdit"));
        assertTrue(detail.contains("ArticleBody.replacingRenderedLine"));
        assertTrue(detail.contains("library.saveArticles"));
        assertTrue(detail.contains("InputMethodManager.SHOW_IMPLICIT"));
        assertTrue(detail.contains("setSingleLine(false)"));
        assertTrue(detail.contains("refreshArticleHistoryState(rec)"));
        assertTrue(store.contains("public boolean saveArticles"));
        assertTrue(store.contains("http.putBytes"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
