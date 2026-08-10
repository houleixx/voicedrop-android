package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FeedbackAndManualUiSourceTest {
    @Test
    public void feedbackScrollAlwaysExposesPlatformStretchOverscroll() throws Exception {
        String source = readSource("FeedbackActivity.java");

        assertTrue(source.contains("scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);"));
    }

    @Test
    public void manualUsesNativeViewsAndBundledMarkdownInsteadOfAWebView() throws Exception {
        String source = readSource("HelpManualActivity.java");

        assertTrue(source.contains("ManualMarkdown.parse(readBundledManual())"));
        assertTrue(source.contains("getAssets().open(MANUAL_ASSET)"));
        assertTrue(source.contains("new BouncyScrollView(this)"));
        assertFalse(source.contains("android.webkit.WebView"));
        assertFalse(source.contains("loadUrl("));
    }

    @Test
    public void manualTablesFillTheAvailableContentWidth() throws Exception {
        String source = readSource("HelpManualActivity.java");

        assertTrue(source.contains("table.setStretchAllColumns(true);"));
        assertTrue(source.contains("horizontal.setFillViewport(true);"));
        assertTrue(source.contains(
                "horizontal.addView(table, new HorizontalScrollView.LayoutParams(-1, -2));"));
    }

    private static String readSource(String fileName) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", fileName);
        if (!Files.exists(path)) {
            path = Paths.get("app/src/main/java/com/baixingai/voicedrop", fileName);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
