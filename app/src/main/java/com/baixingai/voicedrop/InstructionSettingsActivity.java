package com.baixingai.voicedrop;

import android.app.Activity;
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
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.core.PromptTree;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.PromptStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.DialogWindowDefaults;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.PromptDragController;
import com.baixingai.voicedrop.ui.PromptListPresentation;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.ui.SystemBarDefaults;

import java.util.HashSet;
import java.util.ArrayList;
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
    private boolean savingSort;
    private boolean dragRenderPosted;
    private View openSwipeRow;
    private List<PromptStore.MarketItem> marketItems = new ArrayList<>();
    private final Set<String> importingMarketCodes = new HashSet<>();
    private String marketSort = "hot";
    private String marketScope = "";
    private boolean marketLoading;
    private boolean marketFailed;

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
        SystemBarDefaults.applyTopInsets(top, dp(12), dp(8), dp(16), dp(8));
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
        loadMarket();
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

    @Override public void onBackPressed() {
        if (savingSort) return;
        if (sorting) exitSort(false); else finishWithPageTransition();
    }

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

    private void showSortSaveAction(boolean loading) {
        showSortCancelAction();
        leftHeaderButton.setEnabled(!loading);
        leftHeaderButton.setAlpha(loading ? 0.45f : 1f);
        rightHeaderButton.setText(loading ? "保存中…" : "完成");
        rightHeaderButton.setTextSize(14);
        rightHeaderButton.setTextColor(Theme.ACCENT);
        rightHeaderButton.setBackgroundColor(Color.TRANSPARENT);
        rightHeaderButton.setEnabled(!loading);
        rightHeaderButton.setAlpha(loading ? 0.65f : 1f);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rightHeaderButton.getLayoutParams();
        params.width = dp(loading ? 72 : 48);
        rightHeaderButton.setLayoutParams(params);
        rightHeaderButton.setOnClickListener(v -> exitSort(true));
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
        openSwipeRow = null;
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
            card.setBackground(outlined(Color.WHITE, 12, Theme.BORDER_CHROME, 1, 0, 0));
            card.setClipToOutline(true);
            for (int i = 0; i < rows.size(); i++) {
                card.addView(promptRow(rows.get(i)));
                if (i < rows.size() - 1) card.addView(divider(59 + rows.get(i + 1).depth * 16));
            }
            content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }

        if (!sorting) {
            content.addView(marketSection(), margins(-1, -2, 0, 22, 0, 0));
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

    private View marketSection() {
        LinearLayout section = vertical();
        TextView heading = text("社区提示词", 13, Typeface.BOLD, Theme.SECONDARY);
        section.addView(heading, margins(-1, -2, 2, 0, 0, 10));

        LinearLayout filters = horizontal();
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.addView(marketFilter("热门", "hot", ""), marketChipParams(true));
        filters.addView(marketFilter("最新", "new", ""), marketChipParams(true));
        filters.addView(marketFilter("文字", "hot", "text"), marketChipParams(true));
        filters.addView(marketFilter("配图", "hot", "image"), marketChipParams(false));
        section.addView(filters, new LinearLayout.LayoutParams(-1, -2));

        if (marketLoading && marketItems.isEmpty()) {
            LinearLayout loading = vertical();
            loading.setGravity(Gravity.CENTER);
            ProgressBar spinner = new ProgressBar(this);
            spinner.setIndeterminate(true);
            spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Theme.ACCENT));
            loading.addView(spinner, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView state = text("正在加载社区提示词…", 13, Typeface.NORMAL, Theme.FAINT);
            loading.addView(state, margins(-2, -2, 0, 8, 0, 0));
            section.addView(loading, new LinearLayout.LayoutParams(-1, dp(88)));
            return section;
        }
        if (marketFailed && marketItems.isEmpty()) {
            TextView state = text("社区提示词加载失败，点此重试", 14, Typeface.NORMAL, Theme.ACCENT);
            state.setGravity(Gravity.CENTER);
            state.setOnClickListener(v -> loadMarket());
            section.addView(state, new LinearLayout.LayoutParams(-1, dp(88)));
            return section;
        }
        if (marketItems.isEmpty()) {
            TextView state = text("暂时没有可用的社区提示词", 14, Typeface.NORMAL, Theme.FAINT);
            state.setGravity(Gravity.CENTER);
            section.addView(state, new LinearLayout.LayoutParams(-1, dp(88)));
            return section;
        }

        LinearLayout card = vertical();
        card.setBackground(outlined(Color.WHITE, 12, Theme.BORDER_CHROME, 1, 0, 0));
        card.setClipToOutline(true);
        for (int i = 0; i < marketItems.size(); i++) {
            card.addView(marketRow(marketItems.get(i)));
            if (i < marketItems.size() - 1) card.addView(divider(59));
        }
        section.addView(card, margins(-1, -2, 0, 12, 0, 0));
        return section;
    }

    private View marketFilter(String label, String sort, String scope) {
        boolean selected = marketSort.equals(sort) && marketScope.equals(scope);
        TextView chip = text(label, 13, selected ? Typeface.BOLD : Typeface.NORMAL,
                selected ? Color.WHITE : 0xff5b5349);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(outlined(selected ? 0xff2b2823 : Color.WHITE, 18,
                selected ? Color.TRANSPARENT : 0xffe5dccb, selected ? 0 : 1, 0, 0));
        chip.setOnClickListener(v -> {
            if (selected || marketLoading) return;
            marketSort = sort;
            marketScope = scope;
            marketItems = new ArrayList<>();
            loadMarket();
        });
        return chip;
    }

    private LinearLayout.LayoutParams marketChipParams(boolean spacing) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1);
        if (spacing) params.rightMargin = dp(8);
        return params;
    }

    private View marketRow(PromptStore.MarketItem item) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(9), dp(15), dp(9));
        row.setMinimumHeight(dp(52));
        ImageView icon = marketIconTile(isImageOnly(item.appliesTo));
        addIconWithSpacing(row, icon, 32);

        LinearLayout copy = vertical();
        TextView title = text(item.label, 15, Typeface.NORMAL, Theme.INK);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);
        String author = item.author == null || item.author.isEmpty() ? "匿名" : item.author;
        TextView subline = text(author + " · 导入 " + item.importCount, 13, Typeface.NORMAL, Theme.SECONDARY);
        subline.setMaxLines(1);
        subline.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(subline, margins(-1, -2, 0, 2, 0, 0));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        boolean imported = PromptTree.containsImport(store.items(), item.code);
        boolean importing = importingMarketCodes.contains(item.code);
        if (importing) {
            ProgressBar spinner = new ProgressBar(this);
            spinner.setIndeterminate(true);
            spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Theme.ACCENT));
            LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            spinnerParams.leftMargin = dp(10);
            row.addView(spinner, spinnerParams);
        } else if (imported) {
            LinearLayout importedState = horizontal();
            importedState.setGravity(Gravity.CENTER_VERTICAL);
            importedState.addView(text("已导入", 12, Typeface.NORMAL, Theme.FAINT));
            ImageView chevron = trailingChevron();
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(14), dp(14));
            chevronParams.leftMargin = dp(3);
            importedState.addView(chevron, chevronParams);
            row.addView(importedState, new LinearLayout.LayoutParams(-2, -2));
        } else {
            TextView action = text("导入", 13, Typeface.BOLD, Theme.ACCENT);
            action.setGravity(Gravity.CENTER);
            action.setPadding(dp(13), dp(4), dp(13), dp(4));
            action.setBackground(outlined(Color.WHITE, 16, 0xffebc4b7, 1, 0, 0));
            action.setOnClickListener(v -> importMarketItem(item, null, null));
            row.addView(action, new LinearLayout.LayoutParams(-2, -2));
        }
        row.setOnClickListener(v -> showMarketDetail(item));
        return row;
    }

    private void loadMarket() {
        if (marketLoading) return;
        marketLoading = true;
        marketFailed = false;
        render(store.items());
        String sort = marketSort;
        String scope = marketScope;
        io.execute(() -> {
            List<PromptStore.MarketItem> result = store.market(sort, scope, 30);
            runOnUiThread(() -> {
                if (!sort.equals(marketSort) || !scope.equals(marketScope)) return;
                marketLoading = false;
                marketFailed = result == null;
                marketItems = result == null ? new ArrayList<>() : result;
                render(store.items());
            });
        });
    }

    private void importMarketItem(PromptStore.MarketItem item, Runnable success, Runnable failure) {
        if (importingMarketCodes.contains(item.code)) return;
        importingMarketCodes.add(item.code);
        render(store.items());
        io.execute(() -> {
            String error = store.importCode(item.code);
            runOnUiThread(() -> {
                importingMarketCodes.remove(item.code);
                if (error == null) {
                    toast("已加入提示词");
                    if (success != null) success.run();
                } else {
                    showError(error);
                    if (failure != null) failure.run();
                }
                render(store.items());
            });
        });
    }

    private void showMarketDetail(PromptStore.MarketItem item) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x66000000);

        FrameLayout sheetContainer = new FrameLayout(this);
        sheetContainer.setBackground(topRounded(Theme.BG, 20));
        sheetContainer.setClickable(true);
        sheetContainer.setTranslationY(getResources().getDisplayMetrics().heightPixels);

        LinearLayout sheet = vertical();
        sheetContainer.addView(sheet, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(20), dp(18), dp(20));

        LinearLayout badgeRow = horizontal();
        TextView type = text(marketTypeText(item.appliesTo), 11, Typeface.BOLD,
                isImageOnly(item.appliesTo) ? Theme.ACCENT : Theme.GREEN);
        type.setPadding(dp(7), dp(3), dp(7), dp(3));
        type.setBackground(rounded(isImageOnly(item.appliesTo) ? Theme.ACCENT_SOFT : 0xffeaf1ec, 4));
        badgeRow.addView(type, new LinearLayout.LayoutParams(-2, -2));
        content.addView(badgeRow, margins(-1, -2, 0, 0, 0, 13));

        TextView title = text(item.label, 23, Typeface.BOLD, Theme.INK);
        title.setLineSpacing(dp(2), 1.0f);
        content.addView(title, margins(-1, -2, 0, 0, 0, 15));

        String author = item.author == null || item.author.isEmpty() ? "匿名分享者" : item.author;
        String avatarCharacter = item.author == null || item.author.isEmpty() ? "友" : firstCharacter(item.author);
        LinearLayout authorLine = horizontal();
        authorLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = text(avatarCharacter, 11, Typeface.BOLD, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBackground = rounded(0xffd8a25b, 12);
        avatarBackground.setShape(GradientDrawable.OVAL);
        avatar.setBackground(avatarBackground);
        authorLine.addView(avatar, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView byline = text(author, 13, Typeface.NORMAL, Theme.SECONDARY);
        authorLine.addView(byline, margins(-2, -2, 8, 0, 0, 0));
        content.addView(authorLine, margins(-1, -2, 0, 0, 0, 16));

        LinearLayout stats = horizontal();
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.setPadding(0, dp(12), 0, dp(12));
        stats.setBackground(outlined(Color.WHITE, 5, Theme.BORDER_CHROME, 1, 0, 0));
        stats.addView(statCell(String.valueOf(item.importCount), "被导入"), new LinearLayout.LayoutParams(0, dp(54), 1));
        View statDivider = new View(this);
        statDivider.setBackgroundColor(DIVIDER);
        stats.addView(statDivider, new LinearLayout.LayoutParams(dp(1), dp(30)));
        stats.addView(statCell(appliesText(item.appliesTo), "适用于"), new LinearLayout.LayoutParams(0, dp(54), 1));
        content.addView(stats, margins(-1, -2, 0, 0, 0, 16));

        TextView promptLabel = text("提示词全文", 13, Typeface.BOLD, Theme.SECONDARY);
        content.addView(promptLabel, margins(-1, -2, 0, 0, 0, 8));

        TextView prompt = text("加载中…", 14, Typeface.NORMAL, 0xff5b5349);
        prompt.setLineSpacing(dp(4), 1.0f);
        prompt.setPadding(dp(14), dp(14), dp(14), dp(14));
        prompt.setBackground(outlined(Color.WHITE, 5, Theme.BORDER_CHROME, 1, 0, 0));
        content.addView(prompt, new LinearLayout.LayoutParams(-1, -2));

        if (!item.exampleOutput.isEmpty() || !item.exampleImageKey.isEmpty()) {
            LinearLayout example = vertical();
            example.addView(text("效果示例", 13, Typeface.BOLD, Theme.SECONDARY));
            if (!item.exampleInput.isEmpty()) {
                example.addView(text(item.exampleInput, 13, Typeface.NORMAL, Theme.FAINT),
                        margins(-1, -2, 0, 6, 0, 0));
            }
            if (!item.exampleOutput.isEmpty()) {
                example.addView(text(item.exampleOutput, 14, Typeface.NORMAL, Theme.INK),
                        margins(-1, -2, 0, 6, 0, 0));
            }
            content.addView(example, margins(-1, -2, 0, 18, 0, 0));
        }
        scroll.addView(content);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        boolean imported = PromptTree.containsImport(store.items(), item.code);
        TextView importButton = text(imported ? "已在我的提示词里" : "加入我的提示词", 17,
                Typeface.BOLD, Color.WHITE);
        importButton.setGravity(Gravity.CENTER);
        importButton.setEnabled(!imported);
        importButton.setBackground(rounded(imported ? 0xffc2b9a8 : Theme.ACCENT, 12));
        LinearLayout footer = vertical();
        footer.setPadding(dp(18), dp(10), dp(18), dp(12));
        footer.setBackgroundColor(Theme.BG);
        footer.addView(importButton, new LinearLayout.LayoutParams(-1, dp(54)));
        sheet.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        ImageView closeButton = new ImageView(this);
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeButton.setColorFilter(Theme.SECONDARY);
        closeButton.setPadding(dp(11), dp(11), dp(11), dp(11));
        closeButton.setContentDescription("关闭");
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(40), dp(40),
                Gravity.TOP | Gravity.RIGHT);
        closeParams.topMargin = dp(10);
        closeParams.rightMargin = dp(12);
        sheetContainer.addView(closeButton, closeParams);

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM);
        sheetParams.topMargin = dp(220);
        overlay.addView(sheetContainer, sheetParams);
        dialog.setContentView(overlay, new ViewGroup.LayoutParams(-1, -1));

        final boolean[] closing = {false};
        Runnable dismissSheet = () -> {
            if (closing[0]) return;
            closing[0] = true;
            sheetContainer.animate()
                    .translationY(sheetContainer.getHeight())
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(dialog::dismiss)
                    .start();
        };
        overlay.setOnClickListener(v -> dismissSheet.run());
        closeButton.setOnClickListener(v -> dismissSheet.run());
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_UP) return false;
            dismissSheet.run();
            return true;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            DialogWindowDefaults.applyModal(window, 0x66000000, Theme.BG, true);
            window.setGravity(Gravity.BOTTOM);
        }
        sheetContainer.post(() -> sheetContainer.animate()
                .translationY(0)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start());

        importButton.setOnClickListener(v -> {
            importButton.setEnabled(false);
            importButton.setText("导入中…");
            importMarketItem(item, dismissSheet, () -> {
                importButton.setEnabled(true);
                importButton.setText("加入我的提示词");
            });
        });
        io.execute(() -> {
            PromptStore.Preview preview = store.preview(item.code);
            runOnUiThread(() -> prompt.setText(preview == null || preview.prompt == null || preview.prompt.isEmpty()
                    ? "（全文加载失败）" : preview.prompt));
        });
    }

    private View statCell(String value, String label) {
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        TextView valueView = text(value, 17, Typeface.BOLD, Theme.INK);
        valueView.setGravity(Gravity.CENTER);
        cell.addView(valueView, new LinearLayout.LayoutParams(-1, -2));
        TextView labelView = text(label, 11, Typeface.NORMAL, Theme.FAINT);
        labelView.setGravity(Gravity.CENTER);
        cell.addView(labelView, new LinearLayout.LayoutParams(-1, -2));
        return cell;
    }

    private static boolean isImageOnly(List<String> applies) {
        return applies != null && applies.size() == 1 && applies.contains("image");
    }

    private static String marketTypeText(List<String> applies) {
        return isImageOnly(applies) ? "配图提示词" : "文字提示词";
    }

    private static String firstCharacter(String value) {
        if (value == null || value.isEmpty()) return "友";
        return value.substring(0, value.offsetByCodePoints(0, 1));
    }

    private static String appliesText(List<String> applies) {
        boolean text = applies != null && applies.contains("text");
        boolean image = applies != null && applies.contains("image");
        if (text && image) return "都行";
        if (image) return "图片";
        if (text) return "文字";
        return "全部";
    }

    private View promptRow(PromptListPresentation.Row row) {
        final FrameLayout swipeContainer;
        final TextView deleteAction;
        if (!row.group) {
            swipeContainer = new FrameLayout(this);
            swipeContainer.setBackgroundColor(0xffd94b3d);
            deleteAction = text("删除", 14, Typeface.BOLD, Color.WHITE);
            deleteAction.setGravity(Gravity.CENTER);
            swipeContainer.addView(deleteAction, new FrameLayout.LayoutParams(dp(88), -1, Gravity.RIGHT));
        } else {
            swipeContainer = null;
            deleteAction = null;
        }

        LinearLayout line = horizontal();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(15 + row.depth * 16), dp(9), dp(15), dp(9));
        line.setMinimumHeight(dp(52));
        line.setBackgroundColor(Color.WHITE);

        ImageView icon = iconTile(row.group ? R.drawable.ic_prompt_folder
                : ("仅图片".equals(row.appliesLabel) ? R.drawable.ic_image : R.drawable.ic_doc),
                !row.group && "仅图片".equals(row.appliesLabel));
        addIconWithSpacing(line, icon, 32);

        if (row.group) {
            LinearLayout copy = horizontal();
            copy.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(row.node.label, 15, Typeface.NORMAL, Theme.INK);
            copy.addView(title);
            TextView origin = originBadge(row.node);
            if (origin != null) copy.addView(origin, margins(-2, -2, 6, 0, 0, 0));
            TextView meta = text("分组 · " + row.childCount + " 项", 12, Typeface.NORMAL, Theme.SECONDARY);
            LinearLayout.LayoutParams metaParams = margins(-2, -2, 6, 0, 0, 0);
            copy.addView(meta, metaParams);
            line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            LinearLayout copy = vertical();
            LinearLayout titleLine = horizontal();
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(row.node.label, 15, Typeface.NORMAL, Theme.INK);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView origin = originBadge(row.node);
            if (origin != null) titleLine.addView(origin, margins(-2, -2, 6, 0, 0, 0));
            copy.addView(titleLine, new LinearLayout.LayoutParams(-1, -2));
            if (!row.appliesLabel.isEmpty()) {
                TextView badge = text(row.appliesLabel, 11, Typeface.NORMAL,
                        "仅图片".equals(row.appliesLabel) ? Theme.ACCENT
                                : ("仅文字".equals(row.appliesLabel) ? Theme.GREEN : Theme.SECONDARY));
                badge.setPadding(dp(6), dp(1), dp(6), dp(1));
                badge.setBackground(rounded("仅图片".equals(row.appliesLabel) ? Theme.ACCENT_SOFT
                        : ("仅文字".equals(row.appliesLabel) ? Theme.GREEN_BG : TILE_NEUTRAL), 4));
                LinearLayout badgeWrap = horizontal(); badgeWrap.addView(badge);
                copy.addView(badgeWrap, margins(-1, -2, 0, 2, 0, 0));
            }
            line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        }

        if (row.group && row.expanded) {
            ImageView chevron = trailingChevron();
            chevron.setRotation(90f);
            line.addView(chevron, new LinearLayout.LayoutParams(dp(16), dp(16)));
        } else {
            line.addView(trailingChevron());
        }

        line.setOnClickListener(v -> {
            if (openSwipeRow == v) {
                closeOpenSwipeRow();
                return;
            }
            if (row.group) {
                if (row.expanded) expandedGroups.remove(row.node.id); else expandedGroups.add(row.node.id);
                render(sorting ? drag.draft() : store.items());
            } else if (!sorting) {
                openEditor(row.node.id);
            }
        });
        final float[] touchStart = new float[2];
        final boolean[] swiping = {false};
        final int touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        line.setOnLongClickListener(v -> {
            if (swiping[0]) return true;
            if (savingSort) return true;
            if (!sorting) enterSort(false);
            startPromptDrag(v, row.node.id);
            return true;
        });
        if (!row.group) {
            final float[] startTranslation = new float[1];
            line.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (openSwipeRow != null && openSwipeRow != v) closeOpenSwipeRow();
                    touchStart[0] = event.getRawX();
                    touchStart[1] = event.getRawY();
                    startTranslation[0] = v.getTranslationX();
                    swiping[0] = false;
                } else if (!sorting && event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - touchStart[0];
                    float dy = event.getRawY() - touchStart[1];
                    if (!swiping[0] && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        swiping[0] = true;
                        v.cancelLongPress();
                        v.setPressed(false);
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (swiping[0]) {
                        float translation = startTranslation[0] + dx;
                        v.setTranslationX(Math.max(-dp(88), Math.min(0, translation)));
                        return true;
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (swiping[0]) {
                        boolean revealDelete = !sorting && event.getAction() == MotionEvent.ACTION_UP
                                && v.getTranslationX() <= -dp(44);
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        if (revealDelete) {
                            v.animate().translationX(-dp(88)).setDuration(160).start();
                            openSwipeRow = v;
                        } else {
                            v.animate().translationX(0f).setDuration(160).start();
                            openSwipeRow = null;
                        }
                        swiping[0] = false;
                        return true;
                    }
                }
                return false;
            });
            deleteAction.setOnClickListener(v -> {
                closeOpenSwipeRow();
                confirmDelete(row.node);
            });
        }
        line.setOnDragListener((v, event) -> handleDrag(event, row, v));
        if (swipeContainer == null) return line;
        swipeContainer.addView(line, new FrameLayout.LayoutParams(-1, -2));
        return swipeContainer;
    }

    private void closeOpenSwipeRow() {
        if (openSwipeRow == null) return;
        openSwipeRow.animate().translationX(0f).setDuration(160).start();
        openSwipeRow = null;
    }

    private boolean handleDrag(DragEvent event, PromptListPresentation.Row target, View targetView) {
        Object state = event.getLocalState();
        if (!sorting || !(state instanceof String)) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                targetView.setAlpha(0.58f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                targetView.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP:
                targetView.setAlpha(1f);
                String id = (String) state;
                PromptNode moving = find(drag.draft(), id);
                boolean changed;
                if (target.node.isGroup() && moving != null && !moving.isGroup()) {
                    changed = drag.move(id, target.node.id, target.node.children.size());
                    if (changed) expandedGroups.add(target.node.id);
                } else {
                    String groupId = parentOf(drag.draft(), target.node.id);
                    boolean after = event.getY() >= targetView.getHeight() / 2f;
                    changed = drag.moveOnto(id, groupId, target.node.id, after);
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                targetView.setAlpha(1f);
                postDragRender();
                return true;
            default:
                return true;
        }
    }

    @SuppressWarnings("deprecation")
    private void startPromptDrag(View source, String id) {
        ClipData data = ClipData.newPlainText("prompt-id", id);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(source);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            source.startDragAndDrop(data, shadow, id, 0);
        } else {
            source.startDrag(data, shadow, id, 0);
        }
    }

    private void postDragRender() {
        if (dragRenderPosted) return;
        dragRenderPosted = true;
        content.post(() -> {
            dragRenderPosted = false;
            if (sorting && !savingSort) render(drag.draft());
        });
    }

    private void enterSort(boolean rerender) {
        closeOpenSwipeRow();
        sorting = true;
        drag.begin(store.items());
        intro.setText("长按提示词并拖动排序；拖到分组行可收进该组。");
        showSortSaveAction(false);
        if (rerender) render(drag.draft());
    }

    private void exitSort(boolean save) {
        if (save) {
            saveSort();
            return;
        }
        if (savingSort) return;
        drag.cancel();
        finishSort();
    }

    private void saveSort() {
        if (savingSort) return;
        savingSort = true;
        showSortSaveAction(true);
        List<PromptNode> draft = drag.draft();
        List<String> baseline = drag.baseline();
        io.execute(() -> {
            String error = store.applyReorder(draft, baseline);
            runOnUiThread(() -> {
                savingSort = false;
                if (error != null) {
                    showError(error);
                    showSortSaveAction(false);
                    render(drag.draft());
                } else {
                    finishSort();
                }
            });
        });
    }

    private void finishSort() {
        sorting = false;
        intro.setText(normalIntro());
        showBackAction();
        leftHeaderButton.setEnabled(true);
        leftHeaderButton.setAlpha(1f);
        rightHeaderButton.setText("+"); rightHeaderButton.setTextSize(26); rightHeaderButton.setTextColor(Color.WHITE); rightHeaderButton.setBackground(rounded(Theme.ACCENT, 12)); rightHeaderButton.setOnClickListener(v -> showNewSheet());
        rightHeaderButton.setEnabled(true);
        rightHeaderButton.setAlpha(1f);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rightHeaderButton.getLayoutParams();
        params.width = dp(40);
        rightHeaderButton.setLayoutParams(params);
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
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(16), dp(10), dp(16), dp(12));
        TextView subtitle = text("输入 7 位魔法数字，或粘贴分享链接", 14, Typeface.NORMAL, Theme.FAINT);
        subtitle.setGravity(Gravity.CENTER);
        sheet.addView(subtitle, margins(-1, -2, 0, 2, 0, 16));

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
        IosDialog dialog = IosDialog.showBottomSheet(this, "导入提示词", sheet, 250,
                null, null, null, null, true, false);
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
        codeInput.post(() -> {
            codeInput.requestFocus();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(codeInput, InputMethodManager.SHOW_IMPLICIT);
        });
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
        if (node == null || node.isGroup()) return;
        String message = "删除『" + node.label + "』？此操作不可恢复（可用底部「恢复默认提示词」找回系统项）";
        IosDialog.showConfirmation(this, "删除提示词", message, "删除", () -> {
            Dialog deleteProgress = showDeleteProgress();
            io.execute(() -> {
                    String error = store.remove(node.id);
                    runOnUiThread(() -> {
                        deleteProgress.dismiss();
                        if (error != null) showError(error);
                        render(store.items());
                    });
                });
        }, "取消", null);
    }

    private Dialog showDeleteProgress() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(20), dp(16), dp(20), dp(16));
        box.setBackground(rounded(0xee1f1f1f, 14));
        box.setElevation(dp(8));

        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        box.addView(spinner, spinnerParams);
        TextView status = text("正在删除…", 14, Typeface.BOLD, Color.WHITE);
        status.setGravity(Gravity.CENTER);
        box.addView(status, margins(-2, -2, 0, 10, 0, 0));

        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.16f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.CENTER);
        }
        dialog.show();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        return dialog;
    }

    private void confirmRestore() {
        IosDialog.showAutoHeight(this, "恢复默认提示词", "会补回缺少的系统提示词，不会删除自建内容。", "恢复", () -> io.execute(() -> {
                    String error = store.restoreDefaults();
                    runOnUiThread(() -> { if (error != null) showError(error); render(store.items()); });
                }), "取消", null, false, false);
    }

    private void showError(String message) { errorBanner.setText(message); errorBanner.setVisibility(View.VISIBLE); }
    private void toast(String message) { runOnUiThread(() -> SimpleToast.show(this, message)); }
    private String normalIntro() { return "一套指令，长按文字或图片时按『适用于』自动筛选。改过的系统项标『已自定义』，自己建的标『自建』。"; }
    private TextView originBadge(PromptNode node) {
        if (node == null || node.origin == null || "system".equals(node.origin)) return null;
        boolean custom = "custom".equals(node.origin);
        TextView badge = text(custom ? "已自定义" : "自建", 11, Typeface.BOLD,
                custom ? Theme.AMBER : Theme.GREEN);
        badge.setPadding(dp(6), dp(1), dp(6), dp(1));
        badge.setBackground(rounded(custom ? Theme.AMBER_BG : Theme.GREEN_BG, 4));
        return badge;
    }

    private static PromptNode find(List<PromptNode> items, String id) {
        for (PromptNode node : items) { if (id.equals(node.id)) return node; PromptNode child = find(node.children, id); if (child != null) return child; }
        return null;
    }

    private static String parentOf(List<PromptNode> items, String id) {
        for (PromptNode node : items) for (PromptNode child : node.children) if (id.equals(child.id)) return node.id;
        return null;
    }

    private LinearLayout vertical() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout horizontal() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private View divider(int left) { View view = new View(this); view.setBackgroundColor(DIVIDER); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.leftMargin = dp(left); view.setLayoutParams(p); return view; }
    private TextView text(String value, int sp, int style, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, style); return v; }
    private TextView squareButton(String value, boolean accent) { TextView v = text(value, 22, Typeface.NORMAL, accent ? Color.WHITE : Theme.INK); v.setGravity(Gravity.CENTER); v.setIncludeFontPadding(false); v.setBackground(rounded(accent ? Theme.ACCENT : Color.WHITE, 12)); v.setElevation(dp(2)); return v; }
    private void addIconWithSpacing(LinearLayout parent, ImageView icon, int size) { LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(size), dp(size)); iconLp.rightMargin = dp(12); parent.addView(icon, iconLp); }
    private ImageView iconTile(int iconResId, boolean accent) { ImageView v = new ImageView(this); v.setImageResource(iconResId); v.setColorFilter(accent ? Theme.ACCENT : Theme.SECONDARY); v.setScaleType(ImageView.ScaleType.CENTER); v.setPadding(dp(8), dp(8), dp(8), dp(8)); v.setBackground(rounded(accent ? Theme.ACCENT_SOFT : TILE_NEUTRAL, 10)); return v; }
    private ImageView marketIconTile(boolean imageOnly) { ImageView v = new ImageView(this); v.setImageResource(imageOnly ? R.drawable.ic_image : R.drawable.ic_doc); v.setColorFilter(imageOnly ? Theme.ACCENT : Theme.GREEN); v.setScaleType(ImageView.ScaleType.CENTER); v.setPadding(dp(8), dp(8), dp(8), dp(8)); v.setBackground(rounded(imageOnly ? Theme.ACCENT_SOFT : 0xffeaf1ec, 8)); return v; }
    private GradientDrawable rounded(int color, int radius) { return outlined(color, radius, Color.TRANSPARENT, 0, 0, 0); }
    private GradientDrawable topRounded(int color, int radius) { GradientDrawable d = new GradientDrawable(); float r = dp(radius); d.setColor(color); d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0}); return d; }
    private GradientDrawable outlined(int color, int radius, int stroke, int width, int dash, int gap) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if (width > 0) d.setStroke(dp(width), stroke, dp(dash), dp(gap)); return d; }
    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
