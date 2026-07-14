package com.baixingai.voicedrop;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.PromptStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.PromptDragController;
import com.baixingai.voicedrop.ui.Theme;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InstructionSettingsActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final PromptDragController drag = new PromptDragController();
    private PromptStore store;
    private LinearLayout content;
    private TextView errorBanner;
    private Button sortButton;
    private boolean sorting;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new PromptStore(this, new AuthStore(this), new HttpClient());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(18), dp(16), dp(24));
        page.setBackgroundColor(Theme.BG);

        LinearLayout titleRow = horizontal();
        Button back = button("返回"); back.setOnClickListener(v -> finish());
        titleRow.addView(back);
        TextView topTitle = text("提示词", 24, Typeface.BOLD);
        topTitle.setGravity(Gravity.CENTER);
        titleRow.addView(topTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
        sortButton = button("排序"); sortButton.setOnClickListener(v -> toggleSort());
        titleRow.addView(sortButton);
        page.addView(titleRow);

        errorBanner = text("", 13, Typeface.NORMAL);
        errorBanner.setTextColor(0xffa44c28);
        errorBanner.setBackgroundColor(0xffffeadb);
        errorBanner.setPadding(dp(12), dp(9), dp(12), dp(9));
        errorBanner.setVisibility(View.GONE);
        page.addView(errorBanner, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = horizontal();
        actions.addView(action("新建提示词", () -> openEditor("action")), weighted());
        actions.addView(action("新建分组", () -> openEditor("group")), weighted());
        actions.addView(action("导入", () -> startActivity(new Intent(this, PromptImportActivity.class))), weighted());
        actions.addView(action("恢复默认", this::confirmRestore), weighted());
        page.addView(actions);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, 0);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        render(store.items());
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null && !sorting) {
            store = new PromptStore(this, new AuthStore(this), new HttpClient());
            render(store.items());
        }
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }

    private void refresh() {
        io.execute(() -> {
            String error = store.refresh();
            runOnUiThread(() -> {
                errorBanner.setText(error == null ? "" : error);
                errorBanner.setVisibility(error == null ? View.GONE : View.VISIBLE);
                render(store.items());
            });
        });
    }

    private void render(List<PromptNode> items) {
        content.removeAllViews();
        for (int i = 0; i < items.size(); i++) content.addView(nodeView(items.get(i), null, i));
        if (items.isEmpty()) {
            TextView empty = text("还没有提示词，可新建或导入", 15, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER); content.addView(empty, new LinearLayout.LayoutParams(-1, dp(120)));
        }
    }

    private View nodeView(PromptNode node, String parentGroup, int index) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(node.isGroup() ? 0 : dp(8), dp(4), 0, dp(4));
        TextView row = text((sorting ? "☰  " : "") + node.label + (node.isSystem() ? "  系统" : ""), 16, node.isGroup() ? Typeface.BOLD : Typeface.NORMAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackgroundColor(0xffffffff);
        if (!sorting) {
            row.setOnClickListener(v -> openEditor(node.id));
            row.setOnLongClickListener(v -> { confirmDelete(node); return true; });
        } else {
            row.setOnLongClickListener(v -> {
                v.startDragAndDrop(ClipData.newPlainText("prompt-id", node.id), new View.DragShadowBuilder(v), node.id, 0);
                return true;
            });
            final String targetGroup = parentGroup;
            row.setOnDragListener((v, event) -> handleDrop(event, targetGroup, index));
        }
        wrapper.addView(row);
        if (node.isGroup()) {
            LinearLayout children = new LinearLayout(this);
            children.setOrientation(LinearLayout.VERTICAL);
            children.setPadding(dp(18), 0, 0, 0);
            for (int i = 0; i < node.children.size(); i++) children.addView(nodeView(node.children.get(i), node.id, i));
            if (sorting) children.setOnDragListener((v, event) -> handleDrop(event, node.id, node.children.size()));
            wrapper.addView(children);
        }
        return wrapper;
    }

    private boolean handleDrop(DragEvent event, String groupId, int index) {
        if (event.getAction() != DragEvent.ACTION_DROP) return true;
        Object state = event.getLocalState();
        if (state instanceof String && drag.move((String) state, groupId, index)) render(drag.draft());
        return true;
    }

    private void toggleSort() {
        if (!sorting) {
            sorting = true; drag.begin(store.items()); sortButton.setText("完成"); render(drag.draft()); return;
        }
        String error = store.applyReorder(drag.draft(), drag.baseline());
        sorting = false; sortButton.setText("排序");
        if (error != null) showError(error);
        render(store.items());
    }

    private void openEditor(String idOrType) {
        Intent intent = new Intent(this, PromptEditActivity.class);
        if ("action".equals(idOrType) || "group".equals(idOrType)) intent.putExtra("createType", idOrType);
        else intent.putExtra("promptId", idOrType);
        startActivity(intent);
    }

    private void confirmDelete(PromptNode node) {
        new AlertDialog.Builder(this).setTitle("删除提示词")
                .setMessage(node.isGroup() ? "删除分组会同时删除组内提示词，确定继续吗？" : "确定删除“" + node.label + "”吗？")
                .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> io.execute(() -> {
                    String error = store.remove(node.id);
                    runOnUiThread(() -> { if (error != null) showError(error); render(store.items()); });
                })).show();
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this).setTitle("恢复默认").setMessage("会补回缺少的系统提示词，不会删除自建内容。")
                .setNegativeButton("取消", null).setPositiveButton("恢复", (d, w) -> io.execute(() -> {
                    String error = store.restoreDefaults();
                    runOnUiThread(() -> { if (error != null) showError(error); render(store.items()); });
                })).show();
    }

    private void showError(String message) {
        errorBanner.setText(message); errorBanner.setVisibility(View.VISIBLE);
    }

    private LinearLayout horizontal() { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, dp(44), 1); }
    private Button action(String title, Runnable run) { Button b = button(title); b.setOnClickListener(v -> run.run()); return b; }
    private Button button(String title) { Button b = new Button(this); b.setText(title); b.setAllCaps(false); return b; }
    private TextView text(String value, int sp, int style) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(Theme.INK); v.setTypeface(Typeface.DEFAULT, style); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
