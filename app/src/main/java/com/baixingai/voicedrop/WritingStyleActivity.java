package com.baixingai.voicedrop;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.data.WritingStyleHistoryCache;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.ui.WritingStylePresentation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lists every writing-style version and edits one version in a bottom sheet. */
public final class WritingStyleActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SettingsStore store;
    private WritingStyleHistoryCache cache;
    private LinearLayout content;
    private JSONArray versions = new JSONArray();
    private int head;
    private boolean loading;
    private int loadGeneration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AuthStore auth = new AuthStore(this);
        store = new SettingsStore(auth, new HttpClient());
        cache = new WritingStyleHistoryCache(this, auth.libraryCacheIdentity());
        configureEdgeToEdge();

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);

        LinearLayout page = vertical();
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        page.addView(new PageTitleBar(this, "写作风格", this::finishWithPageTransition),
                new LinearLayout.LayoutParams(-1, -2));

        BouncyScrollView scroll = new BouncyScrollView(this);
        scroll.setFillViewport(true);
        content = vertical();
        SystemBarDefaults.applyBottomInsets(content, dp(16), dp(6), dp(16), dp(40));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (!applyHistory(cache.read())) renderLoading();
        loadHistory();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override public void onBackPressed() {
        finishWithPageTransition();
    }

    private void loadHistory() {
        if (loading) return;
        loading = true;
        int generation = ++loadGeneration;
        io.execute(() -> {
            try {
                JSONObject history = store.loadStyleHistory();
                JSONArray next = history.optJSONArray("versions");
                if (next == null) throw new IllegalStateException("style history unavailable");
                int nextHead = history.optInt("head", newestVersion(next));
                runOnUiThread(() -> {
                    if (generation != loadGeneration) return;
                    loading = false;
                    cache.write(history);
                    versions = next == null ? new JSONArray() : next;
                    head = nextHead;
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (generation != loadGeneration) return;
                    loading = false;
                    if (cache.read() == null) renderError(error.getMessage());
                    else toast("刷新失败，已显示本地缓存");
                });
            }
        });
    }

    private void renderLoading() {
        content.removeAllViews();
        LoadingStateView state = new LoadingStateView(this, "正在加载写作风格...");
        content.addView(state, new LinearLayout.LayoutParams(-1, dp(160)));
    }

    private void renderError(String message) {
        content.removeAllViews();
        TextView state = text("写作风格加载失败\n点击重试", 14, Theme.SECONDARY, Typeface.NORMAL);
        state.setGravity(Gravity.CENTER);
        state.setLineSpacing(dp(4), 1f);
        state.setOnClickListener(view -> {
            renderLoading();
            loadHistory();
        });
        content.addView(state, new LinearLayout.LayoutParams(-1, dp(180)));
        if (message != null && !message.isEmpty()) toast("写作风格加载失败：" + message);
    }

    private void render() {
        content.removeAllViews();

        TextView intro = text("默认风格会用于之后生成的文章。点击任一版本可查看、修改或设为默认。",
                13, Theme.SECONDARY, Typeface.NORMAL);
        intro.setLineSpacing(0, 1.18f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2);
        introLp.setMargins(dp(4), 0, dp(4), dp(12));
        content.addView(intro, introLp);

        if (versions.length() == 0) {
            LinearLayout empty = vertical();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(22), dp(28), dp(22), dp(28));
            empty.setBackground(cardBackground(false));
            empty.addView(text("还没有写作风格", 17, Theme.INK, Typeface.BOLD));
            TextView hint = text("创建第一份风格后，VoiceDrop 会按它生成文章。",
                    13, Theme.SECONDARY, Typeface.NORMAL);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-2, -2);
            hintLp.setMargins(0, dp(7), 0, dp(18));
            empty.addView(hint, hintLp);
            TextView create = actionButton("创建写作风格");
            create.setOnClickListener(view -> showEditor(0, "", false));
            empty.addView(create, new LinearLayout.LayoutParams(-1, dp(52)));
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        for (int index = versions.length() - 1; index >= 0; index--) {
            JSONObject item = versions.optJSONObject(index);
            if (item == null) continue;
            int version = item.optInt("v", index + 1);
            String style = item.optString("style", "");
            boolean currentDefault = version == head;
            View row = versionCard(version, style, currentDefault);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            if (content.getChildCount() > 1) rowLp.setMargins(0, dp(10), 0, 0);
            content.addView(row, rowLp);
        }
    }

    private View versionCard(int version, String style, boolean currentDefault) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(14));
        card.setBackground(cardBackground(currentDefault));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription("v" + version + (currentDefault ? "，当前默认" : ""));
        card.setOnClickListener(view -> showEditor(version, style, currentDefault));

        TextView versionTile = text("v" + version, 15,
                currentDefault ? Theme.ACCENT : Theme.SECONDARY, Typeface.BOLD);
        versionTile.setGravity(Gravity.CENTER);
        versionTile.setBackground(round(currentDefault ? Theme.ACCENT_SOFT : 0xfff1ece3, 10));
        card.addView(versionTile, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = vertical();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(13), 0, dp(8), 0);
        card.addView(copy, copyLp);

        TextView title = text(WritingStylePresentation.displayName(style), 16,
                Theme.INK, currentDefault ? Typeface.BOLD : Typeface.NORMAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView preview = text(WritingStylePresentation.preview(style), 12,
                Theme.SECONDARY, Typeface.NORMAL);
        preview.setSingleLine(true);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2);
        previewLp.setMargins(0, dp(5), 0, 0);
        copy.addView(preview, previewLp);

        LinearLayout trailing = vertical();
        trailing.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (currentDefault) {
            TextView badge = text("默认", 12, Theme.ACCENT, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(round(Theme.ACCENT_SOFT, 9));
            trailing.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(28)));
        } else {
            TextView count = text(style.trim().length() + " 字", 12, Theme.FAINT, Typeface.BOLD);
            count.setGravity(Gravity.RIGHT);
            trailing.addView(count, new LinearLayout.LayoutParams(-2, dp(28)));
        }
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right_flat);
        chevron.setColorFilter(Theme.FAINT);
        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        chevronLp.gravity = Gravity.RIGHT;
        chevronLp.setMargins(0, dp(4), 0, 0);
        trailing.addView(chevron, chevronLp);
        card.addView(trailing, new LinearLayout.LayoutParams(-2, -2));
        return card;
    }

    private void showEditor(int version, String original, boolean currentDefault) {
        boolean existingVersion = version > 0;
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(12), dp(18), dp(12));

        TextView explanation = text(existingVersion
                        ? "正在基于 v" + version + " 编辑。修改后会保存为新版本，并自动设为默认。"
                        : "写下希望文章遵循的语气、结构和表达习惯。保存后会成为默认风格。",
                13, Theme.SECONDARY, Typeface.NORMAL);
        explanation.setLineSpacing(0, 1.18f);
        form.addView(explanation, new LinearLayout.LayoutParams(-1, -2));

        EditText input = new EditText(this);
        input.setGravity(Gravity.TOP);
        input.setTextSize(16);
        input.setTextColor(Theme.INK);
        input.setHintTextColor(Theme.FAINT);
        input.setHint("例如：短句、口语化，保留现场细节，结尾自然收束。");
        input.setBackground(round(0xfff7f2ec, 14));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setText(original);
        input.setSelection(input.length());
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(330));
        inputLp.setMargins(0, dp(12), 0, dp(12));
        form.addView(input, inputLp);

        TextView action = actionButton("");
        form.addView(action, new LinearLayout.LayoutParams(-1, dp(56)));

        final IosDialog[] dialog = {null};
        Runnable updateAction = () -> updateEditorAction(action, existingVersion,
                currentDefault, original, input.getText().toString());
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateAction.run();
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        updateAction.run();

        action.setOnClickListener(view -> {
            WritingStylePresentation.Action next = WritingStylePresentation.action(
                    existingVersion, currentDefault, original, input.getText().toString());
            if (next == WritingStylePresentation.Action.CURRENT_DEFAULT) return;
            String edited = input.getText().toString().trim();
            if (edited.isEmpty()) {
                toast("写作风格不能为空");
                return;
            }
            action.setEnabled(false);
            action.setText("保存中…");
            io.execute(() -> {
                try {
                    if (next == WritingStylePresentation.Action.SET_DEFAULT) {
                        store.saveStyleHead(version);
                        cache.moveHead(version);
                    } else {
                        int savedHead = store.saveStyleAndReturnHead(edited);
                        if (savedHead <= 0) savedHead = newestVersion(versions) + 1;
                        cache.appendVersion(savedHead, edited);
                    }
                    runOnUiThread(() -> {
                        String message = next == WritingStylePresentation.Action.SET_DEFAULT
                                ? "已设为默认风格" : "新版本已保存并设为默认";
                        toast(message);
                        if (dialog[0] != null) dialog[0].dismissAnimated(this::reloadAfterSave);
                        else reloadAfterSave();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        toast("写作风格保存失败：" + error.getMessage());
                        updateAction.run();
                    });
                }
            });
        });

        dialog[0] = IosDialog.showBottomSheet(this,
                existingVersion ? "v" + version + " 写作风格" : "新建写作风格",
                form, 466, null, null, null, null, true, false);
        input.setOnFocusChangeListener((view, focused) -> {
            if (!focused) return;
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void updateEditorAction(TextView button, boolean existingVersion,
                                    boolean currentDefault, String original, String edited) {
        WritingStylePresentation.Action action = WritingStylePresentation.action(
                existingVersion, currentDefault, original, edited);
        button.setText(WritingStylePresentation.actionLabel(action));
        boolean enabled = action != WritingStylePresentation.Action.CURRENT_DEFAULT
                && !edited.trim().isEmpty();
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.55f);
        button.setBackground(round(enabled ? Theme.ACCENT : 0xffc9c0b3, 12));
    }

    private void reloadAfterSave() {
        if (!applyHistory(cache.read())) renderLoading();
        loading = false;
        loadHistory();
    }

    private boolean applyHistory(JSONObject history) {
        if (history == null) return false;
        JSONArray cachedVersions = history.optJSONArray("versions");
        if (cachedVersions == null) return false;
        versions = cachedVersions;
        head = history.optInt("head", newestVersion(cachedVersions));
        render();
        return true;
    }

    private int newestVersion(JSONArray items) {
        if (items == null || items.length() == 0) return 0;
        JSONObject latest = items.optJSONObject(items.length() - 1);
        return latest == null ? 0 : latest.optInt("v", items.length());
    }

    private TextView actionButton(String label) {
        TextView button = text(label, 16, 0xffffffff, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(Theme.ACCENT, 12));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private GradientDrawable cardBackground(boolean selected) {
        GradientDrawable background = round(selected ? 0xfffffbf8 : Theme.CARD, 14);
        background.setStroke(dp(1), selected ? 0xffe8b9aa : Theme.BORDER_CHROME);
        return background;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private void toast(String message) {
        SimpleToast.show(this, message);
    }

    private void configureEdgeToEdge() {
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
