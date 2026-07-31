package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Connects a VoiceDrop data space to a WeChat Official Account. */
public class WechatSettingsActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout connectionContent;
    private FrameLayout iconSlot;
    private TextView stateIcon;
    private ImageView successIcon;
    private TextView status;
    private TextView detail;
    private TextView primary;
    private LinearLayout accountCard;
    private TextView accountName;
    private TextView accountMeta;
    private TextView note;
    private boolean connected;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout top = new FrameLayout(this);
        SystemBarDefaults.applyTopInsets(top, dp(12), dp(8), dp(16), dp(8));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout backTouch = new FrameLayout(this);
        backTouch.setClickable(true);
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
        backTouch.setOnClickListener(v -> finishWithPageTransition());
        top.addView(backTouch, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        TextView title = text("微信公众号", 22, Theme.INK, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new FrameLayout.LayoutParams(-1, dp(48), Gravity.CENTER));

        BouncyScrollView scroll = new BouncyScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        SystemBarDefaults.applyBottomInsets(content, dp(20), dp(22), dp(20), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        connectionContent = new LinearLayout(this);
        connectionContent.setOrientation(LinearLayout.VERTICAL);
        connectionContent.setVisibility(LinearLayout.INVISIBLE);
        content.addView(connectionContent, new LinearLayout.LayoutParams(-1, -2));

        iconSlot = new FrameLayout(this);
        connectionContent.addView(iconSlot, new LinearLayout.LayoutParams(-1, dp(62)));
        stateIcon = text("✈", 42, Theme.ACCENT, Typeface.NORMAL);
        stateIcon.setGravity(Gravity.CENTER);
        iconSlot.addView(stateIcon, new FrameLayout.LayoutParams(-1, -1));
        successIcon = new ImageView(this);
        successIcon.setImageResource(R.drawable.ic_check_flat);
        successIcon.setColorFilter(0xffffffff);
        successIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        successIcon.setPadding(dp(12), dp(12), dp(12), dp(12));
        successIcon.setBackground(round(Theme.GREEN, 28));
        iconSlot.addView(successIcon, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        successIcon.setVisibility(ImageView.GONE);
        status = text("正在检查绑定状态…", 22, Theme.INK, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        connectionContent.addView(status, new LinearLayout.LayoutParams(-1, -2));
        detail = text("连接后，VoiceDrop 可以将文章保存到你的公众号草稿箱。不会自动群发。", 15, Theme.SECONDARY, Typeface.NORMAL);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.setMargins(dp(18), dp(10), dp(18), dp(28));
        connectionContent.addView(detail, detailLp);

        accountCard = new LinearLayout(this);
        accountCard.setOrientation(LinearLayout.VERTICAL);
        accountCard.setBackground(strokedRound(0xfff1f7f2, 14, 0xffd3e4d7));
        accountCard.setPadding(dp(18), dp(14), dp(18), dp(14));
        TextView accountLabel = text("已授权账号", 12, Theme.GREEN, Typeface.BOLD);
        accountCard.addView(accountLabel, new LinearLayout.LayoutParams(-1, -2));
        accountName = text("", 18, Theme.INK, Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = dp(4);
        accountCard.addView(accountName, nameLp);
        accountMeta = text("草稿箱已就绪 · VoiceDrop 不会自动群发", 13, Theme.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
        metaLp.topMargin = dp(5);
        accountCard.addView(accountMeta, metaLp);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(dp(12), dp(-10), dp(12), dp(24));
        connectionContent.addView(accountCard, cardLp);
        accountCard.setVisibility(LinearLayout.GONE);

        primary = text("连接微信公众号", 18, 0xffffffff, Typeface.BOLD);
        primary.setGravity(Gravity.CENTER);
        primary.setBackground(round(0xffdf5d49, 10));
        primary.setOnClickListener(v -> { if (connected) disconnect(); else openAuthorization(); });
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(-1, dp(56));
        primaryLp.setMargins(dp(12), 0, dp(12), 0);
        connectionContent.addView(primary, primaryLp);

        note = text("1. 在授权页面打开二维码后，点右上角截图保存\n2. 打开微信「扫一扫」后，点击页面上的「相册」，选择刚才保存的二维码完成授权", 13, Theme.FAINT, Typeface.NORMAL);
        note.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(dp(16), dp(16), dp(16), 0);
        connectionContent.addView(note, noteLp);
    }

    @Override protected void onResume() { super.onResume(); refreshStatus(); }

    private void refreshStatus() {
        status.setText("正在检查绑定状态…");
        io.execute(() -> {
            try {
                HttpClient.Response response = new HttpClient().get(
                        Api.filesBase() + "/wechat/bind-status", new AuthStore(this).bearer());
                JSONObject body = response.ok() ? new JSONObject(response.text()) : new JSONObject();
                boolean connected = body.optBoolean("connected", false);
                runOnUiThread(() -> showStatus(connected, body));
            } catch (Exception ignored) {
                runOnUiThread(() -> showStatus(false, null));
            }
        });
    }

    private void showStatus(boolean connected, JSONObject body) {
        connectionContent.setVisibility(LinearLayout.VISIBLE);
        this.connected = connected;
        if (!connected) {
            stateIcon.setText("✈");
            stateIcon.setTextSize(42);
            stateIcon.setTextColor(Theme.ACCENT);
            stateIcon.setPadding(0, 0, 0, 0);
            stateIcon.setBackground(null);
            stateIcon.setVisibility(TextView.VISIBLE);
            successIcon.setVisibility(ImageView.GONE);
            status.setText("未连接微信公众号");
            detail.setText("连接后，VoiceDrop 可以将文章保存到你的公众号草稿箱。不会自动群发。");
            primary.setText("连接微信公众号");
            primary.setTextSize(18);
            primary.setTextColor(0xffffffff);
            primary.setBackground(round(0xffdf5d49, 10));
            accountCard.setVisibility(LinearLayout.GONE);
            note.setVisibility(LinearLayout.VISIBLE);
            return;
        }
        String name = body == null ? "微信公众号" : body.optString("account_name", "微信公众号");
        stateIcon.setVisibility(TextView.GONE);
        successIcon.setVisibility(ImageView.VISIBLE);
        status.setText("已连接微信公众号");
        detail.setText("授权成功，现在可以将文章保存或更新到公众号草稿箱。 ");
        accountName.setText(name);
        accountCard.setVisibility(LinearLayout.VISIBLE);
        primary.setText("取消连接");
        primary.setTextSize(16);
        primary.setTextColor(Theme.SECONDARY);
        primary.setBackground(strokedRound(Theme.BG, 10, 0xffded6ca));
        note.setVisibility(LinearLayout.GONE);
    }

    private void openAuthorization() {
        startActivity(new Intent(this, WechatAuthorizationActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void disconnect() {
        primary.setEnabled(false);
        primary.setText("正在取消连接…");
        io.execute(() -> {
            try {
                HttpClient.Response response = new HttpClient().postJson(
                        Api.filesBase() + "/wechat/unbind", new AuthStore(this).bearer(),
                        "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (!response.ok()) throw new IllegalStateException("解绑失败");
                runOnUiThread(() -> {
                    primary.setEnabled(true);
                    toast("已取消公众号连接");
                    refreshStatus();
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    primary.setEnabled(true);
                    showStatus(true, null);
                    toast("取消连接失败，请稍后重试");
                });
            }
        });
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, style); return v;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable strokedRound(int color, int radius, int stroke) { GradientDrawable d = round(color, radius); d.setStroke(dp(1), stroke); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { com.baixingai.voicedrop.ui.SimpleToast.show(this, message); }
}
