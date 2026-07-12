package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
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
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.UIConfigStore;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.IosSwitch;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InstructionSettingsActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private UIConfigStore store;
    private LinearLayout content;
    private List<UIConfigStore.InstructionItem> items = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new UIConfigStore(this, new AuthStore(this), new HttpClient());
        configureEdgeToEdge();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        setContentView(page);

        FrameLayout top = new FrameLayout(this);
        top.setPadding(dp(12), dp(14) + getStatusBarHeight(), dp(16), dp(10));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout back = navButton();
        back.setOnClickListener(v -> finishWithTransition());
        top.addView(back, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        top.addView(text("提示词", 24, Theme.INK, Typeface.BOLD), new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        BouncyScrollView scroll = new BouncyScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(36));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderLoading();
        loadItems();
    }

    @Override public void onBackPressed() {
        finishWithTransition();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void loadItems() {
        io.execute(() -> {
            try {
                List<UIConfigStore.InstructionItem> fresh = store.loadCustomItems();
                runOnUiThread(() -> {
                    items = fresh;
                    renderItems();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    content.removeAllViews();
                    TextView fail = text("加载失败：" + e.getMessage(), 15, Theme.SECONDARY, Typeface.NORMAL);
                    fail.setGravity(Gravity.CENTER);
                    content.addView(fail, new LinearLayout.LayoutParams(-1, dp(120)));
                });
            }
        });
    }

    private void renderLoading() {
        content.removeAllViews();
        TextView loading = text("正在加载…", 15, Theme.SECONDARY, Typeface.NORMAL);
        loading.setGravity(Gravity.CENTER);
        content.addView(loading, new LinearLayout.LayoutParams(-1, dp(120)));
    }

    private void renderItems() {
        content.removeAllViews();
        TextView hint = text("长按菜单里的动作名称和提示词可以按自己的说法调整；留空会回到默认。", 13, Theme.SECONDARY, Typeface.NORMAL);
        hint.setLineSpacing(dp(4), 1f);
        hint.setPadding(dp(4), 0, dp(4), dp(12));
        content.addView(hint);
        LinearLayout card = card();
        for (int i = 0; i < items.size(); i++) {
            UIConfigStore.InstructionItem item = items.get(i);
            card.addView(row(item));
            if (i < items.size() - 1) card.addView(divider());
        }
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private View row(UIConfigStore.InstructionItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(12), dp(12), dp(12));
        row.setAlpha(item.hidden ? 0.55f : 1f);
        row.setClickable(true);
        row.setOnClickListener(v -> showEditor(item));

        TextView main = text(item.effectiveLabel(), 16, Theme.INK, Typeface.BOLD);
        TextView sub = text(item.hidden ? "已从菜单隐藏" : trim(item.effectiveInstruction(), 44),
                12, Theme.SECONDARY, Typeface.NORMAL);
        sub.setPadding(0, dp(4), 0, 0);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(main);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        if (item.customized()) {
            TextView badge = text("已自定义", 12, Theme.RED, Typeface.BOLD);
            badge.setPadding(dp(8), 0, dp(8), 0);
            row.addView(badge);
        }
        if (item.sharing) {
            TextView badge = text("分享中", 12, Theme.RED, Typeface.BOLD);
            badge.setPadding(dp(8), 0, dp(4), 0);
            row.addView(badge);
        }
        row.addView(text("›", 28, 0xffcfc6b6, Typeface.NORMAL));
        return row;
    }

    private void showEditor(UIConfigStore.InstructionItem item) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), dp(8));

        TextView nameLabel = label("菜单里的名字");
        form.addView(nameLabel);
        EditText name = input(item.customLabel == null ? "" : item.customLabel, "留空 = " + item.label, 44);
        form.addView(name, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView instructionLabel = label("我的提示词");
        instructionLabel.setPadding(0, dp(14), 0, dp(7));
        form.addView(instructionLabel);
        EditText instruction = input(item.override == null ? "" : item.override, "留空 = 使用默认提示词", 128);
        instruction.setSingleLine(false);
        instruction.setGravity(Gravity.TOP);
        form.addView(instruction, new LinearLayout.LayoutParams(-1, dp(128)));

        LinearLayout hiddenRow = new LinearLayout(this);
        hiddenRow.setGravity(Gravity.CENTER_VERTICAL);
        hiddenRow.setPadding(0, dp(14), 0, 0);
        hiddenRow.addView(text("在菜单中隐藏", 15, Theme.INK, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
        IosSwitch hidden = new IosSwitch(this);
        hidden.setChecked(item.hidden);
        hiddenRow.addView(hidden);
        form.addView(hiddenRow);

        form.addView(promptShareCard(item));

        TextView defaults = text("默认提示词：\n" + item.defaultText, 12, Theme.FAINT, Typeface.NORMAL);
        defaults.setPadding(0, dp(14), 0, 0);
        defaults.setLineSpacing(dp(4), 1f);
        form.addView(defaults);

        IosDialog.showBottomSheet(this, item.label, form, 560, "保存", () -> {
            io.execute(() -> {
                try {
                    store.saveCustomItem(item.id, instruction.getText().toString(), name.getText().toString(), hidden.isChecked());
                    runOnUiThread(() -> {
                        toast("已保存");
                        loadItems();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> toast("保存失败：" + e.getMessage()));
                }
            });
        }, null, null, true, true);
    }

    private View promptShareCard(UIConfigStore.InstructionItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(14), dp(12), dp(12));
        card.setBackground(strokedRound(0xfffbf6ef, 10, 0xffeadccd));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("分享这条提示词", 15, Theme.INK, Typeface.BOLD));
        labels.addView(text(item.sharing ? "分享中，关闭后分享码立即失效" : "开启后可用分享码或链接一次性使用", 12, Theme.FAINT, Typeface.NORMAL));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        IosSwitch sharing = new IosSwitch(this);
        sharing.setChecked(item.sharing);
        row.addView(sharing);
        card.addView(row);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(0, dp(12), 0, 0);
        String[] code = {item.shareCode};
        boolean[] changing = {false};
        renderShareDetails(details, code[0]);
        details.setVisibility(item.sharing && code[0] != null ? View.VISIBLE : View.GONE);
        card.addView(details);

        sharing.setOnCheckedChangeListener((button, enabled) -> {
            if (changing[0]) return;
            changing[0] = true;
            button.setEnabled(false);
            io.execute(() -> {
                try {
                    UIConfigStore.ShareState state = store.setSharing(item.id, enabled);
                    runOnUiThread(() -> {
                        code[0] = state.code;
                        renderShareDetails(details, code[0]);
                        details.setVisibility(state.sharing && code[0] != null ? View.VISIBLE : View.GONE);
                        button.setEnabled(true);
                        changing[0] = false;
                        loadItems();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        button.setChecked(!enabled);
                        button.setEnabled(true);
                        changing[0] = false;
                        toast(e.getMessage());
                    });
                }
            });
        });
        return card;
    }

    private void renderShareDetails(LinearLayout details, String code) {
        details.removeAllViews();
        if (code == null || code.isEmpty()) return;
        TextView number = text(code, 30, Theme.INK, Typeface.BOLD);
        number.setLetterSpacing(0.12f);
        number.setGravity(Gravity.CENTER);
        details.addView(number, new LinearLayout.LayoutParams(-1, -2));
        TextView url = text(Api.sharePage(code), 13, Theme.SECONDARY, Typeface.NORMAL);
        url.setGravity(Gravity.CENTER);
        url.setPadding(0, dp(4), 0, dp(9));
        details.addView(url, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.addView(shareAction("复制数字", () -> copyText(code, "已复制分享码")));
        actions.addView(shareAction("复制链接", () -> copyText(Api.sharePage(code), "已复制链接")));
        actions.addView(shareAction("分享…", () -> sharePromptLink(code)));
        details.addView(actions, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private View shareAction(String title, Runnable action) {
        TextView view = text(title, 13, Theme.RED, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setBackground(strokedRound(0xfff8e8dd, 8, 0xfff8e8dd));
        view.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        view.setLayoutParams(lp);
        return view;
    }

    private void copyText(String value, String confirmation) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("VoiceDrop", value));
        toast(confirmation);
    }

    private void sharePromptLink(String code) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, Api.sharePage(code));
        startActivity(Intent.createChooser(intent, "分享提示词"));
    }

    private EditText input(String value, String hint, int heightDp) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setTextSize(15);
        input.setTextColor(Theme.INK);
        input.setHintTextColor(0xffc9c6c1);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackground(strokedRound(Theme.CARD, 10, 0xffe5ded2));
        input.setMinHeight(dp(heightDp));
        return input;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, Theme.FAINT, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        label.setPadding(0, 0, 0, dp(7));
        return label;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(strokedRound(Theme.CARD, 12, 0xffe5ded2));
        return card;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(0xffeee6db);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return view;
    }

    private FrameLayout navButton() {
        FrameLayout touch = new FrameLayout(this);
        touch.setClickable(true);
        FrameLayout bg = new FrameLayout(this);
        bg.setBackground(strokedRound(Theme.CARD, 11, 0xffe0d8cc));
        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, AliIconFont.BACK, Theme.INK);
        bg.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        touch.addView(bg, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        return touch;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private GradientDrawable strokedRound(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : dp(24);
    }

    private void configureEdgeToEdge() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Theme.BG);
        }
    }

    private void finishWithTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void toast(String message) {
        runOnUiThread(() -> SimpleToast.show(this, message));
    }
}
