package com.baixingai.voicedrop;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertTrue;

/** Keeps every book entry on the app's standard page transition pair. */
public final class BookReaderNavigationContractTest {
    @Test public void bookReaderUsesStandardOpenAndBackTransitions() throws Exception {
        String reader = read("BookReaderActivity.java");
        String shelf = read("ui/BooksShelfPanel.java");
        String titleBar = read("ui/PageTitleBar.java");
        assertTrue(reader.contains("source.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)"));
        assertTrue(reader.contains("overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)"));
        assertTrue(reader.contains("this::finishWithPageTransition"));
        assertTrue(shelf.contains("BookReaderActivity.open((Activity) getContext(), book.slug, book.main)"));
        assertTrue(reader.contains("new LoadingStateView(this, \"正在加载书籍…\")"));
        assertTrue(reader.contains("onPageStarted(WebView view"));
        assertTrue(reader.contains("onPageFinished(WebView view"));
        assertTrue(reader.contains("request.isForMainFrame()"));
        assertTrue(reader.contains("new FrameLayout.LayoutParams(-1, dp(180), Gravity.TOP)"));
        assertTrue(reader.contains("loadingParams.topMargin = dp(20)"));
        assertTrue(titleBar.contains("heading.setEllipsize(TextUtils.TruncateAt.END)"));
        assertTrue(titleBar.contains("headingParams.leftMargin = dp(64)"));
        assertTrue(titleBar.contains("headingParams.rightMargin = dp(64)"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
