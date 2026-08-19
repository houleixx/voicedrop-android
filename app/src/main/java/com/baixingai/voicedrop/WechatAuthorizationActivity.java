package com.baixingai.voicedrop;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.PixelCopy;
import android.graphics.Rect;
import android.view.Gravity;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.core.WechatAuthorizationHandoff;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.ui.WechatShareLoadingDialog;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

/** WebView wrapper for the third-party-platform QR authorization page. */
public final class WechatAuthorizationActivity extends Activity {
    private static final int WRITE_IMAGE = 41;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WebView webView;
    private TextView title;
    private TextView hint;
    private WechatShareLoadingDialog authorizationLoading;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        setContentView(page);

        FrameLayout top = new FrameLayout(this);
        SystemBarDefaults.applyTopInsets(top, dp(12), dp(8), dp(16), dp(8));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout backTouch = standardBackButton();
        top.addView(backTouch, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        title = text("授权公众号", 22, Theme.INK, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new FrameLayout.LayoutParams(-1, dp(48), Gravity.CENTER));
        TextView capture = text("截图二维码", 14, 0xffffffff, Typeface.BOLD);
        capture.setGravity(Gravity.CENTER);
        capture.setBackground(round(Theme.ACCENT, 10));
        capture.setElevation(dp(1));
        capture.setOnClickListener(v -> saveScreenshot());
        top.addView(capture, new FrameLayout.LayoutParams(dp(104), dp(40), Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        webView = new WebView(this);
        configure(webView);
        // The authorization page only needs to show its QR code. Keeping this
        // compact leaves the scan instructions visible without scrolling.
        page.addView(webView, new LinearLayout.LayoutParams(-1, dp(360)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        // Insets replace a view's padding, so pass the desired horizontal gutter
        // as the base inset rather than setting it before applyBottomInsets().
        SystemBarDefaults.applyBottomInsets(bottom, dp(12), dp(12), dp(12), dp(16));
        hint = text("正在检查公众号连接状态…", 13, Theme.SECONDARY, Typeface.NORMAL);
        hint.setLineSpacing(dp(3), 1f);
        bottom.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        page.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        authorizationLoading = WechatShareLoadingDialog.show(this, "二维码加载中...");
        refreshAuthorizationStatus();
    }

    /** Refresh here as well, so a stale settings page cannot label a reauthorization as a first authorization. */
    private void refreshAuthorizationStatus() {
        io.execute(() -> {
            boolean connected = false;
            try {
                HttpClient.Response response = new HttpClient().get(
                        Api.filesBase() + "/wechat/bind-status", new AuthStore(this).bearer());
                connected = response.ok() && new JSONObject(response.text()).optBoolean("connected", false);
            } catch (Exception ignored) { }
            final boolean isConnected = connected;
            runOnUiThread(() -> requestAuthorizationPage(isConnected));
        });
    }

    private void requestAuthorizationPage(boolean reauthorization) {
        title.setText(reauthorization ? "重新授权公众号" : "授权公众号");
        hint.setText((reauthorization ? "重新授权会更新当前公众号的授权信息。\n\n" : "")
                + "进入微信授权二维码页后：\n1. 点右上角「截图二维码」，保存当前页面\n2. 打开微信「扫一扫」后，点击页面上的「相册」，选择刚才保存的二维码\n\n授权过程中请勿关闭此页面；看到“授权成功”后，再返回 VoiceDrop。");
        io.execute(() -> {
            try {
                HttpClient.Response response = new HttpClient().postJson(
                        Api.filesBase() + "/wechat/authorization", new AuthStore(this).bearer(),
                        "{}".getBytes(StandardCharsets.UTF_8));
                String scanUrl = response.ok() ? new JSONObject(response.text()).optString("scan_url", "") : "";
                if (scanUrl.isEmpty()) throw new IllegalStateException("missing scan_url");
                HttpClient.Response scanResponse = new HttpClient().get(scanUrl, null);
                if (!scanResponse.ok()) throw new IllegalStateException("scan page unavailable");
                String handoffHtml = WechatAuthorizationHandoff.handoffHtml(scanUrl, scanResponse.text());
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.loadDataWithBaseURL(scanUrl, handoffHtml, "text/html", "UTF-8", null);
                    }
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    hideAuthorizationLoading();
                    toast("无法打开公众号授权页，请稍后重试");
                });
            }
        });
    }

    private void configure(WebView view) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return allow(r.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return allow(Uri.parse(url)); }
            @Override public void onPageFinished(WebView v, String url) {
                centerQrWhenReady(v, 0);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    hideAuthorizationLoading();
                    toast("授权页加载失败，请检查网络后重试");
                }
            }
        });
    }

    /** The WeChat page is desktop-oriented; place its QR image in the phone viewport. */
    private void centerQrWhenReady(WebView view, int attempt) {
        view.postDelayed(() -> view.evaluateJavascript(
                "(function(){var all=[].slice.call(document.querySelectorAll('img,canvas,svg,[class*=qr],[id*=qr],[class*=code],[id*=code]'));"
                        + "var qr=all.map(function(e){return {e:e,r:e.getBoundingClientRect()};})"
                        + ".filter(function(x){return x.r.width>=100&&x.r.height>=100&&x.r.width/x.r.height>.65&&x.r.width/x.r.height<1.35;})"
                        + ".sort(function(a,b){return b.r.width*b.r.height-a.r.width*a.r.height;})[0];"
                        + "if(!qr)return false;var r=qr.r,root=document.scrollingElement||document.documentElement||document.body;"
                        + "var x=Math.max(0,root.scrollLeft+r.left-(window.innerWidth-r.width)/2);"
                        + "var y=Math.max(0,root.scrollTop+r.top-(window.innerHeight-r.height)/2);"
                        + "root.scrollLeft=x;root.scrollTop=y;document.body.scrollLeft=x;document.body.scrollTop=y;"
                        + "document.documentElement.scrollLeft=x;document.documentElement.scrollTop=y;window.scrollTo(x,y);return true;})()",
                result -> {
                    if ("true".equals(result)) {
                        hideAuthorizationLoading();
                    } else if (attempt < 6) {
                        centerQrWhenReady(view, attempt + 1);
                    } else {
                        hideAuthorizationLoading();
                    }
                }), 500L + attempt * 350L);
    }

    private void hideAuthorizationLoading() {
        if (authorizationLoading == null) return;
        if (authorizationLoading.isShowing()) authorizationLoading.dismiss();
        authorizationLoading = null;
    }

    /** Keep the authorization handoff in its expected hosts; all other links stay outside the app. */
    private boolean allow(Uri uri) {
        String host = uri == null ? "" : uri.getHost();
        if (Api.CN_HOST.equalsIgnoreCase(host) || Api.CF_HOST.equalsIgnoreCase(host)
                || "mp.weixin.qq.com".equalsIgnoreCase(host)) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
        return true;
    }

    private void saveScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_IMAGE);
            return;
        }
        captureAndSave();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == WRITE_IMAGE && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) captureAndSave();
        else if (requestCode == WRITE_IMAGE) toast("需要相册写入权限才能保存二维码截图");
    }

    private void captureAndSave() {
        if (webView == null || webView.getWidth() <= 0 || webView.getHeight() <= 0) { toast("二维码还在加载，请稍后再试"); return; }
        Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.ARGB_8888);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int[] location = new int[2];
            webView.getLocationInWindow(location);
            Rect source = new Rect(location[0], location[1], location[0] + webView.getWidth(), location[1] + webView.getHeight());
            PixelCopy.request(getWindow(), source, bitmap, result -> {
                if (result == PixelCopy.SUCCESS) saveBitmap(bitmap);
                else { bitmap.recycle(); captureWebViewFallback(); }
            }, new Handler(Looper.getMainLooper()));
        } else {
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);
            saveBitmap(bitmap);
        }
    }

    private void captureWebViewFallback() {
        if (webView == null) return;
        Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.ARGB_8888);
        webView.draw(new Canvas(bitmap));
        saveBitmap(bitmap);
    }

    private void saveBitmap(Bitmap bitmap) {
        io.execute(() -> {
            Uri uri = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "VoiceDrop-公众号授权-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VoiceDrop"); values.put(MediaStore.Images.Media.IS_PENDING, 1); }
                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("无法创建图片");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) { if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("无法写入图片"); }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { ContentValues done = new ContentValues(); done.put(MediaStore.Images.Media.IS_PENDING, 0); getContentResolver().update(uri, done, null, null); }
                runOnUiThread(this::showWechatScanPrompt);
            } catch (Exception e) {
                if (uri != null) getContentResolver().delete(uri, null, null);
                runOnUiThread(() -> toast("截图保存失败，请使用系统截图"));
            } finally { bitmap.recycle(); }
        });
    }

    private void showWechatScanPrompt() {
        IosDialog.showConfirmation(this, "二维码已保存到相册",
                "请打开微信，进入「扫一扫」后点击页面上的「相册」，选择刚保存的二维码完成公众号授权。\n\n现在打开微信吗？",
                "打开微信", this::openWechat, "暂不打开", null);
    }

    private void openWechat() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
        if (launch == null) { toast("未检测到微信，请安装微信后完成授权"); return; }
        try { startActivity(launch); } catch (Exception ignored) { toast("无法打开微信，请手动打开后扫一扫"); }
    }
    private FrameLayout standardBackButton() {
        FrameLayout touch = new FrameLayout(this);
        touch.setClickable(true);
        FrameLayout back = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.CARD); bg.setCornerRadius(dp(11)); bg.setStroke(dp(1), 0xffe0d8cc);
        back.setBackground(bg); back.setElevation(dp(2));
        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, AliIconFont.BACK, Theme.INK);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        back.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        touch.addView(back, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        touch.setOnClickListener(v -> finishWithPageTransition());
        return touch;
    }
    private TextView text(String v, int sp, int c, int style) { TextView t = new TextView(this); t.setText(v); t.setTextSize(sp); t.setTextColor(c); t.setTypeface(Typeface.DEFAULT, style); return t; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { SimpleToast.show(this, message); }
    @Override public void onBackPressed() { finishWithPageTransition(); }
    private void finishWithPageTransition() { finish(); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); }
    @Override protected void onDestroy() { hideAuthorizationLoading(); if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; } io.shutdownNow(); super.onDestroy(); }
}
