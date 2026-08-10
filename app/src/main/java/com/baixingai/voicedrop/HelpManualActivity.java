package com.baixingai.voicedrop;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.baixingai.voicedrop.core.ManualMarkdown;
import com.baixingai.voicedrop.core.ManualSectionSelection;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class HelpManualActivity extends Activity {
    static final String MANUAL_ASSET = "help_manual.md";
    static final String[] SECTION_LABELS = {
            "1 上手", "2 录音", "3 改稿", "4 发布", "5 社区", "6 文风", "7 账号", "8 FAQ"
    };

    private final ManualSectionSelection sectionSelection = new ManualSectionSelection();
    private final List<View> chapterAnchors = new ArrayList<>();
    private final Runnable releaseSectionTarget = () -> sectionSelection.releaseTappedSection();
    private BouncyScrollView manualScroll;
    private HorizontalScrollView sectionScroll;
    private TextView[] sectionButtons;

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
        page.addView(new PageTitleBar(this, "使用手册", this::finishWithPageTransition),
                new LinearLayout.LayoutParams(-1, -2));
        page.addView(buildSectionBar(), new LinearLayout.LayoutParams(-1, dp(52)));

        manualScroll = new BouncyScrollView(this);
        manualScroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        SystemBarDefaults.applyBottomInsets(content, dp(20), dp(6), dp(20), dp(50));
        manualScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(manualScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderManual(content, ManualMarkdown.parse(readBundledManual()));
        manualScroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> reportVisibleChapter(y));
        setContentView(root);
    }

    private HorizontalScrollView buildSectionBar() {
        sectionScroll = new HorizontalScrollView(this);
        sectionScroll.setHorizontalScrollBarEnabled(false);
        sectionScroll.setFillViewport(false);
        sectionScroll.setBackgroundColor(Theme.BG);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(4), dp(10), dp(8));
        sectionScroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));

        sectionButtons = new TextView[SECTION_LABELS.length];
        for (int i = 0; i < SECTION_LABELS.length; i++) {
            final int index = i;
            TextView button = text(SECTION_LABELS[i], 13, Theme.SECONDARY, Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setMinWidth(dp(64));
            button.setPadding(dp(12), 0, dp(12), 0);
            button.setClickable(true);
            button.setFocusable(true);
            button.setContentDescription("跳到" + SECTION_LABELS[i]);
            button.setOnClickListener(view -> scrollToSection(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
            params.setMargins(0, 0, dp(6), 0);
            row.addView(button, params);
            sectionButtons[i] = button;
        }
        selectSection(0);
        return sectionScroll;
    }

    private void renderManual(LinearLayout content, List<ManualMarkdown.Block> blocks) {
        for (ManualMarkdown.Block block : blocks) {
            switch (block.kind) {
                case TITLE:
                    content.addView(blockText(block.text, 24, Typeface.BOLD, Theme.INK, 4, 4));
                    break;
                case CHAPTER:
                    View chapter = blockText(block.text, 20, Typeface.BOLD, Theme.INK, 22, 4);
                    chapterAnchors.add(chapter);
                    content.addView(chapter);
                    break;
                case SECTION:
                    content.addView(blockText(block.text, 17, Typeface.BOLD, Theme.INK, 12, 2));
                    break;
                case PARAGRAPH:
                    content.addView(inlineText(block.text, 16, Typeface.NORMAL, Theme.INK, 4, 4));
                    break;
                case BULLETS:
                    content.addView(listBlock(block.items, false));
                    break;
                case NUMBERED:
                    content.addView(listBlock(block.items, true));
                    break;
                case TABLE:
                    content.addView(tableBlock(block.items, block.rows));
                    break;
                case CODE:
                    content.addView(codeBlock(block.text));
                    break;
            }
        }
    }

    private View blockText(String value, int size, int style, int color, int top, int bottom) {
        TextView view = text(value, size, color, style);
        view.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(top), 0, dp(bottom));
        view.setLayoutParams(params);
        return view;
    }

    private TextView inlineText(String markdown, int size, int style, int color, int top, int bottom) {
        TextView view = text("", size, color, style);
        view.setText(Html.fromHtml(ManualMarkdown.inlineHtml(markdown), Html.FROM_HTML_MODE_LEGACY));
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(top), 0, dp(bottom));
        view.setLayoutParams(params);
        return view;
    }

    private View listBlock(List<String> items, boolean numbered) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.size(); i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            TextView marker = text(numbered ? (i + 1) + "." : "·", 14, Theme.FAINT, Typeface.BOLD);
            row.addView(marker, new LinearLayout.LayoutParams(dp(28), -2));
            TextView body = inlineText(items.get(i), 16, Typeface.NORMAL, Theme.INK, 0, 0);
            row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, dp(3), 0, dp(3));
            list.addView(row, rowParams);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(4));
        list.setLayoutParams(params);
        return list;
    }

    private View tableBlock(List<String> header, List<List<String>> rows) {
        TableLayout table = new TableLayout(this);
        table.setStretchAllColumns(true);
        table.setBackground(round(Theme.CARD, 12, Theme.BORDER_CHROME));
        table.addView(tableRow(header, true));
        for (List<String> row : rows) table.addView(tableRow(row, false));

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setFillViewport(true);
        horizontal.addView(table, new HorizontalScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(8));
        horizontal.setLayoutParams(params);
        return horizontal;
    }

    private TableRow tableRow(List<String> cells, boolean header) {
        TableRow row = new TableRow(this);
        for (int i = 0; i < cells.size(); i++) {
            TextView cell = inlineText(cells.get(i), 14, header ? Typeface.BOLD : Typeface.NORMAL,
                    Theme.INK, 0, 0);
            cell.setPadding(dp(12), dp(9), dp(12), dp(9));
            row.addView(cell, new TableRow.LayoutParams(i == 0 && cells.size() > 1 ? dp(92) : dp(220), -2));
        }
        return row;
    }

    private View codeBlock(String value) {
        TextView code = text(value, 13, Theme.INK, Typeface.NORMAL);
        code.setTypeface(Typeface.MONOSPACE);
        code.setPadding(dp(12), dp(12), dp(12), dp(12));
        code.setBackground(round(0xfff3ede4, 10, Theme.BORDER_CHROME));
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.addView(code, new HorizontalScrollView.LayoutParams(-2, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(8));
        horizontal.setLayoutParams(params);
        return horizontal;
    }

    private String readBundledManual() {
        try (InputStream input = getAssets().open(MANUAL_ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return "# 使用手册\n\n手册内容暂时无法读取。";
        }
    }

    private void scrollToSection(int index) {
        if (index < 0 || index >= chapterAnchors.size()) return;
        selectSection(sectionSelection.onSectionTapped(index));
        View target = chapterAnchors.get(index);
        manualScroll.removeCallbacks(releaseSectionTarget);
        manualScroll.post(() -> {
            manualScroll.smoothScrollTo(0, Math.max(0, target.getTop() - dp(6)));
            manualScroll.postDelayed(releaseSectionTarget, 700);
        });
    }

    private void reportVisibleChapter(int scrollY) {
        int active = 0;
        int threshold = scrollY + dp(12);
        for (int i = 0; i < chapterAnchors.size(); i++) {
            if (chapterAnchors.get(i).getTop() <= threshold) active = i;
            else break;
        }
        selectSection(sectionSelection.onSectionReported(active));
    }

    private void selectSection(int index) {
        if (sectionButtons == null || index < 0 || index >= sectionButtons.length) return;
        for (int i = 0; i < sectionButtons.length; i++) {
            boolean selected = i == index;
            TextView button = sectionButtons[i];
            button.setTextColor(selected ? Theme.ACCENT : Theme.SECONDARY);
            button.setBackground(chipBackground(selected));
            button.setSelected(selected);
        }
        TextView selected = sectionButtons[index];
        sectionScroll.post(() -> sectionScroll.smoothScrollTo(Math.max(0, selected.getLeft() - dp(16)), 0));
    }

    private GradientDrawable chipBackground(boolean selected) {
        return round(selected ? Theme.ACCENT_SOFT : Theme.CARD, 10,
                selected ? 0xffedc7b8 : Theme.BORDER_CHROME);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override public void onBackPressed() {
        finishWithPageTransition();
    }

    @Override protected void onDestroy() {
        if (manualScroll != null) manualScroll.removeCallbacks(releaseSectionTarget);
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

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
