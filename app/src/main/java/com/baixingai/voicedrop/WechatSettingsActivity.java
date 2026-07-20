package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosSwitch;
import com.baixingai.voicedrop.ui.Theme;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WechatSettingsActivity extends Activity {
    private SettingsStore settingsStore;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthStore auth = new AuthStore(this);
        HttpClient http = new HttpClient();
        settingsStore = new SettingsStore(auth, http);

        configureEdgeToEdge();

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        configureEdgeToEdge();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout top = new FrameLayout(this);
        top.setPadding(dp(12), dp(14) + getStatusBarHeight(), dp(16), dp(10));
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
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(6), dp(20), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        buildWechatForm(content);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override public void onBackPressed() {
        finishWithPageTransition();
    }

    private void buildWechatForm(LinearLayout content) {
        LinearLayout intro = new LinearLayout(this);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.setPadding(dp(16), 0, dp(16), 0);
        intro.setBackground(strokedRound(Theme.CARD, 9, 0xffe5ded2));
        TextView paper = text("✈", 26, Theme.SECONDARY, Typeface.NORMAL);
        intro.addView(paper, new LinearLayout.LayoutParams(dp(34), -2));
        intro.addView(text("填入公众号 AppID / AppSecret 即可连接。", 15, Theme.SECONDARY, Typeface.NORMAL),
                new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, dp(50));
        introLp.setMargins(0, 0, 0, dp(24));
        content.addView(intro, introLp);

        LinearLayout autoRow = new LinearLayout(this);
        autoRow.setGravity(Gravity.CENTER_VERTICAL);
        autoRow.setPadding(dp(16), 0, dp(16), 0);
        autoRow.setBackground(strokedRound(Theme.CARD, 9, 0xffe5ded2));
        LinearLayout autoTexts = new LinearLayout(this);
        autoTexts.setOrientation(LinearLayout.VERTICAL);
        autoTexts.addView(text("自动推草稿", 17, Theme.INK, Typeface.BOLD));
        TextView autoSub = text("挖出新文章后自动发到公众号草稿箱", 14, Theme.SECONDARY, Typeface.NORMAL);
        autoSub.setPadding(0, dp(3), 0, 0);
        autoTexts.addView(autoSub);
        autoRow.addView(autoTexts, new LinearLayout.LayoutParams(0, -2, 1));
        IosSwitch autoDraft = new IosSwitch(this);
        autoRow.addView(autoDraft, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams autoLp = new LinearLayout.LayoutParams(-1, dp(70));
        autoLp.setMargins(0, 0, 0, dp(24));
        content.addView(autoRow, autoLp);

        TextView creds = text("凭据", 18, Theme.INK, Typeface.BOLD);
        LinearLayout.LayoutParams credsLp = new LinearLayout.LayoutParams(-1, -2);
        credsLp.setMargins(0, 0, 0, dp(10));
        content.addView(creds, credsLp);

        android.widget.EditText appid = wechatInput("AppID（wx...）", false);
        content.addView(appid, inputLp(0, dp(10)));

        FrameLayout secretWrap = new FrameLayout(this);
        android.widget.EditText secret = wechatInput("AppSecret", true);
        secret.setPadding(dp(14), 0, dp(52), 0);
        secretWrap.addView(secret, new FrameLayout.LayoutParams(-1, -1));
        TextView eye = text("⊙", 28, Theme.SECONDARY, Typeface.NORMAL);
        eye.setGravity(Gravity.CENTER);
        secretWrap.addView(eye, new FrameLayout.LayoutParams(dp(52), -1, Gravity.RIGHT));
        eye.setOnClickListener(v -> {
            boolean hidden = (secret.getInputType() & android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
            secret.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | (hidden ? android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD));
            secret.setSelection(secret.getText().length());
        });
        content.addView(secretWrap, inputLp(0, dp(10)));

        TextView note = text("凭证只保存在你的设备与服务器的加密配置里，不会出现在文章中。", 14, Theme.FAINT, Typeface.NORMAL);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(0, dp(2), 0, dp(6));
        content.addView(note, noteLp);

        TextView error = text("", 13, 0xffc66a35, Typeface.NORMAL);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorLp = new LinearLayout.LayoutParams(-1, -2);
        errorLp.setMargins(0, 0, 0, dp(8));
        content.addView(error, errorLp);

        LinearLayout help = new LinearLayout(this);
        help.setGravity(Gravity.CENTER_VERTICAL);
        help.setClickable(true);
        ImageView helpIcon = new ImageView(this);
        helpIcon.setImageResource(R.drawable.ic_wechat_help_compass);
        helpIcon.setColorFilter(Theme.ACCENT);
        help.addView(helpIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView helpText = text("去哪里找 AppID / AppSecret？", 15, Theme.ACCENT, Typeface.BOLD);
        LinearLayout.LayoutParams helpTextLp = new LinearLayout.LayoutParams(-2, -2);
        helpTextLp.setMargins(dp(6), 0, 0, 0);
        help.addView(helpText, helpTextLp);
        ImageView helpArrow = new ImageView(this);
        helpArrow.setImageResource(R.drawable.ic_arrow_up_right_flat);
        helpArrow.setColorFilter(Theme.ACCENT);
        LinearLayout.LayoutParams helpArrowLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        helpArrowLp.setMargins(dp(5), 0, 0, 0);
        help.addView(helpArrow, helpArrowLp);
        help.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.weixin.qq.com/console/"));
            try { startActivity(intent); } catch (Exception e) { toast("无法打开浏览器"); }
        });
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(-1, -2);
        helpLp.setMargins(0, 0, 0, dp(10));
        content.addView(help, helpLp);

        TextView helpSteps = text("点上方链接打开公众平台后台，出现登录二维码时先截屏，再打开微信「扫一扫 → 右上角相册」选中截图，选择要绑定的公众号完成登录；进「设置与开发 → 基本配置」：AppID 直接复制，AppSecret 需点「生成」（或「重置」）、管理员扫码确认后显示；同时在该页把下方的 IP 地址加入「IP 白名单」。",
                13, Theme.FAINT, Typeface.NORMAL);
        helpSteps.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams helpStepsLp = new LinearLayout.LayoutParams(-1, -2);
        helpStepsLp.setMargins(0, 0, 0, dp(24));
        content.addView(helpSteps, helpStepsLp);

        View divider = new View(this);
        divider.setBackgroundColor(0x1a000000);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.setMargins(0, 0, 0, dp(24));
        content.addView(divider, dividerLp);

        TextView ipTitle = text("IP 白名单", 18, Theme.INK, Typeface.BOLD);
        LinearLayout.LayoutParams ipTitleLp = new LinearLayout.LayoutParams(-1, -2);
        ipTitleLp.setMargins(0, 0, 0, dp(10));
        content.addView(ipTitle, ipTitleLp);

        TextView ipDesc = text("在公众号后台 → 开发 → 基本配置 → IP 白名单中加入以下地址，服务器才能正常调用接口推草稿。",
                14, Theme.SECONDARY, Typeface.NORMAL);
        ipDesc.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams ipDescLp = new LinearLayout.LayoutParams(-1, -2);
        ipDescLp.setMargins(0, 0, 0, dp(14));
        content.addView(ipDesc, ipDescLp);

        LinearLayout ipRow = new LinearLayout(this);
        ipRow.setGravity(Gravity.CENTER_VERTICAL);
        ipRow.setPadding(dp(14), 0, dp(10), 0);
        ipRow.setBackground(strokedRound(Theme.CARD, 7, 0xffe5ded2));
        TextView ipValue = text("66.42.45.128", 18, Theme.INK, Typeface.NORMAL);
        ipValue.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        ipRow.addView(ipValue, new LinearLayout.LayoutParams(0, -2, 1));
        TextView copy = text("⧉", 26, Theme.SECONDARY, Typeface.NORMAL);
        copy.setGravity(Gravity.CENTER);
        ipRow.addView(copy, new LinearLayout.LayoutParams(dp(44), -1));
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("wechat-ip", "66.42.45.128"));
                toast("IP 已复制");
            }
        });
        LinearLayout.LayoutParams ipRowLp = new LinearLayout.LayoutParams(-1, dp(54));
        ipRowLp.setMargins(0, 0, 0, dp(24));
        content.addView(ipRow, ipRowLp);

        TextView save = text("保存", 18, 0x55ffffff, Typeface.BOLD);
        save.setGravity(Gravity.CENTER);
        save.setEnabled(false);
        save.setBackground(round(0xffeba092, 10));
        content.addView(save, new LinearLayout.LayoutParams(-1, dp(56)));

        final boolean[] savingWechat = {false};
        final boolean[] savedWechat = {false};
        Runnable clearSaveMessage = () -> {
            savedWechat[0] = false;
            error.setVisibility(View.GONE);
            error.setText("");
        };
        Runnable updateSaveState = () -> {
            boolean canSave = appid.getText().toString().trim().length() > 0
                    && secret.getText().toString().trim().length() > 0
                    && !savingWechat[0];
            save.setEnabled(canSave);
            save.setText(savingWechat[0] ? "保存中…" : (savedWechat[0] ? "已保存" : "保存"));
            save.setTextColor(canSave || savingWechat[0] || savedWechat[0] ? android.graphics.Color.WHITE : 0x55ffffff);
            save.setBackground(round(canSave || savingWechat[0] || savedWechat[0] ? 0xffdf5d49 : 0xffeba092, 10));
        };
        android.text.TextWatcher saveWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearSaveMessage.run();
                updateSaveState.run();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        };
        appid.addTextChangedListener(saveWatcher);
        secret.addTextChangedListener(saveWatcher);

        io.execute(() -> {
            try {
                JSONObject cfg = settingsStore.loadWechat();
                runOnUiThread(() -> {
                    appid.setText(cfg.optString("appid"));
                    secret.setText(cfg.optString("secret"));
                    autoDraft.setChecked(cfg.optBoolean("enabled", false));
                    updateSaveState.run();
                });
            } catch (Exception ignored) {}
        });
        save.setOnClickListener(v -> {
            String cleanAppid = appid.getText().toString().trim();
            String cleanSecret = secret.getText().toString().trim();
            boolean enabled = autoDraft.isChecked();
            savingWechat[0] = true;
            savedWechat[0] = false;
            error.setVisibility(View.GONE);
            error.setText("");
            updateSaveState.run();
            io.execute(() -> {
                try {
                    String validation = settingsStore.validateWechatCreds(cleanAppid, cleanSecret);
                    if (validation != null) {
                        runOnUiThread(() -> {
                            savingWechat[0] = false;
                            error.setText(validation);
                            error.setVisibility(View.VISIBLE);
                            updateSaveState.run();
                        });
                        return;
                    }
                    settingsStore.saveWechat(cleanAppid, cleanSecret, enabled);
                    runOnUiThread(() -> {
                        savingWechat[0] = false;
                        savedWechat[0] = true;
                        updateSaveState.run();
                        toast("公众号配置已保存");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        savingWechat[0] = false;
                        error.setText("公众号配置失败：" + e.getMessage());
                        error.setVisibility(View.VISIBLE);
                        updateSaveState.run();
                    });
                }
            });
        });
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void configureEdgeToEdge() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Theme.BG);
        }
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private android.widget.EditText wechatInput(String hint, boolean password) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(hint);
        input.setTextSize(17);
        input.setTextColor(Theme.INK);
        input.setHintTextColor(0xffc9c6c1);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(strokedRound(Theme.CARD, 7, 0xffe5ded2));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | (password ? android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                : android.text.InputType.TYPE_TEXT_VARIATION_NORMAL));
        return input;
    }

    private LinearLayout.LayoutParams inputLp(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.setMargins(0, top, 0, bottom);
        return lp;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable strokedRound(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = round(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : (int) (24 * metrics.density);
    }

    private void toast(String message) {
        runOnUiThread(() -> com.baixingai.voicedrop.ui.SimpleToast.show(this, message));
    }
}
