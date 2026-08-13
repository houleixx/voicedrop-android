package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.data.WechatMiniProgramShare;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.PopupMenuPosition;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** In-app web reader for a published VoiceDrop book. */
public final class BookReaderActivity extends Activity {
    private WebView web;
    private LoadingStateView loadingState;
    private final ExecutorService shareIo = Executors.newSingleThreadExecutor();

    /** Opens with the same leftward page transition used by the rest of the app. */
    public static void open(Activity source, BookShelfIndex.Book book) {
        Intent intent = new Intent(source, BookReaderActivity.class);
        intent.putExtra("slug", book.slug);
        intent.putExtra("title", book.main);
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
        PageTitleBar titleBar = new PageTitleBar(this, getIntent().getStringExtra("title"),
                this::finishWithPageTransition);
        final FrameLayout[] shareAnchor = {null};
        shareAnchor[0] = titleBar.addIconAction(
                AliIconFont.SHARE_FORWARD, Theme.SECONDARY, "分享到微信",
                () -> showWechatShareMenu(shareAnchor[0]));
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

    private void showWechatShareMenu(View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(0, dp(3), 0, dp(3));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xf9ffffff);
        background.setCornerRadius(dp(16));
        menu.setBackground(background);
        menu.setElevation(dp(8));

        final PopupWindow[] popupRef = {null};
        LinearLayout friend = shareMenuRow("微信好友", AliIconFont.PEOPLE);
        friend.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            shareBookToWechat(false);
        });
        menu.addView(friend);
        menu.addView(shareMenuDivider());

        LinearLayout timeline = shareMenuRow("朋友圈", AliIconFont.SHARE_FORWARD);
        timeline.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            shareBookToWechat(true);
        });
        menu.addView(timeline);

        int popupWidth = dp(260);
        PopupWindow popup = new PopupWindow(menu, popupWidth, -2, true);
        popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));
        popupRef[0] = popup;
        popup.showAsDropDown(anchor,
                PopupMenuPosition.rightAlignedXOffset(anchor.getWidth(), popupWidth) - dp(5),
                dp(10));
    }

    private LinearLayout shareMenuRow(String label, int iconResId) {
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
        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, iconResId, Theme.SECONDARY);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        return row;
    }

    private View shareMenuDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(0xffe0d8cc);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(dp(16), 0, dp(16), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private void shareBookToWechat(boolean timeline) {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return;
        String title = getIntent().getStringExtra("title");
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
        if (web != null) web.destroy();
        shareIo.shutdownNow();
        super.onDestroy();
    }
}
