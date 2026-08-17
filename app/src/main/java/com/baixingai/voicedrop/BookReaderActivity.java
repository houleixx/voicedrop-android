package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.core.BookShareTarget;
import com.baixingai.voicedrop.data.WechatMiniProgramShare;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.PopupMenuPosition;
import com.baixingai.voicedrop.ui.RemixIconGlyph;
import com.baixingai.voicedrop.ui.RemixIconView;
import com.baixingai.voicedrop.ui.ShareBottomSheet;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** In-app web reader for a published VoiceDrop book. */
public final class BookReaderActivity extends Activity {
    private static final String MATCH_NATIVE_BACKGROUND_SCRIPT =
            "(function(){var id='voicedrop-native-background';"
                    + "var style=document.getElementById(id);"
                    + "if(!style){style=document.createElement('style');style.id=id;"
                    + "style.textContent='html,body{background:#FAF6EF!important}';"
                    + "document.head.appendChild(style);}})();";
    private WebView web;
    private LoadingStateView loadingState;
    private BookReviseBottomSheet reviseSheet;
    private final ExecutorService shareIo = Executors.newSingleThreadExecutor();
    private String currentPageUrl;
    private String currentPageTitle;

    /** Opens with the same leftward page transition used by the rest of the app. */
    public static void open(Activity source, BookShelfIndex.Book book) {
        Intent intent = new Intent(source, BookReaderActivity.class);
        intent.putExtra("slug", book.slug);
        intent.putExtra("displayTitle", book.main);
        intent.putExtra("shareTitle", book.title);
        intent.putExtra("author", book.author);
        intent.putExtra("cover", book.cover);
        intent.putExtra("coverUrl", book.coverUrl());
        source.startActivity(intent);
        source.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        PageTitleBar titleBar = new PageTitleBar(this, getIntent().getStringExtra("displayTitle"),
                this::finishWithPageTransition);
        FrameLayout moreAction = titleBar.addIconAction(
                AliIconFont.MORE, Theme.SECONDARY, "更多", () -> {});
        moreAction.setOnClickListener(this::showBookMenu);
        page.addView(titleBar, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(Theme.BG);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                showLoading();
            }

            @Override public void onPageFinished(WebView view, String url) {
                updateShareLocation(view, url);
                view.evaluateJavascript(MATCH_NATIVE_BACKGROUND_SCRIPT, null);
                hideLoading();
            }

            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                updateShareLocation(view, url);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    hideLoading();
                    SimpleToast.show(BookReaderActivity.this, "书籍加载失败，请检查网络后重试");
                }
            }
        });
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));
        loadingState = new LoadingStateView(this, "正在加载书籍…");
        loadingState.setBackgroundColor(Theme.BG);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(-1, dp(180), Gravity.TOP);
        loadingParams.topMargin = dp(20);
        content.addView(loadingState, loadingParams);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);

        String slug = getIntent().getStringExtra("slug");
        if (slug != null && slug.matches("[A-Za-z0-9_-]+")) {
            web.loadUrl("https://voicedrop.cn/books/" + slug + "/");
        }
    }

    private void showLoading() {
        if (loadingState == null) return;
        loadingState.setVisibility(View.VISIBLE);
        loadingState.bringToFront();
    }

    private void hideLoading() {
        if (loadingState != null) loadingState.setVisibility(View.GONE);
    }

    private void showBookMenu(View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(0, dp(3), 0, dp(3));
        menu.setBackground(roundedMenuBackground());
        menu.setElevation(dp(8));
        final PopupWindow[] popupRef = {null};

        LinearLayout reviseRow = bookMenuRow("修改这本书", RemixIconGlyph.EDIT, Theme.ACCENT);
        reviseRow.setOnClickListener(ignored -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            openBookRevision();
        });
        menu.addView(reviseRow);
        View divider = new View(this);
        divider.setBackgroundColor(0xffe0d8cc);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(dp(16), 0, dp(16), 0);
        menu.addView(divider, dividerParams);

        LinearLayout shareRow = bookMenuRow("分享", RemixIconGlyph.SHARE_FORWARD, Theme.SECONDARY);
        shareRow.setOnClickListener(ignored -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            showBookShareSheet();
        });
        menu.addView(shareRow);

        int popupWidth = dp(220);
        PopupWindow popup = new PopupWindow(menu, popupWidth, -2, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));
        popup.showAsDropDown(anchor,
                PopupMenuPosition.rightAlignedXOffset(anchor.getWidth(), popupWidth) - dp(5),
                dp(10));
        popupRef[0] = popup;
    }

    private LinearLayout bookMenuRow(String label, String glyph, int iconColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(16), 0);
        row.setMinimumHeight(dp(48));
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(17);
        text.setTextColor(Theme.INK);
        text.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        RemixIconView icon = new RemixIconView(this);
        icon.setIcon(glyph);
        icon.setTextSize(22);
        icon.setTextColor(iconColor);
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        return row;
    }

    private GradientDrawable roundedMenuBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xf9ffffff);
        background.setCornerRadius(dp(16));
        return background;
    }

    private void showBookShareSheet() {
        List<ShareBottomSheet.Item> items = new ArrayList<>();
        items.add(ShareBottomSheet.drawable("微信好友", R.drawable.ic_wechat,
                ShareBottomSheet.WECHAT_GREEN, Color.WHITE, () -> shareBookToWechat(false)));
        items.add(ShareBottomSheet.remix("朋友圈", RemixIconGlyph.CAMERA_LENS_LINE,
                ShareBottomSheet.WECHAT_GREEN, Color.WHITE, () -> shareBookToWechat(true)));
        items.add(ShareBottomSheet.drawable("复制链接", R.drawable.ic_link_flat,
                ShareBottomSheet.NEUTRAL_BACKGROUND, Theme.SECONDARY, 23, this::copyBookLink));
        items.add(ShareBottomSheet.drawable("其它分享", R.drawable.ic_share_forward,
                ShareBottomSheet.NEUTRAL_BACKGROUND, Theme.SECONDARY, 24, this::shareBookWithSystem));
        ShareBottomSheet.show(this, items);
    }

    private void openBookRevision() {
        reviseSheet = BookReviseBottomSheet.show(this, getIntent().getStringExtra("slug"),
                getIntent().getStringExtra("displayTitle"), () -> {
                    reviseSheet = null;
                    if (web != null && !isFinishing() && !isDestroyed()) web.reload();
                });
    }

    @Override protected void onStart() {
        super.onStart();
        if (reviseSheet != null) reviseSheet.onHostStart();
    }

    @Override protected void onStop() {
        if (reviseSheet != null) reviseSheet.onHostStop();
        super.onStop();
    }

    private void copyBookLink() {
        BookShareTarget.Target target = currentShareTarget();
        if (target == null) {
            SimpleToast.show(this, "复制失败");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            SimpleToast.show(this, "复制失败");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("VoiceDrop 书籍链接", target.url));
        SimpleToast.show(this, "链接已复制");
    }

    private void shareBookToWechat(boolean timeline) {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return;
        BookShareTarget.Target target = currentShareTarget();
        if (target == null) return;
        if (!getIntent().getBooleanExtra("cover", false)) {
            showWechatShareResult(sendToWechat(timeline, target, null), target);
            return;
        }
        SimpleToast.show(this, "正在准备微信分享…");
        shareIo.execute(() -> {
            Bitmap cover = loadBookCover(getIntent().getStringExtra("coverUrl"));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (cover != null) cover.recycle();
                    return;
                }
                WechatMiniProgramShare.Result result = sendToWechat(
                        timeline, target, cover);
                if (cover != null) cover.recycle();
                showWechatShareResult(result, target);
            });
        });
    }

    private WechatMiniProgramShare.Result sendToWechat(boolean timeline,
                                                        BookShareTarget.Target target, Bitmap cover) {
        String description = target.chapter ? rootBookTitle() : "VoiceDrop 图书馆 · 点开即读";
        if (timeline) return WechatMiniProgramShare.sendTimeline(
                this, target.title, target.url, cover, description);
        return WechatMiniProgramShare.sendFriend(this, target.title, target.url, cover, description);
    }

    private void shareBookWithSystem() {
        BookShareTarget.Target target = currentShareTarget();
        if (target == null) return;
        String text = target.title + "\n" + target.url;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, target.title);
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "分享这本书"));
    }

    private Bitmap loadBookCover(String coverUrl) {
        if (coverUrl == null || coverUrl.trim().isEmpty()) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(coverUrl).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            return BitmapFactory.decodeStream(connection.getInputStream());
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void updateShareLocation(WebView view, String url) {
        currentPageUrl = url;
        currentPageTitle = view == null ? null : view.getTitle();
    }

    private BookShareTarget.Target currentShareTarget() {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return null;
        String root = "https://voicedrop.cn/books/" + slug + "/";
        String bookTitle = getIntent().getStringExtra("shareTitle");
        if (bookTitle == null || bookTitle.trim().isEmpty()) {
            bookTitle = getIntent().getStringExtra("displayTitle");
        }
        return BookShareTarget.resolve(root, currentPageUrl, currentPageTitle,
                bookTitle, getIntent().getStringExtra("author"));
    }

    private String rootBookTitle() {
        String title = getIntent().getStringExtra("shareTitle");
        if (title == null || title.trim().isEmpty()) title = getIntent().getStringExtra("displayTitle");
        if (title == null || title.trim().isEmpty()) title = "未命名";
        String author = getIntent().getStringExtra("author");
        return "《" + title.trim() + "》"
                + (author == null || author.trim().isEmpty() ? "" : " — " + author.trim());
    }

    private void showWechatShareResult(WechatMiniProgramShare.Result result,
                                       BookShareTarget.Target target) {
        if (result == WechatMiniProgramShare.Result.WECHAT_NOT_INSTALLED) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("VoiceDrop 书籍链接",
                        target.title + "\n" + target.url));
                SimpleToast.show(this, "未安装微信，链接已复制");
                return;
            }
        }
        SimpleToast.show(this, result.message());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else finishWithPageTransition();
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override protected void onDestroy() {
        if (reviseSheet != null) reviseSheet.dismiss();
        if (web != null) web.destroy();
        shareIo.shutdownNow();
        super.onDestroy();
    }
}
