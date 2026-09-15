package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

/** Public product information; no authentication or payment bridge is exposed to the page. */
public final class WechatProductActivity extends Activity {
    private WebView web;
    private LoadingStateView loading;
    private View error;
    private boolean failed;

    public static void open(Activity source) {
        source.startActivity(new Intent(source, WechatProductActivity.class));
        source.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        page.addView(new PageTitleBar(this, "产品介绍", this::finish),
                new LinearLayout.LayoutParams(-1, -2));
        FrameLayout content = new FrameLayout(this);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);

        web = new WebView(this);
        web.setBackgroundColor(Theme.BG);
        SystemBarDefaults.applyBottomInsets(web, 0, 0, 0, 0);
        WebSettings settings = web.getSettings();
        // The backend serves a self-contained HTML page with no JavaScript dependency.
        settings.setJavaScriptEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));

        loading = new LoadingStateView(this, "正在加载产品介绍…");
        loading.setBackgroundColor(Theme.BG);
        content.addView(loading, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout errorContent = new LinearLayout(this);
        errorContent.setOrientation(LinearLayout.VERTICAL);
        errorContent.setGravity(Gravity.CENTER);
        errorContent.setPadding(dp(24), dp(24), dp(24), dp(24));
        errorContent.setBackgroundColor(Theme.BG);
        TextView message = new TextView(this);
        message.setText("产品介绍暂时无法加载，请稍后重试");
        message.setTextSize(16);
        message.setTextColor(Theme.SECONDARY);
        message.setGravity(Gravity.CENTER);
        errorContent.addView(message);
        TextView retry = new TextView(this);
        retry.setText("重新加载");
        retry.setTextSize(16);
        retry.setTextColor(Theme.AMBER);
        retry.setGravity(Gravity.CENTER);
        retry.setMinHeight(dp(48));
        retry.setPadding(dp(24), dp(12), dp(24), dp(12));
        retry.setOnClickListener(v -> loadProduct());
        errorContent.addView(retry);
        error = errorContent;
        error.setVisibility(View.GONE);
        content.addView(error, new FrameLayout.LayoutParams(-1, -1));
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                boolean blocked = !"https".equalsIgnoreCase(request.getUrl().getScheme());
                if (!blocked && request.isForMainFrame()) prepareLoad();
                return blocked;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                boolean blocked = !"https".equalsIgnoreCase(Uri.parse(url).getScheme());
                if (!blocked) prepareLoad();
                return blocked;
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap icon) {
                // HTTP errors can arrive before the visual page-start callback.
                // Reset only when a navigation is requested, never after an error.
                if (!failed) loading.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                loading.setVisibility(View.GONE);
                if (!failed) error.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError detail) {
                if (request.isForMainFrame()) showError();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) showError();
            }
        });
        loadProduct();
    }

    private void prepareLoad() {
        failed = false;
        error.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
    }

    private void loadProduct() {
        prepareLoad();
        web.loadUrl(Api.wechatProductUrl());
    }

    private void showError() {
        failed = true;
        loading.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
    }

    @Override public void onBackPressed() {
        if (!failed && web != null && web.canGoBack()) web.goBack();
        else finish();
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            ((FrameLayout) web.getParent()).removeView(web);
            web.setWebViewClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
