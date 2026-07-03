package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class LoadingStateView extends LinearLayout {
    private final TextView messageView;

    public LoadingStateView(Context context) {
        this(context, "正在加载...");
    }

    public LoadingStateView(Context context, String message) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);

        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        tintSpinner(spinner);
        addView(spinner, new LinearLayout.LayoutParams(dp(40), dp(40)));

        messageView = new TextView(context);
        messageView.setTextSize(16);
        messageView.setTextColor(Theme.SECONDARY);
        messageView.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(0, dp(14), 0, 0);
        addView(messageView);

        setMessage(message);
    }

    public void setMessage(String message) {
        messageView.setText(message == null || message.isEmpty() ? "正在加载..." : message);
    }

    private void tintSpinner(ProgressBar spinner) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Theme.RED));
        } else {
            spinner.getIndeterminateDrawable().setColorFilter(Theme.RED,
                    android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
