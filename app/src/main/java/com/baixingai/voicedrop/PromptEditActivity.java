package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.core.PromptTree;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.PromptStore;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.Theme;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PromptEditActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private PromptStore store;
    private PromptNode original;
    private EditText label;
    private EditText prompt;
    private CheckBox textAnchor;
    private CheckBox imageAnchor;
    private LinearLayout shareArea;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new PromptStore(this, new AuthStore(this), new HttpClient());
        String id = getIntent().getStringExtra("promptId");
        String createType = getIntent().getStringExtra("createType");
        original = id == null ? newNode("group".equals(createType) ? "group" : "action") : find(store.items(), id);
        if (original == null) { finish(); return; }

        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(18), dp(20), dp(18), dp(24)); page.setBackgroundColor(Theme.BG);
        TextView title = text(id == null ? (original.isGroup() ? "新建分组" : "新建提示词") : "编辑提示词", 24); title.setGravity(Gravity.CENTER); page.addView(title);
        ScrollView scroll = new ScrollView(this); LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); scroll.addView(form); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        form.addView(text("名称", 14)); label = input(original.label, false); form.addView(label);
        prompt = input(original.prompt == null ? "" : original.prompt, true);
        if (!original.isGroup()) {
            form.addView(text("提示词", 14)); form.addView(prompt, new LinearLayout.LayoutParams(-1, dp(180)));
            textAnchor = new CheckBox(this); textAnchor.setText("应用于文字"); textAnchor.setChecked(original.appliesTo.contains("text")); form.addView(textAnchor);
            imageAnchor = new CheckBox(this); imageAnchor.setText("应用于图片"); imageAnchor.setChecked(original.appliesTo.contains("image")); form.addView(imageAnchor);
            shareArea = new LinearLayout(this); shareArea.setOrientation(LinearLayout.VERTICAL); shareArea.setPadding(0, dp(18), 0, dp(8)); form.addView(shareArea); loadShareState();
        }

        LinearLayout actions = new LinearLayout(this);
        Button cancel = button("取消"); cancel.setOnClickListener(v -> finish()); actions.addView(cancel, weighted());
        Button save = button("保存"); save.setOnClickListener(v -> save()); actions.addView(save, weighted());
        page.addView(actions);
        setContentView(page);
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }

    private void save() {
        String name = label.getText().toString().trim();
        if (name.isEmpty()) { label.setError("请输入名称"); return; }
        PromptNode edited = original.copy(); edited.label = name;
        if (!edited.isGroup()) {
            String instruction = prompt.getText().toString().trim();
            if (instruction.isEmpty()) { prompt.setError("请输入提示词"); return; }
            edited.prompt = instruction; edited.appliesTo.clear();
            if (textAnchor.isChecked()) edited.appliesTo.add("text");
            if (imageAnchor.isChecked()) edited.appliesTo.add("image");
            if (edited.appliesTo.isEmpty()) { textAnchor.setError("至少选择一种应用场景"); return; }
        }
        boolean creating = getIntent().getStringExtra("promptId") == null;
        if (!creating && original.isSystem()) edited = PromptTree.fork(edited, PromptTree::newUserId);
        PromptNode result = edited;
        io.execute(() -> {
            String error = creating ? store.add(result, null) : store.replace(original.id, result);
            runOnUiThread(() -> { if (error == null) finish(); else label.setError(error); });
        });
    }

    private void loadShareState() {
        shareArea.removeAllViews(); shareArea.addView(text("正在加载分享状态…", 13));
        io.execute(() -> {
            Map<String, PromptStore.ShareState> states = store.shareStates();
            PromptStore.ShareState state = states.get(original.id);
            runOnUiThread(() -> renderShare(state == null ? new PromptStore.ShareState("", false) : state));
        });
    }

    private void renderShare(PromptStore.ShareState state) {
        shareArea.removeAllViews();
        Button toggle = button(state.sharing ? "关闭分享" : "分享这条提示词");
        toggle.setOnClickListener(v -> {
            toggle.setEnabled(false);
            io.execute(() -> {
                PromptStore.ShareState next = store.setSharing(original.id, !state.sharing);
                runOnUiThread(() -> { if (next.error != null) toggle.setError(next.error); renderShare(next.error == null ? next : state); });
            });
        });
        shareArea.addView(toggle);
        if (!state.code.isEmpty()) {
            TextView code = text("分享码  " + state.code, 22); code.setGravity(Gravity.CENTER); shareArea.addView(code);
            String url = Api.sharePage(state.code);
            Button copyCode = button("复制数字"); copyCode.setOnClickListener(v -> copy(state.code)); shareArea.addView(copyCode);
            Button copyUrl = button("复制链接"); copyUrl.setOnClickListener(v -> copy(url)); shareArea.addView(copyUrl);
            Button share = button("分享…"); share.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_SEND); intent.setType("text/plain"); intent.putExtra(Intent.EXTRA_TEXT, Api.sharePage(state.code));
                startActivity(Intent.createChooser(intent, "分享提示词"));
            }); shareArea.addView(share);
        }
    }

    private void copy(String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("VoiceDrop", value));
    }

    private static PromptNode find(java.util.List<PromptNode> items, String id) {
        for (PromptNode node : items) { if (id.equals(node.id)) return node; PromptNode child = find(node.children, id); if (child != null) return child; }
        return null;
    }

    private static PromptNode newNode(String type) {
        PromptNode node = new PromptNode(); node.id = PromptTree.newUserId(); node.type = type; node.label = ""; node.origin = "user";
        if ("action".equals(type)) node.appliesTo.add("text"); return node;
    }

    private EditText input(String value, boolean multiline) { EditText v = new EditText(this); v.setText(value); v.setTextSize(16); v.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0)); v.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL); return v; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); return b; }
    private TextView text(String value, int sp) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(Theme.INK); v.setPadding(0, dp(8), 0, dp(8)); return v; }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, dp(52), 1); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
