package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android counterpart of iOS BookWritingSheet. */
public final class BookWritingActivity extends Activity {
    static final String API = "https://lab.jianshuo.dev/api/book";
    static final String SHELF = "https://voicedrop.cn/books/";
    static final int SHELF_ICON_RES_ID = R.drawable.ic_about_books_vertical;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private EditText seed;
    private TextView submit;
    private TextView status;
    private boolean sending;
    private boolean submitted;

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
        page.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        SystemBarDefaults.applyBottomInsets(content, dp(20), dp(4), dp(20), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        renderForm();
    }

    private View buildHeader() {
        PageTitleBar header = new PageTitleBar(this, "写书", this::finishWithPageTransition);
        submit = header.addTextAction("开写", this::startBook);
        updateSubmitState();
        return header;
    }

    private void renderForm() {
        content.removeAllViews();
        content.setGravity(Gravity.NO_GRAVITY);

        TextView intro = text(
                "给一个词、一句话，或贴一整篇文章，AI 会把它长成一本书：先写大纲，再每章一个写手并行写正文（费曼式白话），独立评审过稿一章、发布一章。",
                14, Theme.SECONDARY, Typeface.NORMAL);
        intro.setLineSpacing(dp(2), 1f);
        content.addView(intro, matchWrap());

        TextView timing = text(
                "点「开写」提交后就可以关掉 App——书在服务器上继续写，通常 10–30 分钟后出现在公开书架。",
                13, Theme.FAINT, Typeface.NORMAL);
        timing.setLineSpacing(dp(2), 1f);
        content.addView(timing, topMargin(dp(10)));

        content.addView(buildShelfCard(), topMargin(dp(12)));

        seed = new EditText(this);
        seed.setGravity(Gravity.TOP);
        seed.setMinLines(7);
        seed.setMaxLines(14);
        seed.setTextSize(16);
        seed.setTextColor(Theme.INK);
        seed.setHintTextColor(Theme.FAINT);
        seed.setHint("书的种子：一个词、一句话，或一整篇文章……");
        seed.setPadding(dp(18), dp(17), dp(18), dp(17));
        seed.setBackground(roundWithStroke(Theme.CARD, 14, Theme.ACCENT, 2));
        LinearLayout.LayoutParams seedParams = new LinearLayout.LayoutParams(-1, dp(180));
        seedParams.setMargins(0, dp(14), 0, 0);
        content.addView(seed, seedParams);
        seed.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                updateSubmitState();
            }
            @Override public void afterTextChanged(Editable value) {}
        });

        status = text("", 13, Theme.RED, Typeface.NORMAL);
        status.setVisibility(View.GONE);
        content.addView(status, topMargin(dp(10)));
        updateSubmitState();
    }

    private View buildShelfCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(14), dp(12));
        card.setBackground(roundWithStroke(Theme.CARD, 12, Theme.BORDER_CHROME, 1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> openShelf());

        ImageView icon = new ImageView(this);
        icon.setImageResource(SHELF_ICON_RES_ID);
        icon.setColorFilter(Theme.SECONDARY);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setBackground(round(0xfff1ece3, 10));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconParams.setMargins(0, 0, dp(12), 0);
        card.addView(icon, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("公开书架", 17, Theme.INK, Typeface.BOLD));
        TextView subtitle = text(
                "voicedrop.cn/books · 已出版的书都在这",
                13, Theme.SECONDARY, Typeface.NORMAL);
        subtitle.setPadding(0, dp(3), 0, 0);
        labels.addView(subtitle);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right_flat);
        chevron.setColorFilter(0xffcfc6b6);
        card.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        return card;
    }

    private void startBook() {
        String value = seed.getText().toString().trim();
        if (value.isEmpty() || sending || submitted) return;
        sending = true;
        showStatus(null);
        updateSubmitState();
        hideKeyboard();

        io.execute(() -> {
            int code = 0;
            try {
                byte[] body = new JSONObject().put("seed", value).toString()
                        .getBytes(StandardCharsets.UTF_8);
                code = new HttpClient().postJson(
                        API,
                        new AuthStore(this).bearer(),
                        body,
                        new HttpClient.RequestOptions().readTimeoutMs(30_000)).code;
            } catch (Exception ignored) {}
            int result = code;
            runOnUiThread(() -> showResult(result));
        });
    }

    private void showResult(int code) {
        sending = false;
        if (code == 202) {
            submitted = true;
            renderSubmitted();
        } else if (code == 409) {
            showStatus("服务器正在写另一本书，等它写完再来（通常 10–30 分钟）。");
        } else if (code == 401) {
            showStatus("还不能写书：先用 VoiceDrop 录几段话、成几篇文章，再来把它们长成书。");
        } else if (code == 429) {
            showStatus("今天的写书额度用完了，明天再来。");
        } else {
            showStatus(code == 0
                    ? "没连上服务器，请检查网络后重试。"
                    : "服务器返回 " + code + "，请稍后重试。");
        }
        updateSubmitState();
    }

    private void renderSubmitted() {
        content.removeAllViews();
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_check_flat);
        check.setColorFilter(0xffffffff);
        check.setPadding(dp(10), dp(10), dp(10), dp(10));
        check.setBackground(round(Theme.GREEN, 24));
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        checkParams.setMargins(0, dp(24), 0, 0);
        content.addView(check, checkParams);

        TextView title = text("开始写了！", 17, Theme.INK, Typeface.BOLD);
        content.addView(title, centeredTopMargin(dp(12)));

        TextView message = text(
                "现在可以关掉 App。书通常 10–30 分钟写完，过稿一章、上架一章——过会儿去公开书架看。",
                14, Theme.SECONDARY, Typeface.NORMAL);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(2), 1f);
        content.addView(message, centeredTopMargin(dp(10)));

        TextView shelf = text("打开公开书架", 16, 0xffffffff, Typeface.BOLD);
        shelf.setGravity(Gravity.CENTER);
        shelf.setPadding(dp(24), 0, dp(24), 0);
        shelf.setBackground(round(Theme.ACCENT, 24));
        shelf.setClickable(true);
        shelf.setFocusable(true);
        shelf.setOnClickListener(view -> openShelf());
        LinearLayout.LayoutParams shelfParams = new LinearLayout.LayoutParams(-2, dp(48));
        shelfParams.setMargins(0, dp(14), 0, 0);
        content.addView(shelf, shelfParams);
    }

    private void updateSubmitState() {
        if (submit == null) return;
        boolean enabled = seed != null
                && !seed.getText().toString().trim().isEmpty()
                && !sending
                && !submitted;
        submit.setEnabled(enabled);
        submit.setText(sending ? "提交中…" : "开写");
        submit.setTextColor(enabled ? Theme.ACCENT : Theme.FAINT);
    }

    private void showStatus(String message) {
        if (status == null) return;
        status.setText(message == null ? "" : message);
        status.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }

    private void openShelf() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SHELF)));
        } catch (Exception ignored) {}
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override public void onBackPressed() {
        finishWithPageTransition();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void configureEdgeToEdge() {
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_out_right);
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable roundWithStroke(
            int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = round(color, radius);
        drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, margin, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams centeredTopMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, margin, 0, 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
