package com.baixingai.voicedrop;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.core.PromptTree;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.PromptStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.ui.SystemBarDefaults;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PromptImportActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private PromptStore store;
    private EditText codeInput;
    private TextView preview;
    private Button importButton;
    private String previous = "";
    private boolean updating;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new PromptStore(this, new AuthStore(this), new HttpClient());
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); SystemBarDefaults.applyTopAndBottomInsets(page, dp(22), dp(10), dp(22), dp(24)); page.setBackgroundColor(Theme.BG);
        TextView title = text("导入提示词", 24); title.setGravity(Gravity.CENTER); page.addView(title);
        page.addView(text("输入 7 位分享码，也可以粘贴包含分享码的链接。", 14));
        codeInput = new EditText(this); codeInput.setTextSize(28); codeInput.setGravity(Gravity.CENTER); codeInput.setInputType(InputType.TYPE_CLASS_NUMBER); page.addView(codeInput, new LinearLayout.LayoutParams(-1, dp(64)));
        preview = text("", 15); preview.setPadding(dp(12), dp(16), dp(12), dp(16)); page.addView(preview);
        importButton = new Button(this); importButton.setText("确认导入"); importButton.setEnabled(false); importButton.setOnClickListener(v -> importPrompt()); page.addView(importButton);
        Button cancel = new Button(this); cancel.setText("取消"); cancel.setOnClickListener(v -> finish()); page.addView(cancel);
        setContentView(page);

        String initial = getIntent().getStringExtra("shareCode");
        if (initial != null) { previous = PromptTree.mergeCodeInput("", initial); codeInput.setText(previous); }
        codeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                if (updating) return;
                String merged = PromptTree.mergeCodeInput(previous, editable.toString());
                previous = merged;
                if (!merged.equals(editable.toString())) { updating = true; codeInput.setText(merged); codeInput.setSelection(merged.length()); updating = false; }
                if (PromptTree.extractShareCode(merged) != null && merged.length() == 7) loadPreview(merged);
                else { preview.setText(""); importButton.setEnabled(false); }
            }
        });
        if (previous.length() == 7) loadPreview(previous);
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }

    private void loadPreview(String code) {
        preview.setText("正在读取…"); importButton.setEnabled(false);
        io.execute(() -> {
            PromptStore.Preview result = store.preview(code);
            runOnUiThread(() -> {
                if (!code.equals(previous)) return;
                if (result == null) { preview.setText("分享码无效或已停止分享"); return; }
                String author = result.author.isEmpty() ? "" : "\n分享者：" + result.author;
                preview.setText(result.label + author + "\n\n" + result.prompt + "\n\n已被导入 " + result.importCount + " 次");
                importButton.setEnabled(true);
            });
        });
    }

    private void importPrompt() {
        importButton.setEnabled(false);
        io.execute(() -> {
            String error = store.importCode(previous);
            runOnUiThread(() -> { if (error == null) { setResult(RESULT_OK); finish(); } else { preview.setText(error); importButton.setEnabled(true); } });
        });
    }

    private TextView text(String value, int sp) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(Theme.INK); v.setPadding(0, dp(8), 0, dp(8)); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
