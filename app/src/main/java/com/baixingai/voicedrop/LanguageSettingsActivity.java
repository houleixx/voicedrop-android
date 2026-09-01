package com.baixingai.voicedrop;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;

import com.baixingai.voicedrop.data.AppLanguage;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.RemixIconGlyph;
import com.baixingai.voicedrop.ui.RemixIconView;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

/** Lets people review a language choice before applying it to the whole app. */
public final class LanguageSettingsActivity extends VoiceDropActivity {
    private AppLanguage current;
    private AppLanguage selected;
    private LinearLayout choices;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = AppLanguage.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags());
        selected = current;
        configureEdgeToEdge();

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        PageTitleBar titleBar = new PageTitleBar(this, "语言", this::finishWithPageTransition);
        TextView confirm = titleBar.addTextAction("确定", this::confirmLanguage);
        confirm.setTextColor(Theme.CARD);
        confirm.setMinWidth(0);
        confirm.setPadding(dp(8), 0, dp(8), 0);
        confirm.setBackground(solidActionBackground());
        FrameLayout.LayoutParams confirmParams = (FrameLayout.LayoutParams) confirm.getLayoutParams();
        confirmParams.width = dp(72);
        confirmParams.height = dp(40);
        confirmParams.rightMargin = dp(4);
        confirm.setLayoutParams(confirmParams);
        page.addView(titleBar, new LinearLayout.LayoutParams(-1, -2));

        BouncyScrollView scroll = new BouncyScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        SystemBarDefaults.applyBottomInsets(content, dp(16), dp(6), dp(16), dp(40));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = text("选择 VoiceDrop 的显示语言。选择后点击右上角确定生效。", 13,
                Theme.SECONDARY, Typeface.NORMAL);
        hint.setLineSpacing(0, 1.18f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.setMargins(dp(4), 0, dp(4), dp(12));
        content.addView(hint, hintParams);

        choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setBackground(cardBackground());
        content.addView(choices, new LinearLayout.LayoutParams(-1, -2));
        renderChoices();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override public void onBackPressed() {
        finishWithPageTransition();
    }

    private void renderChoices() {
        choices.removeAllViews();
        AppLanguage[] languages = AppLanguage.values();
        for (int index = 0; index < languages.length; index++) {
            AppLanguage language = languages[index];
            choices.addView(languageRow(language));
            if (index < languages.length - 1) choices.addView(divider());
        }
    }

    private View languageRow(AppLanguage language) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(language.label());
        row.setOnClickListener(view -> {
            selected = language;
            renderChoices();
        });

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        copy.addView(text(language.label(), 16, Theme.INK, Typeface.BOLD));
        String description = language == AppLanguage.SYSTEM ? "使用设备的系统语言" : language.languageTags();
        TextView subtitle = text(description, 12, Theme.SECONDARY, Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, 0);
        copy.addView(subtitle);

        RemixIconView indicator = new RemixIconView(this);
        indicator.setIcon(selected == language ? RemixIconGlyph.CHECK : "");
        indicator.setTextSize(16);
        indicator.setTextColor(Theme.CARD);
        indicator.setGravity(Gravity.CENTER);
        GradientDrawable indicatorBackground = new GradientDrawable();
        indicatorBackground.setShape(GradientDrawable.OVAL);
        indicatorBackground.setColor(selected == language ? Theme.ACCENT : Theme.CARD);
        indicatorBackground.setStroke(dp(1), selected == language ? Theme.ACCENT : Theme.BORDER_CHROME);
        indicator.setBackground(indicatorBackground);
        row.addView(indicator, new LinearLayout.LayoutParams(dp(22), dp(22)));
        return row;
    }

    private void confirmLanguage() {
        if (selected == current) return;
        // Finish this picker first so the locale recreation returns to Settings
        // instead of recreating both screens at once.
        finishWithPageTransition();
        com.baixingai.voicedrop.ui.I18n.applyLanguage(this, selected.languageTags());
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Theme.BORDER_CHROME);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(dp(16), 0, dp(16), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.CARD);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Theme.BORDER_CHROME);
        return background;
    }

    private GradientDrawable solidActionBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.ACCENT);
        background.setCornerRadius(dp(10));
        return background;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(com.baixingai.voicedrop.ui.I18n.text(this, value));
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void configureEdgeToEdge() {
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
