package com.baixingai.voicedrop;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.ExportManager;
import com.baixingai.voicedrop.data.LibraryStore;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.data.ReferralManager;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.data.UsageStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosSwitch;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.update.AppUpdateManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private interface CardBuilder {
        void build(LinearLayout card);
    }

    public static final String EXTRA_SHOW_WECHAT = "showWechat";
    private static final int REQ_CREATE_EXPORT_ZIP = 301;
    static final int[] SETTING_ROW_ICON_RES_IDS = {
            R.drawable.ic_settings_account,
            R.drawable.ic_settings_pen,
            R.drawable.ic_settings_ai_instruction,
            R.drawable.ic_settings_broadcast,
            R.drawable.ic_settings_bolt,
            R.drawable.ic_settings_community,
            R.drawable.ic_settings_broadcast,
            R.drawable.ic_settings_community,
            R.drawable.ic_settings_info,
            R.drawable.ic_settings_update,
            R.drawable.ic_settings_version,
            R.drawable.ic_settings_export
    };

    private AuthStore auth;
    private LibraryStore library;
    private SettingsStore settingsStore;
    private UsageStore usageStore;
    private ExportManager exportManager;
    private File pendingExportZip;
    private TextView nameValueText;
    private TextView usageBalanceText;
    private TextView usageCapacityText;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = new AuthStore(this);
        HttpClient http = new HttpClient();
        library = new LibraryStore(auth, http);
        settingsStore = new SettingsStore(auth, http);
        usageStore = new UsageStore(auth, http);
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
        TextView topTitle = text("设置", 24, Theme.INK, Typeface.BOLD);
        topTitle.setGravity(Gravity.CENTER);
        top.addView(topTitle, new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        BouncyScrollView scroll = new BouncyScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(6), dp(16), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        rebuildPrimarySettings(content);

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

    private void rebuildPrimarySettings(LinearLayout content) {
        // 顶部卡片：账户、算力
        addPrimaryCard(content);
        addInviteCard(content);

        // 写作
        addSection(content, "写作");
        addCard(content, card -> {
            nameValueText = addCardRowWithValue(card, R.drawable.ic_settings_name_card, "名字",
                    "署名和挖文章时对你的称呼", "", this::showNameEditor);
            loadNameRowValue();
            addCardDivider(card);
            addCardRow(card, R.drawable.ic_settings_pen, "写作风格", "成文时模仿这套语气", this::showWritingStyle);
            addCardDivider(card);
            addCardRow(card, R.drawable.ic_settings_ai_instruction, "提示词", "自定义长按菜单里的每个动作", this::openInstructionSettings);
        });

        // 发布
        addSection(content, "发布");
        addCard(content, card -> {
            addCardRow(card, R.drawable.ic_settings_broadcast, "微信公众号", "成文一键推送到草稿箱", this::openWechatSettings);
            addCardDivider(card);
            addCardSwitchRow(card, R.drawable.ic_settings_community);
        });

        // 其他
        addSection(content, "其他");
        addCard(content, card -> {
            addCardRow(card, R.drawable.ic_settings_export, "导出数据", "所有录音和文章打包下载", this::exportAllData);
            addCardDivider(card);
            addCardRow(card, R.drawable.ic_settings_update, "检查更新", "版本 " + appVersionName(), () -> AppUpdateManager.checkManually(this));
            addCardDivider(card);
            addCardRow(card, R.drawable.ic_settings_info, "关于", "隐私 · 公约 · 屏蔽 · 联系", this::openAbout);
        });
    }

    private void addPrimaryCard(LinearLayout content) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(settingsCardBackground());

        LinearLayout accountRow = new LinearLayout(this);
        accountRow.setOrientation(LinearLayout.HORIZONTAL);
        accountRow.setGravity(Gravity.CENTER_VERTICAL);
        accountRow.setPadding(dp(16), dp(13), dp(16), dp(13));
        accountRow.addView(settingIcon(R.drawable.ic_settings_account));
        LinearLayout accountTexts = new LinearLayout(this);
        accountTexts.setOrientation(LinearLayout.VERTICAL);
        accountTexts.setGravity(Gravity.CENTER_VERTICAL);
        accountRow.addView(accountTexts, new LinearLayout.LayoutParams(0, -2, 1));
        accountTexts.addView(text("账户", 16, Theme.INK, Typeface.BOLD));
        accountTexts.addView(text("无需登录 · ID 已随 iCloud 钥匙串备份", 12, Theme.SECONDARY, Typeface.NORMAL));
        String anonId = auth.anonId();
        String shortId = anonId != null && anonId.length() >= 6 ? anonId.substring(anonId.length() - 6).toUpperCase() : "";
        TextView idText = text(shortId, 13, Theme.FAINT, Typeface.NORMAL);
        idText.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(-2, -2);
        idLp.gravity = Gravity.CENTER_VERTICAL;
        idLp.setMargins(0, 0, dp(8), 0);
        accountRow.addView(idText, idLp);
        accountRow.addView(settingsChevron());
        accountRow.setOnClickListener(v -> openAccount());
        card.addView(accountRow);

        View divider = new View(this);
        divider.setBackgroundColor(0xfff0e8da);
        card.addView(divider, cardDividerLayoutParams());

        LinearLayout usageRow = new LinearLayout(this);
        usageRow.setOrientation(LinearLayout.HORIZONTAL);
        usageRow.setGravity(Gravity.CENTER_VERTICAL);
        usageRow.setPadding(dp(16), dp(13), dp(16), dp(13));
        usageRow.addView(settingIcon(R.drawable.ic_settings_bolt));
        LinearLayout usageTexts = new LinearLayout(this);
        usageTexts.setOrientation(LinearLayout.VERTICAL);
        usageRow.addView(usageTexts, new LinearLayout.LayoutParams(0, -2, 1));
        usageTexts.addView(text("算力", 16, Theme.INK, Typeface.BOLD));
        usageCapacityText = text("余额与消耗明细", 12, Theme.SECONDARY, Typeface.NORMAL);
        usageTexts.addView(usageCapacityText);
        usageBalanceText = text("", 15, Theme.AMBER, Typeface.BOLD);
        usageBalanceText.setVisibility(View.GONE);
        LinearLayout.LayoutParams balanceLp = new LinearLayout.LayoutParams(-2, -2);
        balanceLp.gravity = Gravity.CENTER_VERTICAL;
        balanceLp.setMargins(0, 0, dp(8), 0);
        usageRow.addView(usageBalanceText, balanceLp);
        usageRow.addView(settingsChevron());
        usageRow.setOnClickListener(v -> openUsage());
        card.addView(usageRow);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 0);
        content.addView(card, lp);
        loadPrimaryUsageBalance();
    }

    private void addInviteCard(LinearLayout content) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(settingsCardBackground());
        addCardRow(card, R.drawable.ic_settings_community,
                "邀请好友", "朋友装上，双方都得算力", this::shareInvite);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        content.addView(card, lp);
    }

    private void loadPrimaryUsageBalance() {
        io.execute(() -> {
            try {
                UsageStore.Balance balance = usageStore.balance();
                runOnUiThread(() -> {
                    if (usageBalanceText == null || usageCapacityText == null) return;
                    usageBalanceText.setText(String.valueOf((int) Math.round(balance.suanli)));
                    usageBalanceText.setVisibility(View.VISIBLE);
                    usageCapacityText.setText("约可成文 " + UsageStore.articleCapacity(balance.suanli) + " 篇");
                });
            } catch (Exception ignored) {
                // Best-effort summary. The detail page reports load failures itself.
            }
        });
    }

    private void addCard(LinearLayout content, CardBuilder buildRows) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(settingsCardBackground());
        buildRows.build(card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        content.addView(card, lp);
    }

    private ImageView settingsChevron() {
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right_flat);
        chevron.setColorFilter(0xffcfc6b6);
        chevron.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
        return chevron;
    }

    private void addCardRow(LinearLayout card, int iconResId, String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.addView(settingIcon(iconResId));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        texts.addView(text(title, 16, Theme.INK, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 12, Theme.SECONDARY, Typeface.NORMAL);
            sub.setPadding(0, dp(4), 0, 0);
            texts.addView(sub);
        }
        row.addView(settingsChevron());
        card.addView(row);
        if (action != null) row.setOnClickListener(v -> action.run());
    }

    private TextView addCardRowWithValue(LinearLayout card, int iconResId, String title, String subtitle, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.addView(settingIcon(iconResId));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        texts.addView(text(title, 16, Theme.INK, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 12, Theme.SECONDARY, Typeface.NORMAL);
            sub.setPadding(0, dp(4), 0, 0);
            texts.addView(sub);
        }
        TextView valueText = text(value == null ? "" : value, 15, Theme.SECONDARY, Typeface.NORMAL);
        valueText.setSingleLine(true);
        valueText.setEllipsize(TextUtils.TruncateAt.END);
        valueText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        valueText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(-2, -2);
        valueLp.gravity = Gravity.CENTER_VERTICAL;
        valueLp.setMargins(0, 0, dp(4), 0);
        row.addView(valueText, valueLp);
        row.addView(settingsChevron());
        card.addView(row);
        row.setOnClickListener(v -> action.run());
        return valueText;
    }

    private void addCardDivider(LinearLayout card) {
        View divider = new View(this);
        divider.setBackgroundColor(0xfff0e8da);
        card.addView(divider, cardDividerLayoutParams());
    }

    private LinearLayout.LayoutParams cardDividerLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(56), 0, 0, 0);
        return lp;
    }

    private void addCardSwitchRow(LinearLayout card, int iconResId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.addView(settingIcon(iconResId));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        texts.addView(text("自动分享到 VD 社区", 16, Theme.INK, Typeface.BOLD));
        TextView sub = text("挖出新文章后自动发到社区", 12, Theme.SECONDARY, Typeface.NORMAL);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);

        IosSwitch autoShare = new IosSwitch(this);
        row.addView(autoShare, new LinearLayout.LayoutParams(-2, -2));

        card.addView(row);

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

    private void addInfoRowToCard(LinearLayout card, int iconResId, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.addView(settingIcon(iconResId));
        row.addView(text(title, 16, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(value, 15, Theme.SECONDARY, Typeface.NORMAL));
        card.addView(row);
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

    private void shareInvite() {
        toast("正在准备邀请链接…");
        io.execute(() -> {
            try {
                ReferralManager.InviteLink invite = new ReferralManager(this).inviteLink();
                String reward = invite.suanliInviter > 0 && invite.suanliInviter == invite.suanliFriend
                        ? "，咱俩各得 " + invite.suanliInviter + " 算力" : "";
                String text = "我在用 VoiceDrop，动动嘴就能写出好文章" + reward + "：" + invite.url;
                runOnUiThread(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(send, "邀请好友"));
                });
            } catch (Exception error) {
                toast("邀请链接获取失败：" + error.getMessage());
            }
        });
    }

    private void openWechatSettings() {
        startActivity(new Intent(this, WechatSettingsActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void openInstructionSettings() {
        Intent intent = new Intent(this, InstructionSettingsActivity.class);
        ActivityOptions options = ActivityOptions.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left);
        startActivity(intent, options.toBundle());
    }

    @Override
    public void onBackPressed() {
        finishWithPageTransition();
    }

    private void addSection(LinearLayout content, String label) {
        TextView section = text(label, 13, Theme.FAINT, Typeface.BOLD);
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(4), 0, 0, dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        if (content.getChildCount() > 0) lp.setMargins(0, dp(20), 0, 0);
        content.addView(section, lp);
    }

    private void addSettingRow(LinearLayout content, int iconResId, String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        row.addView(settingIcon(iconResId));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        texts.addView(text(title, 16, Theme.INK, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 12, Theme.SECONDARY, Typeface.NORMAL);
            sub.setPadding(0, dp(4), 0, 0);
            texts.addView(sub);
        }
        row.addView(settingsChevron());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
        row.setOnClickListener(v -> action.run());
    }

    private TextView addSettingRowWithValue(LinearLayout content, int iconResId, String title, String subtitle, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        row.addView(settingIcon(iconResId));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        texts.addView(text(title, 16, Theme.INK, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 12, Theme.SECONDARY, Typeface.NORMAL);
            sub.setPadding(0, dp(4), 0, 0);
            texts.addView(sub);
        }
        TextView valueText = text(value == null ? "" : value, 15, Theme.SECONDARY, Typeface.NORMAL);
        valueText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        valueText.setSingleLine(true);
        valueText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(dp(96), -2);
        valueLp.setMargins(dp(12), 0, dp(8), 0);
        row.addView(valueText, valueLp);
        row.addView(settingsChevron());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
        row.setOnClickListener(v -> action.run());
        return valueText;
    }

    private void loadNameRowValue() {
        io.execute(() -> {
            try {
                SettingsStore.Style style = settingsStore.loadStyle();
                runOnUiThread(() -> {
                    if (nameValueText != null) nameValueText.setText(style.name == null ? "" : style.name);
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void addInfoRow(LinearLayout content, int iconResId, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        row.addView(settingIcon(iconResId));
        row.addView(text(title, 16, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(value, 15, Theme.SECONDARY, Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
    }

    private void addAutoShareSwitchRow(LinearLayout content, int iconResId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        row.setClickable(true);
        row.addView(settingIcon(iconResId));

        TextView title = text("自动分享到 VD社区", 16, Theme.INK, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        IosSwitch autoShare = new IosSwitch(this);
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

    private LinearLayout switchRow(LinearLayout content, int iconResId, String titleText, String subtitleText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        row.setClickable(true);
        row.addView(settingIcon(iconResId));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(titleText, 16, Theme.INK, Typeface.BOLD));
        TextView sub = text(subtitleText, 12, Theme.SECONDARY, Typeface.NORMAL);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        IosSwitch toggle = new IosSwitch(this);
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        row.setTag(toggle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
        return row;
    }

    private ImageView settingIcon(int iconResId) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setColorFilter(Theme.ACCENT);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
        lp.setMargins(0, 0, dp(12), 0);
        icon.setLayoutParams(lp);
        return icon;
    }

    private String appVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void showWritingStyle() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(10), dp(18), dp(10));
        form.setClipToPadding(false);
        form.setClipChildren(false);

        LinearLayout styleBarPanel = new LinearLayout(this);
        styleBarPanel.setOrientation(LinearLayout.VERTICAL);
        form.addView(styleBarPanel, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout editorFrame = new FrameLayout(this);
        editorFrame.setClipToPadding(false);
        editorFrame.setClipChildren(false);
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(-1, 0, 1);
        frameLp.setMargins(0, dp(12), 0, 0);
        form.addView(editorFrame, frameLp);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setMinLines(12);
        input.setGravity(Gravity.TOP);
        input.setTextSize(16);
        input.setTextColor(Theme.INK);
        input.setHintTextColor(Theme.FAINT);
        input.setBackground(round(0xfff7f2ec, 14));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setHint("例如：温柔克制，段落短一点，多保留现场细节，结尾自然收束。");
        editorFrame.addView(input, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout styleOverlay = new LinearLayout(this);
        styleOverlay.setOrientation(LinearLayout.VERTICAL);
        styleOverlay.setClipToPadding(false);
        styleOverlay.setClipChildren(false);
        editorFrame.addView(styleOverlay, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        final List<Integer> selectedStyles = new ArrayList<>();
        final JSONArray[] versionsRef = {new JSONArray()};
        final boolean[] compareOn = {false};
        final boolean[] listOpen = {true};
        final boolean[] styleLoading = {true};
        final int[] selectedHead = {-1};
        final String[] selectedHeadStyle = {""};
        buildStyleVersionPanel(styleBarPanel, styleOverlay, versionsRef[0], selectedStyles, compareOn, listOpen, styleLoading, selectedHead, selectedHeadStyle, input);
        io.execute(() -> {
            try {
                SettingsStore.Style style = settingsStore.loadStyle();
                JSONObject history = settingsStore.loadStyleHistory();
                runOnUiThread(() -> {
                    styleLoading[0] = false;
                    input.setText(style.style);
                    selectedStyles.clear();
                    selectedStyles.addAll(style.selectedStyles);
                    JSONArray versions = history.optJSONArray("versions");
                    versionsRef[0] = versions == null ? new JSONArray() : versions;
                    compareOn[0] = !selectedStyles.isEmpty();
                    selectedHead[0] = history.optInt("head", newestStyleVersion(versionsRef[0]));
                    JSONObject current = findStyleVersion(versionsRef[0], selectedHead[0]);
                    selectedHeadStyle[0] = current == null ? style.style : current.optString("style", style.style);
                    buildStyleVersionPanel(styleBarPanel, styleOverlay, versionsRef[0], selectedStyles, compareOn, listOpen, styleLoading, selectedHead, selectedHeadStyle, input);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    styleLoading[0] = false;
                    buildStyleVersionPanel(styleBarPanel, styleOverlay, versionsRef[0], selectedStyles, compareOn, listOpen, styleLoading, selectedHead, selectedHeadStyle, input);
                    toast("写作风格加载失败：" + e.getMessage());
                });
            }
        });
        IosDialog.showBottomSheet(this, "写作风格", form, 560,
                "保存", () -> io.execute(() -> {
                    try {
                        String next = input.getText().toString().trim();
                        if (selectedHead[0] >= 0 && next.equals(selectedHeadStyle[0].trim())) {
                            settingsStore.saveStyleHead(selectedHead[0]);
                            toast("写作风格版本已切换");
                        } else {
                            settingsStore.saveStyle(next);
                            toast("写作风格已保存");
                        }
                    } catch (Exception e) {
                        toast("写作风格保存失败：" + e.getMessage());
                    }
                }), null, null, true, false);
    }

    private void showNameEditor() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(12), dp(18), dp(12));

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setTextSize(17);
        input.setTextColor(Theme.INK);
        input.setHintTextColor(Theme.FAINT);
        input.setHint("你的名字");
        input.setBackground(round(0xfff7f2ec, 14));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        form.addView(input, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView hint = text("这个名字会出现在文章署名，以及挖文章时对你的称呼。随时可改。", 13, Theme.SECONDARY, Typeface.NORMAL);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        hint.setPadding(0, dp(10), 0, 0);
        form.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        io.execute(() -> {
            try {
                SettingsStore.Style style = settingsStore.loadStyle();
                runOnUiThread(() -> {
                    input.setText(style.name);
                    input.setSelection(input.length());
                });
            } catch (Exception ignored) {
            }
        });

        IosDialog dialog = IosDialog.showBottomSheet(this, "名字", form, 110,
                "完成", () -> io.execute(() -> {
                    try {
                        String typedName = input.getText().toString().trim();
                        if (typedName.length() > 20) typedName = typedName.substring(0, 20);
                        final String name = typedName;
                        settingsStore.saveName(name);
                        runOnUiThread(() -> {
                            if (nameValueText != null) nameValueText.setText(name);
                        });
                        toast("名字已保存");
                    } catch (Exception e) {
                        toast("名字保存失败：" + e.getMessage());
                    }
                }), null, null, true, false);
        input.post(() -> {
            input.requestFocus();
            input.setSelection(input.length());
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            android.view.inputmethod.InputMethodManager keyboard =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void buildStyleVersionPanel(LinearLayout barBox, LinearLayout overlayBox, JSONArray versions, List<Integer> selectedStyles,
                                        boolean[] compareOn, boolean[] listOpen, boolean[] styleLoading, int[] selectedHead, String[] selectedHeadStyle,
                                        android.widget.EditText input) {
        barBox.removeAllViews();
        View modeBar = styleModeBar(versions, selectedStyles, compareOn[0], listOpen[0], selectedHead[0]);
        modeBar.setOnClickListener(v -> {
            listOpen[0] = !listOpen[0];
            buildStyleVersionPanel(barBox, overlayBox, versions, selectedStyles, compareOn, listOpen, styleLoading, selectedHead, selectedHeadStyle, input);
        });
        barBox.addView(modeBar, new LinearLayout.LayoutParams(-1, dp(52)));

        overlayBox.removeAllViews();
        overlayBox.setVisibility(listOpen[0] ? View.VISIBLE : View.GONE);
        if (listOpen[0]) {
            overlayBox.addView(styleVersionCard(barBox, overlayBox, versions, selectedStyles, compareOn, listOpen, styleLoading, selectedHead, selectedHeadStyle, input), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private View styleModeBar(JSONArray versions, List<Integer> selectedStyles, boolean compareOn, boolean listOpen, int selectedHead) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = round(Theme.CARD, 8);
        bg.setStroke(dp(1), 0xffb9b0a6);
        bar.setBackground(bg);

        bar.addView(styleModePill(compareOn, listOpen, selectedHead),
                new LinearLayout.LayoutParams(compareOn ? dp(96) : dp(88), dp(36)));

        String title = compareOn
                ? (selectedStyles.isEmpty() ? "未选版本" : selectedStyleLabel(selectedStyles))
                : styleVersionName(findStyleVersion(versions, selectedHead));
        TextView label = text(title == null || title.isEmpty() ? "当前风格" : title, 15, compareOn ? Theme.SECONDARY : Theme.INK, Typeface.BOLD);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1);
        labelLp.setMargins(dp(12), 0, dp(8), 0);
        bar.addView(label, labelLp);

        TextView count = text(compareOn ? selectedStyles.size() + " / 3" : "共 " + Math.max(0, versions == null ? 0 : versions.length()) + " 版",
                15, Theme.FAINT, Typeface.BOLD);
        bar.addView(count);
        return bar;
    }

    private View styleVersionCard(LinearLayout barBox, LinearLayout overlayBox, JSONArray versions, List<Integer> selectedStyles, boolean[] compareOn, boolean[] listOpen,
                                  boolean[] styleLoading,
                                  int[] selectedHead, String[] selectedHeadStyle, android.widget.EditText input) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(Theme.CARD, 10));
        card.setElevation(dp(8));
        card.setTranslationZ(dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(12), dp(10));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("多风格对比", 14, Theme.INK, Typeface.BOLD));
        TextView sub = text("勾选 2-3 个版本，成文时各生成一篇并排挑", 12, Theme.FAINT, Typeface.NORMAL);
        sub.setPadding(0, dp(2), 0, 0);
        copy.addView(sub);
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        IosSwitch toggle = new IosSwitch(this);
        toggle.setChecked(compareOn[0]);
        header.addView(toggle);
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            compareOn[0] = isChecked;
            if (!isChecked) {
                selectedStyles.clear();
                saveStyleSelectionSnapshot(selectedStyles);
            }
            buildStyleVersionPanel(barBox, overlayBox, versions, selectedStyles, compareOn, listOpen, styleLoading, selectedHead, selectedHeadStyle, input);
        });

        View divider = new View(this);
        divider.setBackgroundColor(0xffeee7dd);
        card.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        if (styleLoading[0]) {
            LoadingStateView loading = new LoadingStateView(this, "正在加载写作风格...");
            loading.setPadding(0, dp(18), 0, dp(18));
            card.addView(loading, new LinearLayout.LayoutParams(-1, dp(120)));
            return card;
        }

        if (versions == null || versions.length() == 0) {
            TextView empty = text("暂无可选风格版本。先保存几个写作风格版本后再开启。", 12, Theme.FAINT, Typeface.NORMAL);
            empty.setPadding(dp(12), dp(10), dp(12), 0);
            card.addView(empty);
            return card;
        }

        int validRows = 0;
        for (int i = 0; i < versions.length(); i++) {
            if (versions.optJSONObject(i) != null) validRows++;
        }
        int rowIndex = 0;
        for (int i = versions.length() - 1; i >= 0; i--) {
            JSONObject item = versions.optJSONObject(i);
            if (item == null) continue;
            int version = item.optInt("v", i);
            boolean selected = compareOn[0] ? selectedStyles.contains(version) : version == selectedHead[0];
            LinearLayout row = styleVersionRow(item, version, selected, compareOn[0]);
            row.setOnClickListener(v -> {
                if (compareOn[0]) {
                    if (selectedStyles.contains(version)) {
                        selectedStyles.remove(Integer.valueOf(version));
                    } else if (selectedStyles.size() < 3) {
                        selectedStyles.add(version);
                    } else {
                        toast("最多选择 3 个风格版本");
                        return;
                    }
                    saveStyleSelectionSnapshot(selectedStyles);
                } else {
                    selectedHead[0] = version;
                    selectedHeadStyle[0] = item.optString("style", "");
                    input.setText(selectedHeadStyle[0]);
                    input.setSelection(input.getText().length());
                }
                buildStyleVersionPanel(barBox, overlayBox, versions, selectedStyles, compareOn, listOpen, styleLoading, selectedHead, selectedHeadStyle, input);
            });
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(56));
            rowLp.setMargins(dp(8), rowIndex == 0 ? dp(8) : dp(2),
                    dp(8), rowIndex == validRows - 1 ? dp(8) : dp(2));
            card.addView(row, rowLp);
            rowIndex++;
        }

        if (compareOn[0]) {
            View bottomDivider = new View(this);
            bottomDivider.setBackgroundColor(0xffeee7dd);
            card.addView(bottomDivider, new LinearLayout.LayoutParams(-1, dp(1)));
            String footer = selectedStyles.isEmpty()
                    ? "勾选 2-3 个版本。"
                    : "将分别用 " + selectedStyleLabel(selectedStyles) + " 生成文章。";
            TextView note = text(footer + " 关闭开关会清空多风格选择。", 12, Theme.FAINT, Typeface.NORMAL);
            note.setPadding(dp(14), dp(10), dp(14), dp(10));
            card.addView(note);
        }
        return card;
    }

    private String selectedStyleLabel(List<Integer> selectedStyles) {
        List<Integer> copy = new ArrayList<>(selectedStyles);
        java.util.Collections.sort(copy, java.util.Collections.reverseOrder());
        StringBuilder out = new StringBuilder();
        for (Integer version : copy) {
            if (out.length() > 0) out.append("、");
            out.append("v").append(version);
        }
        return out.toString();
    }

    private View styleModePill(boolean compareOn, boolean listOpen, int selectedHead) {
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), 0, dp(8), 0);
        pill.setBackground(round(Theme.ACCENT, 7));

        TextView label = text(compareOn ? "对比" : "v" + Math.max(0, selectedHead), 14, 0xffffffff, Typeface.BOLD);
        pill.addView(label);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(listOpen ? R.drawable.ic_chevron_up_flat : R.drawable.ic_chevron_down_flat);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        arrowLp.setMargins(dp(6), 0, 0, 0);
        pill.addView(arrow, arrowLp);
        return pill;
    }

    private LinearLayout styleVersionRow(JSONObject item, int version, boolean selected, boolean compareOn) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(12), dp(10));
        row.setBackground(round(selected ? Theme.ACCENT_SOFT : Theme.CARD, 8));

        TextView versionLabel = text("v" + version, 14, selected ? Theme.RED : Theme.INK, Typeface.BOLD);
        LinearLayout.LayoutParams versionLp = new LinearLayout.LayoutParams(dp(42), -2);
        row.addView(versionLabel, versionLp);

        String style = item.optString("style", item.optString("source", ""));
        String name = styleDisplayName(style);
        int count = style == null ? 0 : style.length();

        TextView title = text(name, 14, selected ? Theme.RED : Theme.INK, selected ? Typeface.BOLD : Typeface.NORMAL);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView meta = text(count > 0 ? count + " 字" : "", 13, Theme.FAINT, Typeface.BOLD);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-2, -2);
        metaLp.setMargins(dp(8), 0, dp(8), 0);
        row.addView(meta, metaLp);

        ImageView state = new ImageView(this);
        if (compareOn) {
            state.setImageResource(selected ? R.drawable.ic_checkbox_checked_flat : R.drawable.ic_checkbox_unchecked_flat);
            row.addView(state, new LinearLayout.LayoutParams(dp(28), dp(28)));
        } else {
            state.setImageResource(R.drawable.ic_check_flat);
            state.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            row.addView(state, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        return row;
    }

    private int newestStyleVersion(JSONArray versions) {
        JSONObject latest = versions == null || versions.length() == 0 ? null : versions.optJSONObject(versions.length() - 1);
        return latest == null ? 0 : latest.optInt("v", versions.length() - 1);
    }

    private JSONObject findStyleVersion(JSONArray versions, int version) {
        if (versions == null) return null;
        for (int i = 0; i < versions.length(); i++) {
            JSONObject item = versions.optJSONObject(i);
            if (item != null && item.optInt("v", i) == version) return item;
        }
        return null;
    }

    private String styleVersionName(JSONObject item) {
        if (item == null) return "";
        return styleDisplayName(item.optString("style", item.optString("source", "")));
    }

    private String styleDisplayName(String style) {
        if (style == null) return "";
        String[] lines = style.split("\\n");
        String first = lines.length == 0 ? "" : lines[0].trim();
        if (first.length() > 12) return first.substring(0, 12) + "…";
        return first;
    }

    private void saveStyleSelectionSnapshot(List<Integer> selectedStyles) {
        List<Integer> snapshot = new ArrayList<>(selectedStyles);
        io.execute(() -> {
            try {
                settingsStore.saveStyleSelection(snapshot);
                toast(snapshot.isEmpty() ? "已关闭多风格对比" : "多风格选择已保存");
            } catch (Exception e) {
                toast("多风格保存失败：" + e.getMessage());
            }
        });
    }

    private void exportAllData() {
        LinearLayout box = form();
        TextView status = text("正在准备导出…", 14, Theme.SECONDARY, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        box.addView(status, new LinearLayout.LayoutParams(-1, -2));
        IosDialog progress = IosDialog.showProgress(this, "正在导出数据", box, 118);
        io.execute(() -> {
            try {
                List<Recording> recordings = library.load(new ArrayList<>());
                if (recordings.isEmpty()) throw new IllegalArgumentException("没有录音可以导出");
                runOnUiThread(() -> status.setText("正在打包 " + recordings.size() + " 条录音…"));
                File zip = exportManager.exportAll(recordings);
                pendingExportZip = zip;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_TITLE, zip.getName());
                runOnUiThread(() -> {
                    progress.dismiss();
                    startActivityForResult(intent, REQ_CREATE_EXPORT_ZIP);
                });
            } catch (Exception e) {
                runOnUiThread(() -> progress.dismiss());
                toast("导出失败：" + e.getMessage());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CREATE_EXPORT_ZIP) return;
        Uri uri = data == null ? null : data.getData();
        File zip = pendingExportZip;
        pendingExportZip = null;
        if (resultCode != RESULT_OK || uri == null || zip == null) {
            toast("已取消保存");
            return;
        }
        io.execute(() -> {
            try (FileInputStream in = new FileInputStream(zip);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("无法打开保存位置");
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                toast("导出文件已保存");
            } catch (Exception e) {
                toast("保存失败：" + e.getMessage());
            }
        });
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable settingsCardBackground() {
        GradientDrawable drawable = round(Theme.CARD, 12);
        drawable.setStroke(dp(1), Theme.BORDER_CHROME);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
}
