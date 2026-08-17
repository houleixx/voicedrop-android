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
        assertTrue(shelf.contains("BookReaderActivity.open((Activity) getContext(), book)"));
        assertTrue(reader.contains("AliIconFont.MORE, Theme.SECONDARY, \"更多\""));
        assertTrue(reader.contains("moreAction.setOnClickListener(this::showBookMenu)"));
        assertTrue(reader.contains("bookMenuRow(\"修改这本书\", RemixIconGlyph.EDIT"));
        assertTrue(reader.contains("bookMenuRow(\"分享\", RemixIconGlyph.SHARE_FORWARD"));
        String bookMenu = reader.substring(reader.indexOf("private void showBookMenu(View anchor)"),
                reader.indexOf("private void showBookShareSheet()"));
        assertTrue(!bookMenu.contains("微信好友"));
        assertTrue(!bookMenu.contains("朋友圈"));
        assertTrue(reader.contains("BookReviseBottomSheet.show"));
        assertTrue(reader.contains("web.reload()"));
        assertTrue(reader.contains("ShareBottomSheet.drawable(\"微信好友\""));
        assertTrue(reader.contains("ShareBottomSheet.remix(\"朋友圈\""));
        assertTrue(reader.contains("ShareBottomSheet.drawable(\"复制链接\""));
        assertTrue(reader.contains("ShareBottomSheet.drawable(\"其它分享\""));
        assertTrue(reader.contains("this::copyBookLink"));
        assertTrue(reader.contains("ClipData.newPlainText(\"VoiceDrop 书籍链接\", target.url)"));
        assertTrue(reader.contains("shareBookWithSystem()"));
        assertTrue(reader.contains("new Intent(Intent.ACTION_SEND)"));
        assertTrue(reader.contains("Intent.createChooser(send, \"分享这本书\")"));
        assertTrue(reader.contains("intent.putExtra(\"displayTitle\", book.main)"));
        assertTrue(reader.contains("intent.putExtra(\"shareTitle\", book.title)"));
        assertTrue(reader.contains("new PageTitleBar(this, getIntent().getStringExtra(\"displayTitle\")"));
        assertTrue(reader.contains("BookShareTarget.resolve(root, currentPageUrl, currentPageTitle"));
        assertTrue(!reader.contains("intent.putExtra(\"title\", book.main)"));
        assertTrue(reader.contains("new PopupWindow(menu, popupWidth, -2, true)"));
        assertTrue(reader.contains("popup.showAsDropDown(anchor"));
        assertTrue(reader.contains("dividerParams.setMargins(dp(16), 0, dp(16), 0)"));
        assertTrue(reader.contains("WechatMiniProgramShare.sendFriend(this, target.title, target.url, cover, description)"));
        assertTrue(reader.contains("WechatMiniProgramShare.sendTimeline("));
        assertTrue(!reader.contains("WechatMiniProgramShare.bookPath("));
        assertTrue(reader.contains("loadBookCover(getIntent().getStringExtra(\"coverUrl\"))"));
        assertTrue(reader.contains("doUpdateVisitedHistory(WebView view"));
        assertTrue(reader.contains("shareIo.shutdownNow()"));
        assertTrue(titleBar.contains("public FrameLayout addIconAction("));
        assertTrue(titleBar.contains("new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)"));
        assertTrue(titleBar.contains("touch.addView(button, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))"));
        assertTrue(reader.contains("new LoadingStateView(this, \"正在加载书籍…\")"));
        assertTrue(reader.contains("page.setBackgroundColor(Theme.BG)"));
        assertTrue(reader.contains("web.setBackgroundColor(Theme.BG)"));
        assertTrue(reader.contains("loadingState.setBackgroundColor(Theme.BG)"));
        assertTrue(!reader.contains("0xfffffaf0"));
        assertTrue(reader.contains("onPageStarted(WebView view"));
        assertTrue(reader.contains("onPageFinished(WebView view"));
        assertTrue(reader.contains("html,body{background:#FAF6EF!important}"));
        assertTrue(reader.contains("view.evaluateJavascript(MATCH_NATIVE_BACKGROUND_SCRIPT, null)"));
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
