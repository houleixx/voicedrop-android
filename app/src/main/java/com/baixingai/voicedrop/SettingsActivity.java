package com.baixingai.voicedrop;

import android.app.Activity;
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
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.ExportManager;
import com.baixingai.voicedrop.data.LibraryStore;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosSwitch;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.LoadingStateView;
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
    public static final String EXTRA_SHOW_WECHAT = "showWechat";
    private static final int REQ_CREATE_EXPORT_ZIP = 301;
    static final int[] SETTING_ROW_ICON_RES_IDS = {
            R.drawable.ic_settings_account,
            R.drawable.ic_settings_pen,
            R.drawable.ic_settings_broadcast,
            R.drawable.ic_settings_bolt,
            R.drawable.ic_settings_community,
            R.drawable.ic_settings_info,
            R.drawable.ic_settings_update,
            R.drawable.ic_settings_version,
            R.drawable.ic_settings_export
    };

    private LibraryStore library;
    private SettingsStore settingsStore;
    private ExportManager exportManager;
    private File pendingExportZip;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthStore auth = new AuthStore(this);
        HttpClient http = new HttpClient();
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

        BouncyScrollView scroll = new BouncyScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(28));
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
        addSection(content, "账户");
        addSettingRow(content, R.drawable.ic_settings_account, "账户", "匿名身份、数据与转移", this::openAccount);

        addSection(content, "创作");
        addSettingRow(content, R.drawable.ic_settings_pen, "写作风格", "成文时模仿这套语气", this::showWritingStyle);
        addSettingRow(content, R.drawable.ic_settings_broadcast, "公众号", "配置 AppID / Secret，发布草稿", this::openWechatSettings);
        addSettingRow(content, R.drawable.ic_settings_bolt, "算力", "余额、消耗明细、约可成文篇数", this::openUsage);

        addSection(content, "同步与社区");
        addAutoShareSwitchRow(content, R.drawable.ic_settings_community);

        addSection(content, "存储");
        addSettingRow(content, R.drawable.ic_settings_export, "导出数据", "所有录音和文章打包下载", this::exportAllData);

        addSection(content, "关于");
        addSettingRow(content, R.drawable.ic_settings_info, "关于", null, this::openAbout);
        addSettingRow(content, R.drawable.ic_settings_update, "检查更新", null, () -> AppUpdateManager.checkManually(this));
        addInfoRow(content, R.drawable.ic_settings_version, "版本", appVersionName());
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
        row.addView(text("›", 28, 0xffcfc6b6, Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
        row.setOnClickListener(v -> action.run());
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
}
