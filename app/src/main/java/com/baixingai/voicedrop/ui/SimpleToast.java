package com.baixingai.voicedrop.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * Lightweight text-only toast replacement.
 * Standard {@link android.widget.Toast} on Android 11+ always shows the app icon
 * and there is no API to suppress it. This helper uses a {@link PopupWindow}
 * to render a plain text bubble with no icon.
 */
public final class SimpleToast {

    private static PopupWindow current;

    public static void show(Activity activity, String message) {
        if (activity == null || activity.isFinishing()) return;

        // Dismiss any toast that is still visible.
        if (current != null) {
            try { current.dismiss(); } catch (Exception ignored) {}
            current = null;
        }

        float density = activity.getResources().getDisplayMetrics().density;

        TextView tv = new TextView(activity);
        tv.setText(message);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(204, 42, 37, 33));   // #CC2A2521
        bg.setCornerRadius(20 * density);
        tv.setBackground(bg);
        int hPad = (int) (18 * density);
        int vPad = (int) (10 * density);
        tv.setPadding(hPad, vPad, hPad, vPad);

        PopupWindow pw = new PopupWindow(tv,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pw.setClippingEnabled(true);
        current = pw;

        View root = activity.getWindow().getDecorView();
        int yOffset = root.getHeight() / 4;
        pw.showAtLocation(root, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, yOffset);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AlphaAnimation fade = new AlphaAnimation(1f, 0f);
            fade.setDuration(300);
            fade.setFillAfter(true);
            tv.startAnimation(fade);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { pw.dismiss(); } catch (Exception ignored) {}
                if (current == pw) current = null;
            }, 350);
        }, 2000);
    }

    private SimpleToast() {}
}
