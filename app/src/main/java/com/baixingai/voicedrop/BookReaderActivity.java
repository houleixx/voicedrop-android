package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

/** In-app web reader for a published VoiceDrop book. */
public final class BookReaderActivity extends Activity {
    private WebView web;
    private LoadingStateView loadingState;

    /** Opens with the same leftward page transition used by the rest of the app. */
    public static void open(Activity source, String slug, String title) {
        Intent intent = new Intent(source, BookReaderActivity.class);
        intent.putExtra("slug", slug);
        intent.putExtra("title", title);
        source.startActivity(intent);
        source.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xfffffaf0);
        page.addView(new PageTitleBar(this, getIntent().getStringExtra("title"),
                this::finishWithPageTransition), new LinearLayout.LayoutParams(-1, -2));

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
        super.onDestroy();
    }
}
