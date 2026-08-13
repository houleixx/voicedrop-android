package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Shared in-app page title bar with a platform-sized back target. */
public final class PageTitleBar extends FrameLayout {
    public PageTitleBar(Context context, String title, Runnable onBack) {
        super(context);
        SystemBarDefaults.applyTopInsets(this, dp(12), dp(8), dp(16), dp(8));

        FrameLayout backTouch = new FrameLayout(context);
        backTouch.setClickable(true);
        backTouch.setFocusable(true);
        backTouch.setContentDescription("返回");

        FrameLayout back = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.CARD);
        background.setCornerRadius(dp(11));
        background.setStroke(dp(1), Theme.BORDER_CHROME);
        back.setBackground(background);
        back.setElevation(dp(2));

        ImageView icon = new ImageView(context);
        AliIconFont.apply(icon, AliIconFont.BACK, Theme.INK);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        back.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        backTouch.addView(back, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        backTouch.setOnClickListener(view -> onBack.run());
        addView(backTouch, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL));

        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(24);
        heading.setTextColor(Theme.INK);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams headingParams = new FrameLayout.LayoutParams(
                -1, dp(48), Gravity.CENTER);
        headingParams.leftMargin = dp(64);
        headingParams.rightMargin = dp(64);
        addView(heading, headingParams);
    }

    /** Adds a trailing text action while retaining the shared title and back affordance. */
    public TextView addTextAction(String label, Runnable action) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setTextSize(16);
        view.setTextColor(Theme.ACCENT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(64));
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription(label);
        view.setOnClickListener(ignored -> action.run());
        addView(view, new FrameLayout.LayoutParams(
                -2, dp(48), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        return view;
    }

    /** Adds a trailing card-style icon action matching the article detail toolbar. */
    public FrameLayout addIconAction(int iconResId, int iconColor, String description,
                                     Runnable action) {
        FrameLayout touch = new FrameLayout(getContext());
        touch.setClickable(true);
        touch.setFocusable(true);
        touch.setContentDescription(description);

        FrameLayout button = new FrameLayout(getContext());
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.CARD);
        background.setCornerRadius(dp(11));
        background.setStroke(dp(1), Theme.BORDER_CHROME);
        button.setBackground(background);
        button.setElevation(dp(2));

        ImageView icon = new ImageView(getContext());
        AliIconFont.apply(icon, iconResId, iconColor);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        button.addView(icon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        touch.addView(button, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        touch.setOnClickListener(ignored -> action.run());
        addView(touch, new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        return touch;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
