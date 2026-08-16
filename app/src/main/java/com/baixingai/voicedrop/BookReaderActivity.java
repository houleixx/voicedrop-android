package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.data.WechatMiniProgramShare;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.RemixIconGlyph;
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
    private WebView web;
    private LoadingStateView loadingState;
    private BookReviseBottomSheet reviseSheet;
    private final ExecutorService shareIo = Executors.newSingleThreadExecutor();

    /** Opens with the same leftward page transition used by the rest of the app. */
    public static void open(Activity source, BookShelfIndex.Book book) {
        Intent intent = new Intent(source, BookReaderActivity.class);
        intent.putExtra("slug", book.slug);
        intent.putExtra("displayTitle", book.main);
        intent.putExtra("shareTitle", book.title);
        intent.putExtra("author", book.author);
        intent.putExtra("cover", book.cover);
        source.startActivity(intent);
        source.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xfffffaf0);
        PageTitleBar titleBar = new PageTitleBar(this, getIntent().getStringExtra("displayTitle"),
                this::finishWithPageTransition);
        titleBar.addIconAction(
                AliIconFont.MORE, Theme.SECONDARY, "更多",
                this::showBookMenu);
        page.addView(titleBar, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(0xfffffaf0);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                showLoading();
            }

            @Override public void onPageFinished(WebView view, String url) {
                hideLoading();
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
        loadingState.setBackgroundColor(0xfffffaf0);
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

    private void showBookMenu() {
        List<ShareBottomSheet.Item> items = new ArrayList<>();
        items.add(ShareBottomSheet.remix("修改这本书", RemixIconGlyph.EDIT,
                ShareBottomSheet.NEUTRAL_BACKGROUND, Theme.ACCENT, this::openBookRevision));
        items.add(ShareBottomSheet.remix("分享", RemixIconGlyph.SHARE_FORWARD,
                ShareBottomSheet.NEUTRAL_BACKGROUND, Theme.SECONDARY, this::showBookShareSheet));
        ShareBottomSheet.show(this, items);
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
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) {
            SimpleToast.show(this, "复制失败");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            SimpleToast.show(this, "复制失败");
            return;
        }
        String url = "https://voicedrop.cn/books/" + slug + "/";
        clipboard.setPrimaryClip(ClipData.newPlainText("VoiceDrop 书籍链接", url));
        SimpleToast.show(this, "链接已复制");
    }

    private void shareBookToWechat(boolean timeline) {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return;
        String shareTitle = getIntent().getStringExtra("shareTitle");
        final String title = shareTitle == null || shareTitle.trim().isEmpty()
                ? getIntent().getStringExtra("displayTitle") : shareTitle;
        String url = "https://voicedrop.cn/books/" + slug + "/";
        if (!getIntent().getBooleanExtra("cover", false)) {
            showWechatShareResult(sendToWechat(timeline, title, url, null));
            return;
        }
        SimpleToast.show(this, "正在准备微信分享…");
        shareIo.execute(() -> {
            Bitmap cover = loadBookCover(url + "cover.jpg");
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (cover != null) cover.recycle();
                    return;
                }
                WechatMiniProgramShare.Result result = sendToWechat(
                        timeline, title, url, cover);
                if (cover != null) cover.recycle();
                showWechatShareResult(result);
            });
        });
    }

    private WechatMiniProgramShare.Result sendToWechat(boolean timeline, String title,
                                                        String url, Bitmap cover) {
        if (timeline) return WechatMiniProgramShare.sendTimeline(this, title, url, cover);
        return WechatMiniProgramShare.sendFriend(this, title, url, cover);
    }

    private void shareBookWithSystem() {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return;
        String title = getIntent().getStringExtra("shareTitle");
        if (title == null || title.trim().isEmpty()) {
            title = getIntent().getStringExtra("displayTitle");
        }
        String author = getIntent().getStringExtra("author");
        String safeTitle = title == null || title.trim().isEmpty() ? "未命名" : title.trim();
        String safeAuthor = author == null ? "" : author.trim();
        String url = "https://voicedrop.cn/books/" + slug + "/";
        String text = "《" + safeTitle + "》"
                + (safeAuthor.isEmpty() ? "" : " — " + safeAuthor) + "\n" + url;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, safeTitle);
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "分享这本书"));
    }

    private Bitmap loadBookCover(String coverUrl) {
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

    private void showWechatShareResult(WechatMiniProgramShare.Result result) {
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
