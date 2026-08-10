package com.baixingai.voicedrop;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

import java.util.concurrent.Executors;

public final class FeedbackActivity extends Activity {
    private EditText draft;
    private TextView send;
    private TextView status;
    private TextView count;

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
        page.addView(new PageTitleBar(this, "意见反馈", this::finishWithPageTransition),
                new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        SystemBarDefaults.applyBottomInsets(content, dp(20), dp(8), dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        inputCard.setPadding(dp(15), dp(10), dp(15), dp(10));
        inputCard.setBackground(round(Theme.CARD, 14, Theme.BORDER_CHROME));
        draft = new EditText(this);
        draft.setGravity(Gravity.TOP);
        draft.setMinLines(8);
        draft.setMaxLines(14);
        draft.setTextSize(16);
        draft.setTextColor(Theme.INK);
        draft.setHintTextColor(Theme.FAINT);
        draft.setHint("哪里不顺手？想要什么功能？\n写一句就行。");
        draft.setPadding(0, dp(4), 0, dp(8));
        draft.setBackgroundColor(0x00000000);
        inputCard.addView(draft, new LinearLayout.LayoutParams(-1, -2));
        count = text("0 / 2000", 12, Theme.FAINT, Typeface.NORMAL);
        count.setGravity(Gravity.RIGHT);
        inputCard.addView(count, new LinearLayout.LayoutParams(-1, -2));
        content.addView(inputCard, new LinearLayout.LayoutParams(-1, -2));
        draft.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                FeedbackActivity.this.count.setText(Math.min(text.length(), 2000) + " / 2000");
                FeedbackActivity.this.count.setTextColor(text.length() > 2000 ? Theme.RED : Theme.FAINT);
            }
            @Override public void afterTextChanged(Editable editable) {}
        });

        status = text("会带上你的账户身份，方便改进后回访。", 13, Theme.SECONDARY, Typeface.NORMAL);
        status.setPadding(dp(2), dp(12), dp(2), dp(14));
        content.addView(status);
        send = text("发送反馈", 16, 0xffffffff, Typeface.BOLD);
        send.setText("发送");
        send.setGravity(Gravity.CENTER);
        send.setClickable(true);
        send.setFocusable(true);
        send.setBackground(round(Theme.ACCENT, 14, Theme.ACCENT));
        send.setOnClickListener(v -> submit());
        content.addView(send, new LinearLayout.LayoutParams(-1, dp(52)));
        setContentView(root);
    }

    private void submit() {
        String value = draft.getText().toString().trim();
        if (value.isEmpty()) { showStatus("请先写下反馈内容", Theme.RED); return; }
        if (value.length() > 2000) { showStatus("反馈内容不能超过 2000 字", Theme.RED); return; }
        send.setEnabled(false); send.setText("发送中…"); status.setText("");
        String feedback = value;
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean ok = false;
            try {
                SettingsStore store = new SettingsStore(new AuthStore(this), new HttpClient());
                String name = "";
                try { name = store.loadStyle().name; } catch (Exception ignored) {}
                String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                ok = store.sendFeedback(feedback, name, version);
            } catch (Exception ignored) {}
            boolean sent = ok;
            runOnUiThread(() -> {
                if (sent) {
                    setResult(RESULT_OK);
                    finishWithPageTransition();
                    return;
                }
                send.setEnabled(true);
                send.setText("重试");
                showStatus("发送失败，请检查网络后重试", Theme.RED);
            });
        });
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override public void onBackPressed() {
        finishWithPageTransition();
    }

    private void showStatus(String message, int color) {
        status.setText(message);
        status.setTextColor(color);
    }

    private void configureEdgeToEdge() {
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_out_right);
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style); return view;
    }
    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
