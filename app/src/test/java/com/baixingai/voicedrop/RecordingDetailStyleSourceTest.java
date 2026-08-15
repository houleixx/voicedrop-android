package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class RecordingDetailStyleSourceTest {
    @Test
    public void detailPageExposesBottomSheetStyleRewritePicker() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("选风格"));
        assertTrue(source.contains("String styleLabel = article.style == null ? \"选风格\" : \"v\" + article.style + \" 风格\""));
        assertTrue(source.contains("styleSwitch.setOnClickListener(v -> showStyleVersions(rec, article.style))"));
        assertTrue(source.contains("IosDialog.showBottomSheet(this, \"换个风格重写\", form, 520"));
        assertTrue(source.contains("final int[] selectedStyleVersion"));
        assertTrue(source.contains("new WritingStyleHistoryCache("));
        assertTrue(source.contains("JSONObject cachedStyleHistory = styleCache.read()"));
        assertTrue(source.contains("styleCache.write(fresh)"));
        assertTrue(source.contains("settingsStore.loadStyleHistory()"));
        assertTrue(source.contains("library.versionHistory(rec)"));
        assertTrue(source.contains("generatedStyleVersions(articleHistory)"));
        assertTrue(source.contains("renderStyleRewriteChoices(form, rec, cachedStyleHistory, new HashMap<>()"));
        assertTrue(source.contains("visibleArticleError == null"));
        assertTrue(source.contains("正在检查文章版本…"));
        assertTrue(source.contains("selectedStyleVersion[0] = version"));
        assertTrue(source.contains("requestStyleRewriteOrSwitch(rec, selectedStyleVersion[0], generatedVersions, dialog)"));
        assertTrue(source.contains("styleRewriteButtonText"));
        assertTrue(source.contains("generatedVersions.containsKey(styleVersion) ? \"切换到 v\""));
        assertTrue(source.contains("restyleLoadingDialog = showProcessingLoading(\"处理中...\");"));
        assertTrue(source.contains("loading.setBackground(round(PROCESSING_CARD_COLOR, 10));"));
        assertTrue(source.contains("TextView label = text(message, 14, Color.WHITE, Typeface.NORMAL);"));
        assertFalse(source.contains("showRestylePreview(rec)"));
        assertTrue(source.contains("new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)"));
        assertTrue(source.contains("private static final int BLOCKING_LOADING_SCRIM = 0x33000000"));
        assertTrue(source.contains("root.setBackgroundColor(BLOCKING_LOADING_SCRIM)"));
        assertTrue(source.contains("DialogWindowDefaults.applyModal(dialog.getWindow(), BLOCKING_LOADING_SCRIM, BLOCKING_LOADING_SCRIM, true)"));
        assertFalse(source.contains("configureBlockingLoadingWindow"));
        assertTrue(source.contains("library.restyle(rec, styleVersion)"));
        assertTrue(source.contains("finishRestyle"));
        assertTrue(source.contains("showArticle(rec, updated, false);\n                        toast(\"重写成功\");"));
        assertTrue(source.contains("if (rec.stem().equals(restyleLoadingStem)) finishRestyle(rec, ok);"));
        assertTrue(source.contains("library.patchHead(rec, head)"));
        assertTrue(source.contains("showArticle(rec, nextDoc, false, false)"));
        assertTrue(source.contains("ArticleBody.styleVersion(article.optString(\"body\", \"\"))"));
        assertTrue(source.contains("articleStyleVersion(a)"));
        assertTrue(source.contains("TextView badge = text(\"当前\""));
        assertTrue(source.contains("trimmed.matches(\"\\\\d{10,13}\")"));
        assertTrue(source.contains("Pattern.compile(\"(\\\\d{4})[-/](\\\\d{1,2})[-/](\\\\d{1,2})\")"));
        assertTrue(source.contains("R.drawable.ic_radio_checked_flat"));
        assertTrue(source.contains("R.drawable.ic_radio_unchecked_flat"));
        assertFalse(source.contains("row.setOnClickListener(v -> requestStyleRewrite"));
        assertFalse(source.contains("输入写作风格版本号重挖"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
