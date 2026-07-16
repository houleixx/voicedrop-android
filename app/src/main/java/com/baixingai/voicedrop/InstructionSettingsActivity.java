package com.baixingai.voicedrop;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.core.PromptTree;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.PromptStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.PromptDragController;
import com.baixingai.voicedrop.ui.PromptListPresentation;
import com.baixingai.voicedrop.ui.Theme;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InstructionSettingsActivity extends Activity {
    private static final int DIVIDER = 0xfff2ede5;
    private static final int TILE_NEUTRAL = 0xfff2eee7;
    private static final int IMPORT_BORDER = 0xffd8b08a;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final PromptDragController drag = new PromptDragController();
    private final Set<String> expandedGroups = new HashSet<>();
    private PromptStore store;
    private LinearLayout content;
    private TextView errorBanner;
    private TextView intro;
    private FrameLayout leftHeaderButton;
    private TextView rightHeaderButton;
    private boolean sorting;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new PromptStore(this, new AuthStore(this), new HttpClient());
        configureEdgeToEdge();

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        configureEdgeToEdge();

        LinearLayout page = vertical();
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout top = new FrameLayout(this);
        top.setPadding(dp(12), dp(14) + getStatusBarHeight(), dp(16), dp(10));
        top.addView(header(), new FrameLayout.LayoutParams(-1, dp(48), Gravity.CENTER_VERTICAL));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        intro = text(normalIntro(), 13, Typeface.NORMAL, Theme.SECONDARY);
        intro.setLineSpacing(0, 1.15f);

        errorBanner = text("", 13, Typeface.NORMAL, 0xffa44c28);
        errorBanner.setBackground(rounded(0xffffeadb, 9));
        errorBanner.setPadding(dp(12), dp(9), dp(12), dp(9));
        errorBanner.setVisibility(View.GONE);

        BouncyScrollView scroll = new BouncyScrollView(this);
        scroll.setFillViewport(true);
        content = vertical();
        content.setPadding(dp(16), dp(6), dp(16), dp(40));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
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

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override public void onBackPressed() { finishWithPageTransition(); }

    private View header() {
        FrameLayout top = new FrameLayout(this);
        FrameLayout backTouch = new FrameLayout(this);
        backTouch.setClickable(true);
        leftHeaderButton = backTouch;
        showBackAction();
        top.addView(backTouch, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL));

        TextView topTitle = text("提示词", 24, Typeface.BOLD, Theme.INK);
        topTitle.setGravity(Gravity.CENTER);
        top.addView(topTitle, new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        rightHeaderButton = squareButton("+", true);
        rightHeaderButton.setTextSize(26);
        rightHeaderButton.setOnClickListener(v -> showNewSheet());
        top.addView(rightHeaderButton, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        return top;
    }

    private void showBackAction() {
        leftHeaderButton.removeAllViews();
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
        leftHeaderButton.addView(back, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        leftHeaderButton.setOnClickListener(v -> finishWithPageTransition());
    }

    private void showSortCancelAction() {
        leftHeaderButton.removeAllViews();
        TextView cancel = text("取消", 14, Typeface.BOLD, Theme.ACCENT);
        cancel.setGravity(Gravity.CENTER);
        leftHeaderButton.addView(cancel, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        leftHeaderButton.setOnClickListener(v -> exitSort(false));
    }

    private void refresh() {
        io.execute(() -> {
            String error = store.refresh();
            runOnUiThread(() -> {
                errorBanner.setText(error == null ? "" : "加载失败，正在显示上次内容");
                errorBanner.setVisibility(error == null ? View.GONE : View.VISIBLE);
                render(store.items());
            });
        });
    }

    private void render(List<PromptNode> items) {
        content.removeAllViews();
        content.addView(intro, margins(-1, -2, 0, 0, 0, 10));
        if (errorBanner.getVisibility() == View.VISIBLE) {
            content.addView(errorBanner, margins(-1, -2, 0, 0, 0, 10));
        }
        List<PromptListPresentation.Row> rows = PromptListPresentation.rows(items, expandedGroups);
        if (rows.isEmpty()) {
            TextView empty = text("还没有提示词，可点击右上角新建", 15, Typeface.NORMAL, Theme.FAINT);
            empty.setGravity(Gravity.CENTER);
            content.addView(empty, new LinearLayout.LayoutParams(-1, dp(140)));
        } else {
            LinearLayout card = vertical();
            card.setBackground(rounded(Color.WHITE, 14));
            for (int i = 0; i < rows.size(); i++) {
                card.addView(promptRow(rows.get(i)));
                if (i < rows.size() - 1) card.addView(divider(rows.get(i + 1).depth == 1 ? 66 : 0));
            }
            content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }

        if (!sorting) {
            content.addView(importBox(), margins(-1, -2, 0, 18, 0, 0));
            TextView note = text("也可以在录音时直接对 VoiceDrop 说出数字，或点开 voicedrop.cn 链接自动跳转到这里。", 12, Typeface.NORMAL, Theme.FAINT);
            note.setLineSpacing(0, 1.18f);
            content.addView(note, margins(-1, -2, 4, 8, 4, 0));
            TextView restore = text("恢复默认提示词", 14, Typeface.NORMAL, Theme.ACCENT);
            restore.setGravity(Gravity.CENTER);
            restore.setPadding(0, dp(18), 0, dp(18));
            restore.setOnClickListener(v -> confirmRestore());
            content.addView(restore, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private View promptRow(PromptListPresentation.Row row) {
        LinearLayout line = horizontal();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(14 + row.depth * 18), dp(8), dp(14), dp(8));
        line.setMinimumHeight(dp(row.group ? 52 : 56));

        ImageView icon = iconTile(row.group ? R.drawable.ic_prompt_folder
                : ("仅图片".equals(row.appliesLabel) ? R.drawable.ic_image : R.drawable.ic_doc),
                !row.group && "仅图片".equals(row.appliesLabel));
        addIconWithSpacing(line, icon, 40);

        if (row.group) {
            LinearLayout copy = horizontal();
            copy.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(row.node.label, 17, Typeface.NORMAL, Theme.INK);
            copy.addView(title);
            TextView meta = text("  分组 · " + row.childCount + " 项", 13, Typeface.NORMAL, Theme.FAINT);
            copy.addView(meta);
            line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            LinearLayout copy = vertical();
            TextView title = text(row.node.label + originSuffix(row.node), 17, Typeface.NORMAL, Theme.INK);
            copy.addView(title);
            if (!row.appliesLabel.isEmpty()) {
                TextView badge = text(row.appliesLabel, 12, Typeface.NORMAL,
                        "仅图片".equals(row.appliesLabel) ? Theme.ACCENT
                                : ("仅文字".equals(row.appliesLabel) ? Theme.GREEN : Theme.SECONDARY));
                badge.setPadding(dp(6), dp(1), dp(6), dp(1));
                badge.setBackground(rounded("仅图片".equals(row.appliesLabel) ? Theme.ACCENT_SOFT
                        : ("仅文字".equals(row.appliesLabel) ? Theme.GREEN_BG : TILE_NEUTRAL), 4));
                LinearLayout badgeWrap = horizontal(); badgeWrap.addView(badge);
                copy.addView(badgeWrap, margins(-1, -2, 0, 0, 0, 0));
            }
            line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        }

        if (row.group && row.expanded) {
            ImageView chevron = new ImageView(this);
            chevron.setImageResource(R.drawable.ic_prompt_chevron_down);
            chevron.setColorFilter(Theme.FAINT);
            chevron.setScaleType(ImageView.ScaleType.CENTER);
            line.addView(chevron, new LinearLayout.LayoutParams(dp(32), dp(40)));
        } else {
            line.addView(trailingChevron());
        }

        line.setOnClickListener(v -> {
            if (row.group) {
                if (row.expanded) expandedGroups.remove(row.node.id); else expandedGroups.add(row.node.id);
                render(sorting ? drag.draft() : store.items());
            } else if (!sorting) {
                openEditor(row.node.id);
            }
        });
        line.setOnLongClickListener(v -> {
            if (!sorting) { enterSort(); return true; }
            v.startDragAndDrop(ClipData.newPlainText("prompt-id", row.node.id), new View.DragShadowBuilder(v), row.node.id, 0);
            return true;
        });
        final float[] touchStart = new float[2];
        line.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchStart[0] = event.getX(); touchStart[1] = event.getY();
            } else if (!sorting && event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - touchStart[0];
                float dy = Math.abs(event.getY() - touchStart[1]);
                if (dx < -dp(60) && dy < dp(40)) { confirmDelete(row.node); return true; }
            }
            return false;
        });
        if (sorting) line.setOnDragListener((v, event) -> handleDrop(event, row));
        return line;
    }

    private boolean handleDrop(DragEvent event, PromptListPresentation.Row target) {
        if (event.getAction() != DragEvent.ACTION_DROP) return true;
        Object state = event.getLocalState();
        if (!(state instanceof String)) return true;
        String id = (String) state;
        PromptNode moving = find(drag.draft(), id);
        String groupId = target.node.isGroup() && moving != null && !moving.isGroup() ? target.node.id : parentOf(drag.draft(), target.node.id);
        int index = target.node.isGroup() && moving != null && !moving.isGroup() ? target.node.children.size() : indexIn(drag.draft(), groupId, target.node.id);
        if (drag.move(id, groupId, Math.max(0, index))) {
            if (groupId != null) expandedGroups.add(groupId);
            render(drag.draft());
        }
        return true;
    }

    private void enterSort() {
        sorting = true;
        drag.begin(store.items());
        intro.setText("长按提示词并拖动排序；拖到分组行可收进该组。");
        showSortCancelAction();
        rightHeaderButton.setText("完成"); rightHeaderButton.setTextSize(14); rightHeaderButton.setTextColor(Theme.ACCENT);
        rightHeaderButton.setBackgroundColor(Color.TRANSPARENT); rightHeaderButton.setOnClickListener(v -> exitSort(true));
        render(drag.draft());
    }

    private void exitSort(boolean save) {
        if (save) {
            String error = store.applyReorder(drag.draft(), drag.baseline());
            if (error != null) showError(error);
        } else {
            drag.cancel();
        }
        sorting = false;
        intro.setText(normalIntro());
        showBackAction();
        rightHeaderButton.setText("+"); rightHeaderButton.setTextSize(26); rightHeaderButton.setTextColor(Color.WHITE); rightHeaderButton.setBackground(rounded(Theme.ACCENT, 12)); rightHeaderButton.setOnClickListener(v -> showNewSheet());
        render(store.items());
    }

    private View importBox() {
        LinearLayout box = horizontal();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), dp(14), dp(14), dp(14));
        box.setBackground(outlined(0xfffbf3e9, 12, IMPORT_BORDER, 1, 5, 4));
        ImageView icon = iconTile(R.drawable.ic_prompt_import, true);
        addIconWithSpacing(box, icon, 44);
        LinearLayout copy = vertical();
        copy.addView(text("输入魔法数字导入", 16, Typeface.BOLD, Theme.INK));
        copy.addView(text("把别人分享的提示词存进你的菜单", 13, Typeface.NORMAL, Theme.SECONDARY));
        box.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(trailingChevron());
        box.setOnClickListener(v -> showImportSheet());
        return box;
    }

    private ImageView trailingChevron() {
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right_flat);
        chevron.setColorFilter(Theme.FAINT);
        chevron.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
        return chevron;
    }

    private void showImportSheet() {
        Dialog dialog = new Dialog(this);
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(16), dp(28), dp(16), dp(28));
        sheet.setBackground(rounded(0xfffffbf6, 22));

        TextView title = text("导入提示词", 20, Typeface.BOLD, Theme.INK);
        title.setGravity(Gravity.CENTER);
        sheet.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView subtitle = text("输入 7 位魔法数字，或粘贴分享链接", 14, Typeface.NORMAL, Theme.FAINT);
        subtitle.setGravity(Gravity.CENTER);
        sheet.addView(subtitle, margins(-1, -2, 0, 2, 0, 18));

        EditText codeInput = new EditText(this);
        codeInput.setTextSize(18);
        codeInput.setTextColor(Theme.INK);
        codeInput.setGravity(Gravity.CENTER);
        codeInput.setSingleLine(true);
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT);
        codeInput.setBackground(outlined(Color.WHITE, 12, 0xffe6ddd1, 1, 0, 0));
        sheet.addView(codeInput, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView importButton = text("加入我的提示词", 18, Typeface.BOLD, Color.WHITE);
        importButton.setGravity(Gravity.CENTER);
        importButton.setEnabled(false);
        importButton.setBackground(rounded(0xffc2b9a8, 12));
        sheet.addView(importButton, margins(-1, dp(54), 0, 14, 0, 0));

        TextView preview = text("", 13, Typeface.NORMAL, Theme.SECONDARY);
        preview.setGravity(Gravity.CENTER);
        preview.setVisibility(View.GONE);
        sheet.addView(preview, margins(-1, -2, 4, 12, 4, 0));
        TextView hint = text("导入后是你自己的副本，可改名、改内容、随时删除；原作者之后的修改不影响你。", 13, Typeface.NORMAL, Theme.FAINT);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(0, 1.15f);
        sheet.addView(hint, margins(-1, -2, 4, 18, 4, 0));

        final String[] previous = {""};
        final boolean[] updating = {false};
        codeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                if (updating[0]) return;
                String merged = PromptTree.mergeCodeInput(previous[0], editable.toString());
                previous[0] = merged;
                if (!merged.equals(editable.toString())) {
                    updating[0] = true;
                    codeInput.setText(merged);
                    codeInput.setSelection(merged.length());
                    updating[0] = false;
                }
                if (PromptTree.extractShareCode(merged) == null || merged.length() != 7) {
                    preview.setVisibility(View.GONE);
                    setImportButton(importButton, false);
                    return;
                }
                preview.setText("正在读取提示词…");
                preview.setVisibility(View.VISIBLE);
                setImportButton(importButton, false);
                loadImportPreview(merged, previous, preview, importButton);
            }
        });
        importButton.setOnClickListener(v -> {
            String code = previous[0];
            setImportButton(importButton, false);
            io.execute(() -> {
                String error = store.importCode(code);
                runOnUiThread(() -> {
                    if (error == null) { dialog.dismiss(); render(store.items()); }
                    else { preview.setText(error); preview.setVisibility(View.VISIBLE); setImportButton(importButton, true); }
                });
            });
        });

        dialog.setContentView(sheet);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.28f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        });
        dialog.show();
    }

    private void loadImportPreview(String code, String[] previous, TextView preview, TextView importButton) {
        io.execute(() -> {
            PromptStore.Preview result = store.preview(code);
            runOnUiThread(() -> {
                if (!code.equals(previous[0])) return;
                if (result == null) { preview.setText("分享码无效或已停止分享"); return; }
                preview.setText("将导入「" + result.label + "」");
                setImportButton(importButton, true);
            });
        });
    }

    private void setImportButton(TextView button, boolean enabled) {
        button.setEnabled(enabled);
        button.setBackground(rounded(enabled ? Theme.ACCENT : 0xffc2b9a8, 12));
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private int getStatusBarHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : (int) (24 * metrics.density);
    }

    private void configureEdgeToEdge() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Theme.BG);
        }
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void showNewSheet() {
        Dialog dialog = new Dialog(this);
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(16), dp(24), dp(16), dp(30));
        sheet.setBackground(rounded(0xfffffbf6, 22));
        TextView title = text("新建", 20, Typeface.BOLD, Theme.INK); title.setGravity(Gravity.CENTER);
        sheet.addView(title, margins(-1, dp(44), 0, 0, 0, 14));
        sheet.addView(newSheetRow("✎", "新建动作", "一条提示词指令", () -> { dialog.dismiss(); openEditor("action"); }), margins(-1, dp(82), 0, 0, 0, 12));
        sheet.addView(newSheetRow("▱", "新建分组", "收纳几个动作，菜单里成二级子菜单", () -> showCreateGroupDialog(dialog)), new LinearLayout.LayoutParams(-1, dp(82)));
        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.28f); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) { shown.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); shown.setGravity(Gravity.BOTTOM); }
        });
        dialog.show();
    }

    private void showCreateGroupDialog(Dialog newSheet) {
        EditText nameInput = new EditText(this);
        nameInput.setHint("分组名字");
        nameInput.setTextSize(17);
        nameInput.setSingleLine(true);
        nameInput.setTextColor(Theme.INK);
        nameInput.setHintTextColor(Theme.FAINT);
        nameInput.setPadding(dp(14), 0, dp(14), 0);
        nameInput.setBackground(outlined(Color.WHITE, 12, 0xffe0d8cc, 1, 0, 0));
        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(8), dp(16), dp(8));
        content.addView(nameInput, new LinearLayout.LayoutParams(-1, dp(54)));
        IosDialog.showRequiredChoice(this, "新建分组", content, "创建", () -> {
            String label = nameInput.getText().toString().trim();
            if (label.isEmpty()) { showError("请输入分组名字"); return; }
            PromptNode group = new PromptNode();
            group.id = PromptTree.newUserId();
            group.type = "group";
            group.label = label;
            group.origin = "user";
            io.execute(() -> {
                String error = store.add(group, null);
                runOnUiThread(() -> {
                    if (error != null) showError(error);
                    else { newSheet.dismiss(); render(store.items()); }
                });
            });
        }, "取消", null);
    }

    private View newSheetRow(String symbol, String title, String subtitle, Runnable action) {
        LinearLayout row = horizontal(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14), dp(10), dp(12), dp(10));
        row.setBackground(outlined(Color.WHITE, 14, 0xffe6ddd1, 1, 0, 0));
        ImageView icon = iconTile("✎".equals(symbol) ? R.drawable.ic_settings_pen : R.drawable.ic_prompt_folder, "✎".equals(symbol)); addIconWithSpacing(row, icon, 48);
        LinearLayout copy = vertical(); copy.addView(text(title, 17, Typeface.BOLD, Theme.INK)); copy.addView(text(subtitle, 13, Typeface.NORMAL, Theme.SECONDARY));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(trailingChevron());
        row.setOnClickListener(v -> action.run()); return row;
    }

    private void openEditor(String idOrType) {
        Intent intent = new Intent(this, PromptEditActivity.class);
        if ("action".equals(idOrType) || "group".equals(idOrType)) intent.putExtra("createType", idOrType);
        else intent.putExtra("promptId", idOrType);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
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
        IosDialog.showAutoHeight(this, "恢复默认提示词", "会补回缺少的系统提示词，不会删除自建内容。", "恢复", () -> io.execute(() -> {
                    String error = store.restoreDefaults();
                    runOnUiThread(() -> { if (error != null) showError(error); render(store.items()); });
                }), "取消", null, false, false);
    }

    private void showError(String message) { errorBanner.setText(message); errorBanner.setVisibility(View.VISIBLE); }
    private String normalIntro() { return "一套指令，长按文字或图片时按『适用于』自动筛选。改过的系统项标『已自定义』，自己建的标『自建』。"; }
    private String originSuffix(PromptNode node) { return "custom".equals(node.origin) ? "  · 已自定义" : ("user".equals(node.origin) ? "  · 自建" : ""); }

    private static PromptNode find(List<PromptNode> items, String id) {
        for (PromptNode node : items) { if (id.equals(node.id)) return node; PromptNode child = find(node.children, id); if (child != null) return child; }
        return null;
    }

    private static String parentOf(List<PromptNode> items, String id) {
        for (PromptNode node : items) for (PromptNode child : node.children) if (id.equals(child.id)) return node.id;
        return null;
    }

    private static int indexIn(List<PromptNode> items, String parentId, String id) {
        List<PromptNode> list = items;
        if (parentId != null) { PromptNode parent = find(items, parentId); if (parent != null) list = parent.children; }
        for (int i = 0; i < list.size(); i++) if (id.equals(list.get(i).id)) return i;
        return list.size();
    }

    private LinearLayout vertical() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout horizontal() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private View divider(int left) { View view = new View(this); view.setBackgroundColor(DIVIDER); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.leftMargin = dp(left); view.setLayoutParams(p); return view; }
    private TextView text(String value, int sp, int style, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, style); return v; }
    private TextView squareButton(String value, boolean accent) { TextView v = text(value, 22, Typeface.NORMAL, accent ? Color.WHITE : Theme.INK); v.setGravity(Gravity.CENTER); v.setIncludeFontPadding(false); v.setBackground(rounded(accent ? Theme.ACCENT : Color.WHITE, 12)); v.setElevation(dp(2)); return v; }
    private void addIconWithSpacing(LinearLayout parent, ImageView icon, int size) { LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(size), dp(size)); iconLp.rightMargin = dp(12); parent.addView(icon, iconLp); }
    private ImageView iconTile(int iconResId, boolean accent) { ImageView v = new ImageView(this); v.setImageResource(iconResId); v.setColorFilter(accent ? Theme.ACCENT : Theme.SECONDARY); v.setScaleType(ImageView.ScaleType.CENTER); v.setPadding(dp(8), dp(8), dp(8), dp(8)); v.setBackground(rounded(accent ? Theme.ACCENT_SOFT : TILE_NEUTRAL, 10)); return v; }
    private GradientDrawable rounded(int color, int radius) { return outlined(color, radius, Color.TRANSPARENT, 0, 0, 0); }
    private GradientDrawable outlined(int color, int radius, int stroke, int width, int dash, int gap) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if (width > 0) d.setStroke(dp(width), stroke, dp(dash), dp(gap)); return d; }
    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
