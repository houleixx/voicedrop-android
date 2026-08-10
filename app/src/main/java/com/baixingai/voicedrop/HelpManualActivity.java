package com.baixingai.voicedrop;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

public final class HelpManualActivity extends Activity {
    static final String HELP_MANUAL_URL = "https://voicedrop.cn/help/manual/";
    static final String[] SECTION_IDS = {"ch1", "ch2", "ch3", "ch4", "ch5", "ch6", "ch7", "ch8"};
    static final String[] SECTION_LABELS = {"1 上手", "2 录音", "3 改稿", "4 发布", "5 社区", "6 文风", "7 账号", "8 FAQ"};

    private static final String MANUAL_UI_SCRIPT =
            "(function(){"
                    + "var old=document.getElementById('vd-native-manual');"
                    + "if(!old){var style=document.createElement('style');style.id='vd-native-manual';"
                    + "style.textContent='header.site,.hero,nav.toc,footer.site{display:none!important}'"
                    + "+'.layout{display:block!important;max-width:780px!important;margin:0 auto!important;padding:5px 20px 72px!important}'"
                    + "+'.chapter{scroll-margin-top:12px!important}'"
                    + "+'.chapter:first-child{padding-top:0!important}'"
                    + "+'.chapter:first-child h2{margin-top:4px!important}'"
                    + "+'body{background:#faf6ef!important}';document.head.appendChild(style);}"
                    + "var sections=['ch1','ch2','ch3','ch4','ch5','ch6','ch7','ch8'].map(function(id){return document.getElementById(id);});"
                    + "window.__vdManualReport=function(){var active=0;for(var i=0;i<sections.length;i++){"
                    + "if(sections[i]&&sections[i].getBoundingClientRect().top<=96){active=i;}}"
                    + "if(window.__vdManualActive!==active){window.__vdManualActive=active;VoiceDropManual.onSectionChanged(active);}};"
                    + "if(!window.__vdManualListening){window.__vdManualListening=true;window.addEventListener('scroll',function(){"
                    + "if(!window.__vdManualTick){window.__vdManualTick=true;requestAnimationFrame(function(){window.__vdManualTick=false;window.__vdManualReport();});}});}"
                    + "window.__vdManualReport();"
                    + "})()";

    private WebView webView;
    private HorizontalScrollView sectionScroll;
    private TextView[] sectionButtons;
    private boolean pageReady;
    private int pendingSection = -1;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureEdgeToEdge();

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        page.addView(new PageTitleBar(this, "使用手册", this::finishWithPageTransition),
                new LinearLayout.LayoutParams(-1, -2));
        page.addView(buildSectionBar(), new LinearLayout.LayoutParams(-1, dp(52)));

        FrameLayout webFrame = new FrameLayout(this);
        SystemBarDefaults.applyBottomInsets(webFrame, 0, 0, 0, 0);
        page.addView(webFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        webView = new WebView(this);
        webView.setBackgroundColor(Theme.BG);
        webFrame.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        webView.addJavascriptInterface(new ManualJavascriptBridge(), "VoiceDropManual");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                pageReady = false;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (!isManualUrl(Uri.parse(url))) return;
                pageReady = true;
                view.evaluateJavascript(MANUAL_UI_SCRIPT, null);
                int section = pendingSection;
                pendingSection = -1;
                if (section >= 0) scrollToSection(section);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeLink(request.getUrl());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeLink(Uri.parse(url));
            }
        });
        setContentView(root);
        webView.loadUrl(HELP_MANUAL_URL);
    }

    private HorizontalScrollView buildSectionBar() {
        sectionScroll = new HorizontalScrollView(this);
        sectionScroll.setHorizontalScrollBarEnabled(false);
        sectionScroll.setFillViewport(false);
        sectionScroll.setBackgroundColor(Theme.BG);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(4), dp(10), dp(8));
        sectionScroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));

        sectionButtons = new TextView[SECTION_LABELS.length];
        for (int i = 0; i < SECTION_LABELS.length; i++) {
            final int index = i;
            TextView button = new TextView(this);
            button.setText(SECTION_LABELS[i]);
            button.setTextSize(13);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setMinWidth(dp(64));
            button.setPadding(dp(12), 0, dp(12), 0);
            button.setClickable(true);
            button.setFocusable(true);
            button.setContentDescription("跳到" + SECTION_LABELS[i]);
            button.setOnClickListener(view -> scrollToSection(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
            params.setMargins(0, 0, dp(6), 0);
            row.addView(button, params);
            sectionButtons[i] = button;
        }
        selectSection(0);
        return sectionScroll;
    }

    private void scrollToSection(int index) {
        if (index < 0 || index >= SECTION_IDS.length) return;
        selectSection(index);
        if (!pageReady) {
            pendingSection = index;
            return;
        }
        String script = "document.getElementById('" + SECTION_IDS[index]
                + "').scrollIntoView({behavior:'smooth',block:'start'})";
        webView.evaluateJavascript(script, null);
    }

    private void selectSection(int index) {
        if (sectionButtons == null || index < 0 || index >= sectionButtons.length) return;
        for (int i = 0; i < sectionButtons.length; i++) {
            boolean selected = i == index;
            TextView button = sectionButtons[i];
            button.setTextColor(selected ? Theme.ACCENT : Theme.SECONDARY);
            button.setBackground(chipBackground(selected));
            button.setSelected(selected);
        }
        TextView selected = sectionButtons[index];
        sectionScroll.post(() -> sectionScroll.smoothScrollTo(
                Math.max(0, selected.getLeft() - dp(16)), 0));
    }

    private GradientDrawable chipBackground(boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? Theme.ACCENT_SOFT : Theme.CARD);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), selected ? 0xffedc7b8 : Theme.BORDER_CHROME);
        return background;
    }

    private boolean routeLink(Uri uri) {
        if (isManualUrl(uri)) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            SimpleToast.show(this, "无法打开这个链接");
        }
        return true;
    }

    private boolean isManualUrl(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        return ((host.equals("voicedrop.cn") || host.equals("www.voicedrop.cn"))
                && path.equals("/help/manual/"))
                || ((host.equals("jianshuo.dev") || host.equals("www.jianshuo.dev"))
                && path.equals("/voicedrop/help/manual/"));
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finishWithPageTransition();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("VoiceDropManual");
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void configureEdgeToEdge() {
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_out_right);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ManualJavascriptBridge {
        @JavascriptInterface public void onSectionChanged(int index) {
            runOnUiThread(() -> selectSection(index));
        }
    }
}
