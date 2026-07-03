package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.ExportManager;
import com.baixingai.voicedrop.data.LibraryStore;
import com.baixingai.voicedrop.data.Prefs;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.update.AppUpdateManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    public static final String EXTRA_SHOW_WECHAT = "showWechat";

    private AuthStore auth;
    private Prefs prefs;
    private HttpClient http;
    private LibraryStore library;
    private SettingsStore settingsStore;
    private ExportManager exportManager;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = new AuthStore(this);
        prefs = new Prefs(this);
        http = new HttpClient();
        library = new LibraryStore(auth, http);
        settingsStore = new SettingsStore(auth, http);
        exportManager = new ExportManager(this, auth, http, library);

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

        // Top bar
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
        top.addView(text("设置", 24, Theme.INK, Typeface.BOLD), new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addSection(content, "账户");
        addSettingRow(content, "账户", "匿名身份、数据与转移", this::openAccount);

        addSection(content, "创作");
        addSettingRow(content, "写作风格", "成文时模仿这套语气", this::showWritingStyle);
        addSettingRow(content, "公众号", "配置 AppID / Secret，发布草稿", this::openWechatSettings);
        addSettingRow(content, "算力", "余额、消耗明细、约可成文篇数", this::openUsage);

        addSection(content, "同步与社区");
        addAutoShareSwitchRow(content);

        addSection(content, "关于");
        addSettingRow(content, "关于", "隐私、公约、屏蔽、联系", this::openAbout);
        addSettingRow(content, "检查更新", null, () -> AppUpdateManager.checkManually(this));
        addInfoRow(content, "版本", appVersionName());

        // TODO: 恢复"本地" section
        // addSection(content, "本地");
        // addSettingRow(content, "导出全部数据", "打包文章、音频、字幕和索引", this::exportAllData);
        // addSettingRow(content, "上传后删除本地", prefs.deleteLocalAfterUpload() ? "开" : "关", () -> {
        //     prefs.setDeleteLocalAfterUpload(!prefs.deleteLocalAfterUpload());
        //     // Refresh this view
        //     content.removeAllViews();
        //     rebuildSettings(content);
        // });

        // addSettingRow(content, "关于 VoiceDrop", "Android parity build", () -> showTextDialog("关于", "VoiceDrop Android\n以 iOS 功能和接口为标准迁移。"));

        if (getIntent().getBooleanExtra(EXTRA_SHOW_WECHAT, false)) {
            root.post(this::openWechatSettings);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
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

    private void rebuildSettings(LinearLayout content) {
        addSection(content, "账户");
        addSettingRow(content, "账户", "匿名身份、数据与转移", this::openAccount);

        addSection(content, "创作");
        addSettingRow(content, "写作风格", "成文时模仿这套语气", this::showWritingStyle);
        addSettingRow(content, "公众号", "配置 AppID / Secret，发布草稿", this::openWechatSettings);
        addSettingRow(content, "算力", "余额、消耗明细、约可成文篇数", this::openUsage);

        addSection(content, "同步与社区");
        addAutoShareSwitchRow(content);

        addSection(content, "关于");
        addSettingRow(content, "关于", "隐私、公约、屏蔽、联系", this::openAbout);
        addSettingRow(content, "检查更新", null, () -> AppUpdateManager.checkManually(this));
        addInfoRow(content, "版本", appVersionName());

        addSection(content, "本地");
        addSettingRow(content, "导出全部数据", "打包文章、音频、字幕和索引", this::exportAllData);
        addSettingRow(content, "上传后删除本地", prefs.deleteLocalAfterUpload() ? "开" : "关", () -> {
            prefs.setDeleteLocalAfterUpload(!prefs.deleteLocalAfterUpload());
            content.removeAllViews();
            rebuildSettings(content);
        });

        // addSettingRow(content, "关于 VoiceDrop", "Android parity build", () -> showTextDialog("关于", "VoiceDrop Android\n以 iOS 功能和接口为标准迁移。"));
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void openAbout() {
        startActivity(new Intent(this, AboutActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void openAccount() {
        startActivity(new Intent(this, AccountActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void openUsage() {
        startActivity(new Intent(this, UsageActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void openWechatSettings() {
        startActivity(new Intent(this, WechatSettingsActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    public void onBackPressed() {
        finishWithPageTransition();
    }

    private void addSection(LinearLayout content, String label) {
        TextView section = text(label, 13, Theme.FAINT, Typeface.BOLD);
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(4), dp(20), 0, dp(8));
        content.addView(section);
    }

    private void addSettingRow(LinearLayout content, String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        texts.addView(text(title, 16, Theme.INK, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 12, Theme.SECONDARY, Typeface.NORMAL);
            sub.setPadding(0, dp(4), 0, 0);
            texts.addView(sub);
        }
        row.addView(text("›", 28, 0xffcfc6b6, Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
        row.setOnClickListener(v -> action.run());
    }

    private void addInfoRow(LinearLayout content, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        row.addView(text(title, 16, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(value, 15, Theme.SECONDARY, Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
    }

    private void addAutoShareSwitchRow(LinearLayout content) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        row.setClickable(true);

        TextView title = text("自动分享到 VD社区", 16, Theme.INK, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Switch autoShare = new Switch(this);
        row.addView(autoShare, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);

        final boolean[] ready = {false};
        autoShare.setEnabled(false);
        io.execute(() -> {
            try {
                JSONObject cfg = settingsStore.loadConfig();
                boolean enabled = cfg.optBoolean("autoShareCommunity", false);
                runOnUiThread(() -> {
                    ready[0] = false;
                    autoShare.setChecked(enabled);
                    ready[0] = true;
                    autoShare.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    ready[0] = true;
                    autoShare.setEnabled(true);
                    toast("配置读取失败：" + e.getMessage());
                });
            }
        });

        autoShare.setOnCheckedChangeListener((button, checked) -> {
            if (!ready[0]) return;
            button.setEnabled(false);
            io.execute(() -> {
                try {
                    settingsStore.saveConfig(checked);
                    toast(checked ? "已开启自动分享到社区" : "已关闭自动分享到社区");
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        ready[0] = false;
                        autoShare.setChecked(!checked);
                        ready[0] = true;
                        toast("配置保存失败：" + e.getMessage());
                    });
                } finally {
                    runOnUiThread(() -> button.setEnabled(true));
                }
            });
        });
        row.setOnClickListener(v -> {
            if (autoShare.isEnabled()) {
                autoShare.setChecked(!autoShare.isChecked());
            }
        });
    }

    private String appVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void showTokenImport() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("anon_...");
        IosDialog.show(this, "导入账号 token", input,
                "导入", () -> {
                    if (auth.adoptToken(input.getText().toString().trim())) {
                        toast("已导入账号");
                        finishWithPageTransition();
                    } else {
                        toast("token 格式不对");
                    }
                });
    }

    private void showWritingStyle() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(10), dp(18), dp(10));

        TextView hint = text("描述你希望文章呈现的口吻、节奏和结构。保存后会同步到云端写作风格配置。", 13, Theme.SECONDARY, Typeface.NORMAL);
        hint.setLineSpacing(dp(3), 1.0f);
        form.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setMinLines(12);
        input.setGravity(Gravity.TOP);
        input.setTextSize(16);
        input.setTextColor(Theme.INK);
        input.setHintTextColor(Theme.FAINT);
        input.setBackground(round(0xfff7f2ec, 14));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setHint("例如：温柔克制，段落短一点，多保留现场细节，结尾自然收束。");
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, 0, 1);
        inputLp.setMargins(0, dp(14), 0, 0);
        form.addView(input, inputLp);
        io.execute(() -> {
            try {
                SettingsStore.Style style = settingsStore.loadStyle();
                runOnUiThread(() -> input.setText(style.style));
            } catch (Exception ignored) {}
        });
        IosDialog.showBottomSheet(this, "写作风格", form, 430,
                "保存", () -> io.execute(() -> {
                    try {
                        settingsStore.saveStyle(input.getText().toString().trim());
                        toast("写作风格已保存");
                    } catch (Exception e) {
                        toast("写作风格保存失败：" + e.getMessage());
                    }
                }), null, null, true, false);
    }

    private void exportAllData() {
        toast("正在打包导出…");
        io.execute(() -> {
            try {
                File zip = exportManager.exportAll(new ArrayList<>());
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", zip);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(intent, "导出 VoiceDrop 数据")));
            } catch (Exception e) {
                toast("导出失败：" + e.getMessage());
            }
        });
    }

    private void showTextDialog(String title, String message) {
        IosDialog.show(this, title, message);
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

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        return form;
    }

    private android.widget.EditText field(String hint) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(hint);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setPadding(0, dp(6), 0, dp(6));
        return input;
    }
}
