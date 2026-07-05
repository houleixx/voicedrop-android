package com.baixingai.voicedrop.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

public final class SystemBarDefaults {
    private SystemBarDefaults() {}

    public static void applyLightActivity(Window window, int navigationBarColor, boolean edgeToEdge) {
        if (window == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(!edgeToEdge);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(navigationBarColor);
        }
        disableContrast(window);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (edgeToEdge) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        flags |= lightSystemBarFlags(true);
        window.getDecorView().setSystemUiVisibility(flags);
        window.setBackgroundDrawableResource(android.R.color.transparent);
    }

    public static void applyModal(Window window, int statusBarColor, int navigationBarColor, boolean lightNavigationBar) {
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(statusBarColor);
            window.setNavigationBarColor(navigationBarColor);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true);
        }
        disableContrast(window);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | lightSystemBarFlags(lightNavigationBar);
        window.getDecorView().setSystemUiVisibility(flags);
        applyFullscreen(window);
    }

    public static void applyFullscreen(Window window) {
        if (window == null) return;
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.dimAmount = 0f;
        window.setAttributes(lp);
    }

    private static int lightSystemBarFlags(boolean lightNavigationBar) {
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (lightNavigationBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        return flags;
    }

    private static void disableContrast(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }
}
