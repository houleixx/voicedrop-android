package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.LibraryStore;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.data.WechatLogin;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccountActivity extends Activity {
    private AuthStore auth;
    private LibraryStore library;
    private LinearLayout content;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = new AuthStore(this);
        HttpClient http = new HttpClient();
        library = new LibraryStore(auth, http);
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
        top.addView(text("账户", 24, Theme.INK, Typeface.BOLD), new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        BouncyScrollView scroll = new BouncyScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(40));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        render(0, 0, false);
        loadCounts();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override
    public void onBackPressed() {
        finishWithPageTransition();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadCounts();
    }

    private void loadCounts() {
        io.execute(() -> {
            int recordings = 0;
            int mined = 0;
            try {
                List<Recording> list = library.load(new ArrayList<>());
                recordings = list.size();
                for (Recording r : list) if (r.hasArticles) mined++;
            } catch (Exception ignored) {
            }
            int finalRecordings = recordings;
            int finalMined = mined;
            runOnUiThread(() -> render(finalRecordings, finalMined, true));
        });
    }

    private void render(int recordingCount, int minedCount, boolean loaded) {
        content.removeAllViews();
        content.addView(identityCard(), matchWrap(0, 0, 0, dp(22)));
        addSection(content, "数据");
        content.addView(dataCard(recordingCount, minedCount, loaded), matchWrap(0, 0, 0, dp(22)));
        addSection(content, "转移与同步");
        content.addView(transferCard(), matchWrap(0, 0, 0, dp(22)));
    }

    private View identityCard() {
        LinearLayout card = card();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("账户", 17, Theme.INK, Typeface.BOLD));
        TextView desc = text("在这台设备上自动生成，不需要用户名或密码。", 13, Theme.SECONDARY, Typeface.NORMAL);
        desc.setPadding(0, dp(3), 0, 0);
        labels.addView(desc);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1);
        header.addView(labels, labelsLp);
        card.addView(header);
        card.addView(dividerWide(dp(16), dp(16)));

        card.addView(keyField("你的 ID", auth.anonId(), false, auth.anonId()));
        card.addView(keyField("访问令牌", maskedToken(), true, auth.bearer()));
        card.addView(dividerWide(dp(16), dp(10)));

        card.addView(wechatAuthRow(), new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout.LayoutParams existingLp = new LinearLayout.LayoutParams(-1, dp(28));
        existingLp.setMargins(0, dp(3), 0, 0);
        card.addView(existingAccountRow(), existingLp);
        return card;
    }

    private View existingAccountRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, 0);
        row.setMinimumHeight(dp(28));
        row.setClickable(true);
        row.setOnClickListener(v -> showTokenImport());

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_login_existing);
        icon.setColorFilter(Theme.RED);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView label = text("登录已有账号", 15, Theme.RED, Typeface.BOLD);
        label.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -1, 1);
        labelLp.setMargins(dp(10), 0, 0, 0);
        row.addView(label, labelLp);

        return row;
    }

    private View wechatAuthRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (auth.isWechatAuthenticated()) {
            row.addView(wechatIcon(Theme.SECONDARY), new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView status = text("已用微信登录", 15, Theme.SECONDARY, Typeface.NORMAL);
            LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(0, -1, 1);
            statusLp.setMargins(dp(9), 0, 0, 0);
            row.addView(status, statusLp);
            TextView signOut = text("退出登录", 15, Theme.RED, Typeface.BOLD);
            signOut.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            signOut.setOnClickListener(v -> {
                auth.signOutWechat();
                toast("已退出微信登录");
                loadCounts();
            });
            row.addView(signOut, new LinearLayout.LayoutParams(dp(92), -1));
        } else {
            row.addView(wechatIcon(Theme.RED), new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView login = actionText("用微信登录（同步设备 · 参与社区）");
            login.setOnClickListener(v -> startWechatLogin());
            LinearLayout.LayoutParams loginLp = new LinearLayout.LayoutParams(0, -1, 1);
            loginLp.setMargins(dp(9), 0, 0, 0);
            row.addView(login, loginLp);
            row.setOnClickListener(v -> startWechatLogin());
        }
        return row;
    }

    private ImageView wechatIcon(int color) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_wechat);
        icon.setColorFilter(color);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return icon;
    }

    private View keyField(String label, String value, boolean masked, String copyValue) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(label, 13, Theme.FAINT, Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        wrap.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), 0, dp(10), 0);
        row.setBackground(strokedRound(Theme.BG, 9, 0xffe5ded2));
        TextView valueText = text(value, masked ? 14 : 15, masked ? Theme.SECONDARY : Theme.INK, Typeface.NORMAL);
        valueText.setSingleLine(true);
        valueText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(valueText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView copy = text("复制", 13, Theme.RED, Typeface.BOLD);
        copy.setGravity(Gravity.CENTER);
        row.addView(copy, new LinearLayout.LayoutParams(dp(46), -1));
        row.setOnClickListener(v -> copy(copyValue, label + "已复制"));
        copy.setOnClickListener(v -> copy(copyValue, label + "已复制"));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(48));
        rowLp.setMargins(0, dp(7), 0, dp(16));
        wrap.addView(row, rowLp);
        return wrap;
    }

    private View dataCard(int recordingCount, int minedCount, boolean loaded) {
        LinearLayout card = card();
        card.addView(dataRow("录音", loaded ? recordingCount + " 条" : "加载中…", false));
        card.addView(divider());
        card.addView(dataRow("成文", loaded ? minedCount + " 篇" : "加载中…", false));
        return card;
    }

    private LinearLayout dataRow(String title, String value, boolean chevron) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(14), dp(15), dp(14));
        row.addView(text(title, 16, Theme.INK, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        if (value != null && !value.isEmpty()) row.addView(text(value, 14, Theme.SECONDARY, Typeface.NORMAL));
        if (chevron) row.addView(text("›", 28, 0xffcfc6b6, Typeface.NORMAL));
        return row;
    }

    private View transferCard() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(14), dp(15), dp(14));
        row.addView(text("云端空间", 16, Theme.INK, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text("已启用", 13, 0xff2f9b68, Typeface.BOLD));
        card.addView(row);
        wrap.addView(card);
        TextView hint = text("云端空间由访问令牌决定。换设备时导入同一个访问令牌，就会看到同一套录音、文章、写作风格和配置。", 13, Theme.FAINT, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(4), dp(10), dp(4), 0);
        wrap.addView(hint);
        return wrap;
    }

    private void startWechatLogin() {
        if (!WechatLogin.start(this)) {
            toast("无法打开微信，请确认已安装微信");
        }
    }

    private void showTokenImport() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(6));

        TextView intro = text("在另一台设备的「账户」页面复制“访问令牌”，粘贴到这里。访问令牌以 anon_ 开头；页面上显示的 anon-xxxx 只是识别用 ID，不能用来登录。", 14, Theme.SECONDARY, Typeface.NORMAL);
        intro.setLineSpacing(dp(4), 1.0f);
        form.addView(intro);

        TextView label = text("访问令牌", 13, Theme.FAINT, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        label.setPadding(0, dp(16), 0, dp(7));
        form.addView(label);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("anon_...");
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        input.setTextSize(15);
        input.setTextColor(Theme.INK);
        input.setHintTextColor(0xffc9c6c1);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(strokedRound(Theme.BG, 9, 0xffe5ded2));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        form.addView(input, new LinearLayout.LayoutParams(-1, dp(92)));

        TextView note = text("导入成功后，本机会读取这个 token 对应的云端空间：录音、文章、写作风格、社区配置都会切过去。原来的本机 token 不会删除，但你需要先保存好才能再切回。", 12, Theme.FAINT, Typeface.NORMAL);
        note.setLineSpacing(dp(3), 1.0f);
        note.setPadding(0, dp(12), 0, 0);
        form.addView(note);

        IosDialog.showBottomSheet(this, "登录已有账号", form, 380,
                "导入", () -> {
                    if (auth.adoptToken(input.getText().toString().trim())) {
                        toast("已切换到已有账号");
                        loadCounts();
                    } else {
                        toast("请粘贴以 anon_ 开头的访问令牌");
                    }
                }, null, null, true, true);
    }

    private String maskedToken() {
        String token = auth.bearer();
        if (token.length() <= 16) return token;
        return token.substring(0, 10) + "••••••" + token.substring(token.length() - 4);
    }

    private void copy(String value, String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("VoiceDrop", value));
        toast(message);
    }

    private TextView actionText(String value) {
        TextView view = text(value, 15, Theme.RED, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void addSection(LinearLayout parent, String label) {
        TextView section = text(label, 13, Theme.FAINT, Typeface.BOLD);
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(4), 0, 0, dp(8));
        parent.addView(section);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(strokedRound(Theme.CARD, 12, 0xffe5ded2));
        return card;
    }

    private View divider() {
        return dividerWide(0, 0);
    }

    private View dividerWide(int top, int bottom) {
        View view = new View(this);
        view.setBackgroundColor(0xffeee6db);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(0, top, 0, bottom);
        view.setLayoutParams(lp);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(l, t, r, b);
        return lp;
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

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void toast(String message) {
        runOnUiThread(() -> SimpleToast.show(this, message));
    }
}
