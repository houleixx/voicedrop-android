package com.baixingai.voicedrop.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Compact WeChat-style loading overlay for the handoff to WeChat. */
public final class WechatShareLoadingDialog extends Dialog {
    private WechatShareLoadingDialog(Context context) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        setContentView(content(context));
    }

    public static WechatShareLoadingDialog show(Context context) {
        WechatShareLoadingDialog dialog = new WechatShareLoadingDialog(context);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(dp(context, 120), dp(context, 120));
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0f;
            window.setAttributes(params);
        }
        return dialog;
    }

    private static LinearLayout content(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setMinimumWidth(dp(context, 120));
        card.setMinimumHeight(dp(context, 120));
        card.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xdd202020);
        background.setCornerRadius(dp(context, 14));
        card.setBackground(background);

        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        card.addView(spinner, new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)));

        TextView message = new TextView(context);
        message.setText("加载中...");
        message.setTextColor(Color.WHITE);
        message.setTextSize(14);
        message.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        message.setIncludeFontPadding(false);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.topMargin = dp(context, 8);
        card.addView(message, textParams);
        return card;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
