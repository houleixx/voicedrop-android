package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.core.PromptTree;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.PromptStore;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.IosSwitch;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.ui.SystemBarDefaults;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PromptEditActivity extends Activity {
    private static final int DIVIDER = 0xffefe7d9;
    private static final int TILE_NEUTRAL = 0xfff2eee7;
    private static final int INPUT_STROKE = 0xffe5ded2;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private PromptStore store;
    private PromptNode original;
    private EditText labelInput;
    private EditText promptInput;
    private boolean textChecked;
    private boolean imageChecked;
    private LinearLayout textCardLayout, imageCardLayout;
    private TextView textCard, imageCard;
    private ImageView textCheck, imageCheck;
    private LinearLayout shareArea;
    private TextView shareVersionWarning;
    private TextView saveButton;
    private boolean saving;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new PromptStore(this, new AuthStore(this), new HttpClient());
        String id = getIntent().getStringExtra("promptId");
        String createType = getIntent().getStringExtra("createType");
        original = id == null ? newNode("group".equals(createType) ? "group" : "action") : find(store.items(), id);
        if (original == null) { finish(); return; }

        textChecked = original.appliesTo.contains("text");
        imageChecked = original.appliesTo.contains("image");

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // Top bar
        FrameLayout top = new FrameLayout(this);
        SystemBarDefaults.applyTopInsets(top, dp(12), dp(8), dp(16), dp(8));
        top.addView(header(id), new FrameLayout.LayoutParams(-1, dp(48), Gravity.CENTER_VERTICAL));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        LinearLayout form = vertical();
        SystemBarDefaults.applyBottomInsets(form, dp(18), 0, dp(18), dp(20));
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (!original.isGroup()) {
            // 菜单里的名字
            form.addView(label("菜单里的名字"), margins(-1, -2, 0, 0, 0, 6));
            labelInput = cardInput(original.label, false);
            form.addView(labelInput, new LinearLayout.LayoutParams(-1, dp(54)));

            // 提示词
            form.addView(label("提示词"), margins(-1, -2, 0, 14, 0, 6));
            promptInput = cardInput(original.prompt == null ? "" : original.prompt, true);
            form.addView(promptInput, new LinearLayout.LayoutParams(-1, dp(160)));

            // 适用于
            form.addView(appliesHeader(), margins(-1, -2, 0, 14, 0, 6));
            form.addView(appliesCards(), new LinearLayout.LayoutParams(-1, -2));

            // 分享仅在提示词已保存后提供；新建时还没有可分享的 ID。
            if (id != null) {
                shareArea = vertical();
                shareArea.setPadding(0, dp(18), 0, dp(8));
                form.addView(shareArea);
                loadShareState();
            }
        } else {
            // Group editing: just label
            form.addView(label("分组名字"), margins(-1, -2, 0, 0, 0, 6));
            labelInput = cardInput(original.label, false);
            form.addView(labelInput, new LinearLayout.LayoutParams(-1, dp(54)));
        }

        updateSaveState();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    private void configureEdgeToEdge() {
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
    }

    private View header(String id) {
        FrameLayout top = new FrameLayout(this);

        // Back button (identical to InstructionSettingsActivity)
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
        backTouch.setOnClickListener(v -> finishWithSlide());
        top.addView(backTouch, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL));

        // Title
        String titleText = id == null
                ? (original.isGroup() ? "新建分组" : "提示词")
                : "编辑提示词";
        TextView title = text(titleText, 24, Typeface.BOLD, Theme.INK);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        // Save button
        saveButton = text("保存", 16, Typeface.BOLD, Theme.SECONDARY);
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setBackground(rounded(0xffc2b9a8, 12));
        saveButton.setPadding(dp(20), dp(10), dp(20), dp(10));
        saveButton.setOnClickListener(v -> save());
        top.addView(saveButton, new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        return top;
    }

    private void finishWithSlide() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }
    @Override public void onBackPressed() { finishWithSlide(); }

    private void updateSaveState() {
        if (saving) {
            saveButton.setText("保存中…");
            saveButton.setTextColor(Theme.SECONDARY);
            saveButton.setBackground(rounded(0xffe5ded2, 12));
            return;
        }
        String name = labelInput == null ? "" : labelInput.getText().toString().trim();
        String prompt = promptInput == null ? "" : promptInput.getText().toString().trim();
        boolean canSave = !name.isEmpty() && (original.isGroup() || (!prompt.isEmpty() && (textChecked || imageChecked)));
        saveButton.setTextColor(canSave ? Color.WHITE : Theme.SECONDARY);
        saveButton.setBackground(rounded(canSave ? Theme.ACCENT : 0xffc2b9a8, 12));
    }

    private void save() {
        String name = labelInput == null ? "" : labelInput.getText().toString().trim();
        if (name.isEmpty()) { labelInput.setError("请输入名称"); return; }
        PromptNode edited = original.copy();
        edited.label = name;
        if (!edited.isGroup()) {
            String instruction = promptInput == null ? "" : promptInput.getText().toString().trim();
            if (instruction.isEmpty()) { promptInput.setError("请输入提示词"); return; }
            edited.prompt = instruction;
            edited.appliesTo.clear();
            if (textChecked) edited.appliesTo.add("text");
            if (imageChecked) edited.appliesTo.add("image");
            if (edited.appliesTo.isEmpty()) { toast("至少选择一种应用场景"); return; }
        }
        boolean creating = getIntent().getStringExtra("promptId") == null;
        if (!creating && original.isSystem()) edited = PromptTree.fork(edited, PromptTree::newUserId);
        PromptNode result = edited;
        saving = true;
        updateSaveState();
        io.execute(() -> {
            String error = creating ? store.add(result, null) : store.replace(original.id, result);
            runOnUiThread(() -> {
                saving = false;
                updateSaveState();
                if (error == null) { finishWithSlide(); }
                else { labelInput.setError(error); }
            });
        });
    }

    // --- UI building blocks ---

    private TextView label(String value) {
        TextView v = text(value, 14, Typeface.NORMAL, Theme.SECONDARY);
        v.setPadding(0, 0, 0, 0);
        return v;
    }

    private EditText cardInput(String value, boolean multiline) {
        EditText v = new EditText(this);
        v.setText(value);
        v.setTextSize(16);
        v.setTextColor(Theme.INK);
        v.setHintTextColor(0xffc9c6c1);
        v.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        v.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackground(strokedRound(Theme.CARD, 12, INPUT_STROKE, 1, 0, 0));
        if (multiline) v.setMinLines(4);
        v.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSaveState();
                updateShareVersionWarning();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        return v;
    }

    private View appliesHeader() {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("适用于", 14, Typeface.BOLD, Theme.INK));
        // spacer pushes hint to the right
        row.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1));
        TextView hint = text("决定在哪种长按里出现", 12, Typeface.NORMAL, Theme.FAINT);
        row.addView(hint, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private View appliesCards() {
        LinearLayout row = horizontal();

        // 文字 card
        textCardLayout = applyCard("文字", R.drawable.ic_settings_pen, textChecked);
        Object[] textTag = (Object[]) textCardLayout.getTag();
        textCard = (TextView) textTag[0];
        ImageView textIcon = (ImageView) textTag[1];
        textCheck = textCardLayout.findViewById(android.R.id.text1);
        textCardLayout.setOnClickListener(v -> {
            textChecked = !textChecked;
            updateApplyCard(textCardLayout, textCard, textCheck, textIcon, textChecked);
            updateSaveState();
            updateShareVersionWarning();
        });
        row.addView(textCardLayout, new LinearLayout.LayoutParams(0, -2, 1));

        // spacer between cards
        row.addView(new View(this), new LinearLayout.LayoutParams(dp(16), -1));

        // 图片 card
        imageCardLayout = applyCard("图片", R.drawable.ic_image, imageChecked);
        Object[] imageTag = (Object[]) imageCardLayout.getTag();
        imageCard = (TextView) imageTag[0];
        ImageView imageIcon = (ImageView) imageTag[1];
        imageCheck = imageCardLayout.findViewById(android.R.id.text1);
        imageCardLayout.setOnClickListener(v -> {
            imageChecked = !imageChecked;
            updateApplyCard(imageCardLayout, imageCard, imageCheck, imageIcon, imageChecked);
            updateSaveState();
            updateShareVersionWarning();
        });
        row.addView(imageCardLayout, new LinearLayout.LayoutParams(0, -2, 1));

        return row;
    }

    private LinearLayout applyCard(String label, int iconRes, boolean checked) {
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        updateCardBackground(card, checked);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(checked ? Theme.ACCENT : Theme.FAINT, PorterDuff.Mode.SRC_IN);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView labelView = text(label, 14, Typeface.NORMAL, checked ? Theme.ACCENT : Theme.SECONDARY);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, dp(6), 0, 0);
        card.addView(labelView);

        // Checkbox: checked = orange checkmark, unchecked = gray outlined square
        ImageView check = new ImageView(this);
        check.setId(android.R.id.text1);
        check.setImageResource(checked ? R.drawable.ic_checkbox_checked_flat : R.drawable.ic_checkbox_unchecked_flat);
        check.setScaleType(ImageView.ScaleType.CENTER);
        card.addView(check, new LinearLayout.LayoutParams(dp(28), dp(28)));
        check.setPadding(0, dp(2), 0, 0);

        card.setTag(new Object[]{labelView, icon});
        return card;
    }

    private void updateCardBackground(LinearLayout card, boolean checked) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.CARD);
        bg.setCornerRadius(dp(14));
        if (checked) {
            bg.setStroke(dp(2), Theme.ACCENT);
        } else {
            bg.setStroke(dp(1), INPUT_STROKE);
        }
        card.setBackground(bg);
    }

    private void updateApplyCard(LinearLayout card, TextView labelView, ImageView checkView, ImageView iconView, boolean checked) {
        updateCardBackground(card, checked);
        iconView.setColorFilter(checked ? Theme.ACCENT : Theme.FAINT, PorterDuff.Mode.SRC_IN);
        labelView.setTextColor(checked ? Theme.ACCENT : Theme.SECONDARY);
        checkView.setImageResource(checked ? R.drawable.ic_checkbox_checked_flat : R.drawable.ic_checkbox_unchecked_flat);
    }

    private void loadShareState() {
        shareArea.removeAllViews();
        shareArea.addView(text("正在加载分享状态…", 13, Typeface.NORMAL, Theme.FAINT));
        io.execute(() -> {
            Map<String, PromptStore.ShareState> states = store.shareStates();
            PromptStore.ShareState state = states.get(original.id);
            runOnUiThread(() -> renderShare(state == null ? new PromptStore.ShareState("", false) : state));
        });
    }

    private void renderShare(PromptStore.ShareState state) {
        renderShare(state, null);
    }

    private void renderShare(PromptStore.ShareState state, String error) {
        shareArea.removeAllViews();
        shareVersionWarning = null;

        LinearLayout card = vertical();
        card.setBackground(rounded(Theme.CARD, 14));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = vertical();
        heading.addView(text("分享这条提示词", 15, Typeface.NORMAL, Theme.INK));
        TextView desc = text(state.sharing
                        ? "分享中，关闭后分享码立即失效"
                        : "开启后，任何人对 VoiceDrop 说出分享码，或打开链接，就能看到并一次性使用这条提示词",
                12, Typeface.NORMAL, Theme.FAINT);
        desc.setLineSpacing(0, 1.15f);
        desc.setPadding(0, dp(2), dp(8), 0);
        heading.addView(desc);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));

        IosSwitch toggle = new IosSwitch(this);
        toggle.setChecked(state.sharing);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            button.setEnabled(false);
            io.execute(() -> {
                PromptStore.ShareState next = store.setSharing(original.id, checked);
                runOnUiThread(() -> {
                    if (next.error == null) renderShare(next);
                    else renderShare(state, next.error);
                });
            });
        });
        header.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));

        if (error != null) {
            TextView errorView = text(error, 12, Typeface.NORMAL, Theme.ACCENT);
            errorView.setPadding(0, dp(8), 0, 0);
            card.addView(errorView);
        }

        if (state.sharing && !state.code.isEmpty()) {
            TextView codeLabel = text(state.code, 34, Typeface.BOLD, Theme.INK);
            codeLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            codeLabel.setLetterSpacing(0.18f);
            codeLabel.setGravity(Gravity.CENTER);
            codeLabel.setPadding(0, dp(12), 0, 0);
            card.addView(codeLabel);

            TextView linkLabel = text("voicedrop.cn/" + state.code, 13, Typeface.NORMAL, Theme.SECONDARY);
            linkLabel.setGravity(Gravity.CENTER);
            linkLabel.setPadding(0, dp(4), 0, 0);
            card.addView(linkLabel);

            LinearLayout actions = horizontal();
            actions.setPadding(0, dp(12), 0, 0);
            card.addView(actions);

            actions.addView(shareAction("复制数字", R.drawable.ic_copy_flat, () -> copy(state.code)),
                    new LinearLayout.LayoutParams(0, dp(40), 1));
            actions.addView(new View(this), new LinearLayout.LayoutParams(dp(8), 1));
            actions.addView(shareAction("复制链接", R.drawable.ic_link_flat, () -> copy(Api.sharePage(state.code))),
                    new LinearLayout.LayoutParams(0, dp(40), 1));
            actions.addView(new View(this), new LinearLayout.LayoutParams(dp(8), 1));
            actions.addView(shareAction("分享…", R.drawable.ic_share_up, () -> sharePrompt(state.code)),
                    new LinearLayout.LayoutParams(0, dp(40), 1));

            shareVersionWarning = text("分享的始终是已保存的版本", 12, Typeface.NORMAL, Theme.FAINT);
            shareVersionWarning.setGravity(Gravity.CENTER);
            shareVersionWarning.setPadding(0, dp(8), 0, 0);
            card.addView(shareVersionWarning);
            updateShareVersionWarning();
        }

        shareArea.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private View shareAction(String label, int iconRes, Runnable action) {
        LinearLayout button = horizontal();
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(6), dp(8), dp(6), dp(8));
        button.setBackground(rounded(Theme.ACCENT_SOFT, 8));

        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, iconRes, Theme.ACCENT);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.addView(icon, new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView labelView = text(label, 13, Typeface.BOLD, Theme.ACCENT);
        labelView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.leftMargin = dp(4);
        button.addView(labelView, labelParams);

        button.setOnClickListener(x -> action.run());
        return button;
    }

    private void copy(String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("VoiceDrop", value));
        toast("已复制");
    }

    private void sharePrompt(String code) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, Api.sharePage(code));
        startActivity(Intent.createChooser(intent, "分享提示词"));
    }

    private boolean hasUnsavedChanges() {
        String name = labelInput == null ? "" : labelInput.getText().toString().trim();
        String instruction = promptInput == null ? "" : promptInput.getText().toString().trim();
        return !name.equals(original.label == null ? "" : original.label.trim())
                || !instruction.equals(original.prompt == null ? "" : original.prompt.trim())
                || textChecked != original.appliesTo.contains("text")
                || imageChecked != original.appliesTo.contains("image");
    }

    private void updateShareVersionWarning() {
        if (shareVersionWarning != null) {
            shareVersionWarning.setVisibility(hasUnsavedChanges() ? View.VISIBLE : View.GONE);
        }
    }

    // --- Helpers ---

    private static PromptNode find(java.util.List<PromptNode> items, String id) {
        for (PromptNode node : items) { if (id.equals(node.id)) return node; PromptNode child = find(node.children, id); if (child != null) return child; }
        return null;
    }

    private static PromptNode newNode(String type) {
        PromptNode node = new PromptNode(); node.id = PromptTree.newUserId(); node.type = type; node.label = ""; node.origin = "user";
        if ("action".equals(type)) { node.appliesTo.add("text"); node.appliesTo.add("image"); } return node;
    }

    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout horizontal() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private TextView text(String value, int sp, int style, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, style); return v;
    }
    private TextView squareButton(String value, boolean accent) {
        TextView v = text(value, 22, Typeface.NORMAL, accent ? Color.WHITE : Theme.INK);
        v.setGravity(Gravity.CENTER); v.setIncludeFontPadding(false);
        v.setBackground(rounded(accent ? Theme.ACCENT : Color.WHITE, 12)); v.setElevation(dp(2)); return v;
    }
    private GradientDrawable rounded(int color, int radius) { return strokedRound(color, radius, Color.TRANSPARENT, 0, 0, 0); }
    private GradientDrawable strokedRound(int color, int radius, int stroke, int width, int dash, int gap) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (width > 0) d.setStroke(dp(width), stroke, dp(dash), dp(gap)); return d;
    }
    private GradientDrawable outlined(int color, int radius, int stroke, int width, int dash, int gap) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (width > 0) d.setStroke(dp(width), stroke, dp(dash), dp(gap)); return d;
    }
    private LinearLayout.LayoutParams margins(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) {
        runOnUiThread(() -> com.baixingai.voicedrop.ui.SimpleToast.show(this, message));
    }
}
