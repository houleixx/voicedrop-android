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
import android.provider.Settings;
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
import com.baixingai.voicedrop.data.BlockStore;
import com.baixingai.voicedrop.data.CommunityTerms;
import com.baixingai.voicedrop.data.DeviceLinkCrypto;
import com.baixingai.voicedrop.data.DeviceLinkStore;
import com.baixingai.voicedrop.data.ExportManager;
import com.baixingai.voicedrop.data.LibraryStore;
import com.baixingai.voicedrop.data.Prefs;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.data.UsageStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private AuthStore auth;
    private Prefs prefs;
    private HttpClient http;
    private LibraryStore library;
    private SettingsStore settingsStore;
    private UsageStore usageStore;
    private DeviceLinkStore deviceLinkStore;
    private ExportManager exportManager;
    private BlockStore blockStore;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = new AuthStore(this);
        prefs = new Prefs(this);
        http = new HttpClient();
        library = new LibraryStore(auth, http);
        settingsStore = new SettingsStore(auth, http);
        usageStore = new UsageStore(auth, http);
        deviceLinkStore = new DeviceLinkStore(auth, http);
        exportManager = new ExportManager(this, auth, http, library);
        blockStore = new BlockStore(this);

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
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
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
        top.addView(backTouch, new LinearLayout.LayoutParams(dp(48), dp(48)));
        top.addView(text("设置", 24, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addSection(content, "账户");
        addSettingRow(content, "匿名身份", auth.anonId(), () -> showTokenImport());
        addSettingRow(content, "设备登录", "token 导入、X25519 加密迁移", this::showDeviceLogin);
        addSettingRow(content, "重置匿名身份", "会切到新的云端空间", () -> {
            auth.resetAnonymous();
            toast("已重置匿名身份");
            finishWithPageTransition();
        });

        addSection(content, "创作");
        addSettingRow(content, "文风", "成文时模仿这套语气", this::showWritingStyle);
        addSettingRow(content, "公众号", "配置 AppID / Secret，发布草稿", this::showWechatSettings);
        addSettingRow(content, "算力", "余额、消耗明细、约可成文篇数", this::showUsage);

        addSection(content, "同步与社区");
        addSettingRow(content, "自动分享到 VD社区", "CONFIG.json", () -> toggleAutoShare());
        addSettingRow(content, "社区公约", "举报、投诉、UGC 规则", () -> showTextDialog("社区公约", CommunityTerms.BODY));
        addSettingRow(content, "系统权限", "麦克风、相机、通知", () ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        // TODO: 恢复"本地" section
        // addSection(content, "本地");
        // addSettingRow(content, "导出全部数据", "打包文章、音频、字幕和索引", this::exportAllData);
        // addSettingRow(content, "上传后删除本地", prefs.deleteLocalAfterUpload() ? "开" : "关", () -> {
        //     prefs.setDeleteLocalAfterUpload(!prefs.deleteLocalAfterUpload());
        //     // Refresh this view
        //     content.removeAllViews();
        //     rebuildSettings(content);
        // });

        // TODO: 恢复"其他" section（关于、版本）
        // addSection(content, "其他");
        // addSettingRow(content, "关于", "隐私、公约、屏蔽、联系", this::showAbout);
        // addSettingRow(content, "关于 VoiceDrop", "Android parity build", () -> showTextDialog("关于", "VoiceDrop Android\n以 iOS 功能和接口为标准迁移。"));
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
        addSettingRow(content, "匿名身份", auth.anonId(), () -> showTokenImport());
        addSettingRow(content, "设备登录", "token 导入、X25519 加密迁移", this::showDeviceLogin);
        addSettingRow(content, "重置匿名身份", "会切到新的云端空间", () -> {
            auth.resetAnonymous();
            toast("已重置匿名身份");
            finishWithPageTransition();
        });

        addSection(content, "创作");
        addSettingRow(content, "文风", "成文时模仿这套语气", this::showWritingStyle);
        addSettingRow(content, "公众号", "配置 AppID / Secret，发布草稿", this::showWechatSettings);
        addSettingRow(content, "算力", "余额、消耗明细、约可成文篇数", this::showUsage);

        addSection(content, "同步与社区");
        addSettingRow(content, "自动分享到 VD社区", "CONFIG.json", () -> toggleAutoShare());
        addSettingRow(content, "社区公约", "举报、投诉、UGC 规则", () -> showTextDialog("社区公约", CommunityTerms.BODY));
        addSettingRow(content, "系统权限", "麦克风、相机、通知", () ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        addSection(content, "本地");
        addSettingRow(content, "导出全部数据", "打包文章、音频、字幕和索引", this::exportAllData);
        addSettingRow(content, "上传后删除本地", prefs.deleteLocalAfterUpload() ? "开" : "关", () -> {
            prefs.setDeleteLocalAfterUpload(!prefs.deleteLocalAfterUpload());
            content.removeAllViews();
            rebuildSettings(content);
        });

        // TODO: 恢复"其他" section（关于、版本）
        // addSection(content, "其他");
        // addSettingRow(content, "关于", "隐私、公约、屏蔽、联系", this::showAbout);
        // addSettingRow(content, "关于 VoiceDrop", "Android parity build", () -> showTextDialog("关于", "VoiceDrop Android\n以 iOS 功能和接口为标准迁移。"));
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
        TextView sub = text(subtitle == null ? "" : subtitle, 12, Theme.SECONDARY, Typeface.NORMAL);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);
        row.addView(text("›", 28, 0xffcfc6b6, Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
        row.setOnClickListener(v -> action.run());
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

    private void showDeviceLogin() {
        String[] items = {"自动登录已有账号", "导入 anon token", "生成本机接收公钥", "解密 sealed token", "给另一台设备加密当前账号"};
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        for (String item : items) {
            TextView row = text(item, 17, Theme.INK, Typeface.NORMAL);
            row.setPadding(dp(20), dp(16), dp(20), dp(16));
            row.setGravity(Gravity.CENTER);
            menu.addView(row);
            final String action = item;
            row.setOnClickListener(v -> {
                if (action.equals("自动登录已有账号")) showAutomaticDeviceLink();
                else if (action.equals("导入 anon token")) showTokenImport();
                else if (action.equals("生成本机接收公钥")) showDeviceReceiverKey();
                else if (action.equals("解密 sealed token")) showDecryptDeviceToken();
                else if (action.equals("给另一台设备加密当前账号")) showEncryptDeviceToken();
            });
        }
        IosDialog.show(this, "设备登录", menu, "关闭", () -> {});
    }

    private void showAutomaticDeviceLink() {
        final android.widget.EditText prefix = field("输入旧设备显示的 6 位账号前缀");
        prefix.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        IosDialog.show(this, "登录已有账号", prefix,
                "下一步", () -> toast("正在发起设备配对…"));
    }

    private void showDeviceReceiverKey() {
        DeviceLinkCrypto.Keypair keypair = DeviceLinkCrypto.newKeypair();
        showTextDialog("本机接收公钥",
                "publicKey:\n" + keypair.publicKeyB64
                        + "\n\nprivateKey:\n" + keypair.privateKeyB64
                        + "\n\n请保存 privateKey，用它解密另一台设备返回的 epk/sealed。");
    }

    private void showDecryptDeviceToken() {
        LinearLayout form = form();
        android.widget.EditText priv = field("privateKey");
        android.widget.EditText epk = field("epk");
        android.widget.EditText sealed = field("sealed");
        form.addView(priv);
        form.addView(epk);
        form.addView(sealed);
        IosDialog.show(this, "解密 sealed token", form,
                "解密并登录", () -> {
                    try {
                        String token = DeviceLinkCrypto.decrypt(
                                epk.getText().toString().trim(),
                                sealed.getText().toString().trim(),
                                priv.getText().toString().trim());
                        if (auth.adoptToken(token)) {
                            toast("已切换到迁移账号");
                            finishWithPageTransition();
                        } else {
                            toast("解密成功，但 token 格式不对");
                        }
                    } catch (Exception e) {
                        toast("解密失败：" + e.getMessage());
                    }
                });
    }

    private void showEncryptDeviceToken() {
        final android.widget.EditText pub = field("新设备 publicKey");
        IosDialog.show(this, "加密当前账号", pub,
                "生成", () -> {
                    try {
                        DeviceLinkCrypto.Blob blob = DeviceLinkCrypto.encrypt(auth.bearer(), pub.getText().toString().trim());
                        showTextDialog("迁移密文", "epk:\n" + blob.epkB64 + "\n\nsealed:\n" + blob.sealedB64);
                    } catch (Exception e) {
                        toast("加密失败：" + e.getMessage());
                    }
                });
    }

    private void showWritingStyle() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(12), dp(18), dp(14));

        TextView hint = text("描述你希望文章呈现的口吻、节奏和结构。保存后会同步到云端文风配置。", 13, Theme.SECONDARY, Typeface.NORMAL);
        hint.setLineSpacing(dp(3), 1.0f);
        form.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setMinLines(10);
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
        IosDialog.showBottomSheet(this, "文风", form, 430,
                "保存", () -> io.execute(() -> {
                    try {
                        settingsStore.saveStyle(input.getText().toString().trim());
                        toast("文风已保存");
                    } catch (Exception e) {
                        toast("文风保存失败：" + e.getMessage());
                    }
                }), null, null, true);
    }

    private void showWechatSettings() {
        final IosDialog[] dialogRef = new IosDialog[1];
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), 0, dp(20), dp(18));

        FrameLayout header = new FrameLayout(this);
        TextView title = text("微信公众号", 20, Theme.INK, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new FrameLayout.LayoutParams(-1, dp(58), Gravity.CENTER));
        TextView done = text("完成", 17, 0xffd8614c, Typeface.BOLD);
        done.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams doneLp = new FrameLayout.LayoutParams(dp(64), dp(58), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(done, doneLp);
        sheet.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));
        done.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });

        LinearLayout intro = new LinearLayout(this);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.setPadding(dp(16), 0, dp(16), 0);
        intro.setBackground(strokedRound(Theme.CARD, 9, 0xffe5ded2));
        TextView paper = text("✈", 26, Theme.SECONDARY, Typeface.NORMAL);
        intro.addView(paper, new LinearLayout.LayoutParams(dp(34), -2));
        intro.addView(text("填入公众号 AppID / AppSecret 即可连接。", 15, Theme.SECONDARY, Typeface.NORMAL),
                new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, dp(50));
        introLp.setMargins(0, dp(10), 0, dp(24));
        sheet.addView(intro, introLp);

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
        Switch autoDraft = new Switch(this);
        autoRow.addView(autoDraft, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams autoLp = new LinearLayout.LayoutParams(-1, dp(70));
        autoLp.setMargins(0, 0, 0, dp(24));
        sheet.addView(autoRow, autoLp);

        TextView creds = text("凭据", 18, Theme.INK, Typeface.BOLD);
        LinearLayout.LayoutParams credsLp = new LinearLayout.LayoutParams(-1, -2);
        credsLp.setMargins(0, 0, 0, dp(10));
        sheet.addView(creds, credsLp);

        android.widget.EditText appid = wechatInput("AppID（wx...）", false);
        sheet.addView(appid, inputLp(0, dp(10)));

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
        sheet.addView(secretWrap, inputLp(0, dp(10)));

        TextView note = text("凭证只保存在你的设备与服务器的加密配置里，不会出现在文章中。", 14, Theme.FAINT, Typeface.NORMAL);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(0, dp(2), 0, dp(6));
        sheet.addView(note, noteLp);

        TextView help = text("⊘ 去哪里找 AppID / AppSecret?  ↗", 15, 0xffd8614c, Typeface.BOLD);
        help.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://mp.weixin.qq.com/"));
            try { startActivity(intent); } catch (Exception e) { toast("无法打开浏览器"); }
        });
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(-1, -2);
        helpLp.setMargins(0, 0, 0, dp(24));
        sheet.addView(help, helpLp);

        View divider = new View(this);
        divider.setBackgroundColor(0x1a000000);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.setMargins(0, 0, 0, dp(24));
        sheet.addView(divider, dividerLp);

        TextView ipTitle = text("IP 白名单", 18, Theme.INK, Typeface.BOLD);
        LinearLayout.LayoutParams ipTitleLp = new LinearLayout.LayoutParams(-1, -2);
        ipTitleLp.setMargins(0, 0, 0, dp(10));
        sheet.addView(ipTitle, ipTitleLp);

        TextView ipDesc = text("在公众号后台 → 开发 → 基本配置 → IP 白名单中加入以下地址，服务器才能正常调用接口推草稿。",
                14, Theme.SECONDARY, Typeface.NORMAL);
        ipDesc.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams ipDescLp = new LinearLayout.LayoutParams(-1, -2);
        ipDescLp.setMargins(0, 0, 0, dp(14));
        sheet.addView(ipDesc, ipDescLp);

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
        sheet.addView(ipRow, ipRowLp);

        TextView save = text("保存", 18, 0x55ffffff, Typeface.BOLD);
        save.setGravity(Gravity.CENTER);
        save.setEnabled(false);
        save.setBackground(round(0xffeba092, 10));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(56));
        sheet.addView(save, saveLp);
        Runnable updateSaveState = () -> {
            boolean canSave = appid.getText().toString().trim().length() > 0
                    && secret.getText().toString().trim().length() > 0;
            save.setEnabled(canSave);
            save.setTextColor(canSave ? android.graphics.Color.WHITE : 0x55ffffff);
            save.setBackground(round(canSave ? 0xffdf5d49 : 0xffeba092, 10));
        };
        android.text.TextWatcher saveWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
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
        save.setOnClickListener(v -> io.execute(() -> {
            try {
                String validation = settingsStore.validateWechatCreds(appid.getText().toString(), secret.getText().toString());
                if (validation != null) {
                    toast(validation);
                    return;
                }
                settingsStore.saveWechat(appid.getText().toString(), secret.getText().toString(), autoDraft.isChecked());
                toast("公众号配置已保存");
                runOnUiThread(() -> {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                });
            } catch (Exception e) {
                toast("公众号配置失败：" + e.getMessage());
            }
        }));

        dialogRef[0] = IosDialog.showBottomSheet(this, null, sheet, 720, null, null, null, null);
    }

    private void showUsage() {
        io.execute(() -> {
            try {
                UsageStore.Balance b = usageStore.balance();
                List<UsageStore.Entry> entries = usageStore.ledger();
                StringBuilder text = new StringBuilder();
                text.append("剩余算力：").append((int) b.suanli)
                        .append("\n约可成文：").append(UsageStore.articleCapacity(b.suanli)).append(" 篇")
                        .append("\n已用：").append((int) b.spentSuanli)
                        .append("\n\n明细：\n");
                for (UsageStore.Entry e : entries) {
                    text.append(e.kind).append(" · ").append(e.reason).append(" · ").append(e.suanli).append("\n");
                }
                runOnUiThread(() -> showTextDialog("算力", text.toString()));
            } catch (Exception e) {
                toast("算力加载失败：" + e.getMessage());
            }
        });
    }

    private void toggleAutoShare() {
        io.execute(() -> {
            try {
                JSONObject cfg = settingsStore.loadConfig();
                boolean next = !cfg.optBoolean("autoShareCommunity", false);
                settingsStore.saveConfig(next);
                toast(next ? "已开启自动分享到社区" : "已关闭自动分享到社区");
            } catch (Exception e) {
                toast("配置保存失败：" + e.getMessage());
            }
        });
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

    private void showAbout() {
        LinearLayout about = new LinearLayout(this);
        about.setOrientation(LinearLayout.VERTICAL);
        about.setPadding(dp(16), 0, dp(16), 0);
        addSettingRow(about, "隐私说明", "", () -> showTextDialog("隐私说明",
                "录音只上传到你自己的云端空间；麦克风仅在录音和语音修改时使用；身份是本机生成的匿名 ID。"));
        addSettingRow(about, "社区公约", "", () -> showTextDialog("社区公约", CommunityTerms.BODY));
        addSettingRow(about, "已屏蔽用户", blockStore.blockedList().size() + " 人", this::showBlockedUsers);
        addSettingRow(about, "联系我们 / 内容投诉", CommunityTerms.SUPPORT_EMAIL, () -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:" + CommunityTerms.SUPPORT_EMAIL + "?subject=VoiceDrop%20反馈与投诉"));
            try { startActivity(intent); } catch (Exception e) { toast("无法打开邮件客户端"); }
        });
        IosDialog.show(this, "关于", about, "关闭", () -> {});
    }

    private void showBlockedUsers() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), 0);
        List<String> blocked = blockStore.blockedList();
        if (blocked.isEmpty()) {
            TextView empty = text("还没有屏蔽任何人", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            list.addView(empty);
        } else {
            for (String name : blocked) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(13), dp(16), dp(13));
                row.setBackground(round(Theme.CARD, 12));
                row.addView(text(name, 16, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
                TextView unblock = text("取消屏蔽", 14, Theme.RED, Typeface.BOLD);
                row.addView(unblock);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, dp(8));
                list.addView(row, lp);
                unblock.setOnClickListener(v -> {
                    blockStore.unblock(name);
                    showBlockedUsers();
                });
            }
        }
        IosDialog.show(this, "已屏蔽用户", list, "关闭", () -> {});
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
