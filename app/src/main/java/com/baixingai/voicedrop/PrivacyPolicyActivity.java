package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.baixingai.voicedrop.data.PrivacyConsent;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.ui.SystemBarDefaults;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class PrivacyPolicyActivity extends Activity {
    private WebView webView;
    private FrameLayout webContainer;
    private ProgressBar progress;
    private View errorView;

    public static void open(Activity source) {
        source.startActivity(new Intent(source, PrivacyPolicyActivity.class));
        source.overridePendingTransition(R.anim.slide_in_right, R.anim.stay);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        setContentView(page);

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, -2));

        webContainer = new FrameLayout(this);
        page.addView(webContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        webView = new WebView(this);
        configureWebView(webView);
        webContainer.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        webContainer.addView(progress,
                new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));

        loadPolicy();
    }

    private View buildTopBar() {
        FrameLayout top = new FrameLayout(this);
        SystemBarDefaults.applyTopInsets(top, dp(12), dp(8), dp(16), dp(8));
        top.setBackgroundColor(Theme.BG);

        FrameLayout backTouch = new FrameLayout(this);
        backTouch.setClickable(true);
        backTouch.setContentDescription("返回");
        FrameLayout back = new FrameLayout(this);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(Theme.CARD);
        backBg.setCornerRadius(dp(11));
        backBg.setStroke(dp(1), 0xffe0d8cc);
        back.setBackground(backBg);
        back.setElevation(dp(2));
        ImageView backIcon = new ImageView(this);
        AliIconFont.apply(backIcon, AliIconFont.BACK, Theme.INK);
        backIcon.setScaleType(ImageView.ScaleType.CENTER);
        back.addView(backIcon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        backTouch.addView(back, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        backTouch.setOnClickListener(v -> finish());
        top.addView(backTouch, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL));

        TextView title = new TextView(this);
        title.setText("隐私政策");
        title.setTextSize(20);
        title.setTextColor(Theme.INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER_HORIZONTAL));
        return top;
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        view.setBackgroundColor(Theme.BG);
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !PrivacyConsent.POLICY_URL.equals(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !PrivacyConsent.POLICY_URL.equals(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (PrivacyConsent.POLICY_URL.equals(url)) {
                    progress.setVisibility(View.GONE);
                    clearError();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }
        });
    }

    private void showError() {
        progress.setVisibility(View.GONE);
        if (errorView != null) return;

        LinearLayout error = new LinearLayout(this);
        error.setOrientation(LinearLayout.VERTICAL);
        error.setGravity(Gravity.CENTER);
        error.setBackgroundColor(Theme.BG);
        error.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView message = new TextView(this);
        message.setText("隐私政策加载失败");
        message.setTextSize(16);
        message.setTextColor(Theme.SECONDARY);
        message.setGravity(Gravity.CENTER);
        error.addView(message);

        TextView retry = new TextView(this);
        retry.setText("重新加载");
        retry.setTextSize(16);
        retry.setTextColor(Theme.RED);
        retry.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(24), dp(14), dp(24), dp(14));
        retry.setOnClickListener(v -> reload());
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(-2, -2);
        retryLp.topMargin = dp(10);
        error.addView(retry, retryLp);

        errorView = error;
        webContainer.addView(error, new FrameLayout.LayoutParams(-1, -1));
    }

    private void reload() {
        clearError();
        progress.setVisibility(View.VISIBLE);
        loadPolicy();
    }

    private void loadPolicy() {
        try (InputStream input = getAssets().open(PrivacyConsent.POLICY_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) html.append(line).append('\n');
            webView.loadDataWithBaseURL(PrivacyConsent.POLICY_URL, html.toString(),
                    "text/html", "UTF-8", null);
        } catch (Exception error) {
            showError();
        }
    }

    private void clearError() {
        if (errorView == null) return;
        webContainer.removeView(errorView);
        errorView = null;
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_out_right);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
