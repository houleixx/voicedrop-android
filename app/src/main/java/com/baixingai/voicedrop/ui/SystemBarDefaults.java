package com.baixingai.voicedrop.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public final class SystemBarDefaults {
    private SystemBarDefaults() {}

    /** Adds the live status-bar and display-cutout safe area to a top-aligned view. */
    public static void applyTopInsets(View view, int baseLeft, int baseTop, int baseRight, int baseBottom) {
        applyInsets(view, baseLeft, baseTop, baseRight, baseBottom, true, false);
    }

    /** Keeps controls below the status bar/cutout even while immersive mode hides the bars. */
    public static void applyTopInsetsIgnoringVisibility(View view, int baseLeft, int baseTop,
                                                        int baseRight, int baseBottom) {
        if (view == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets safe = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            target.setPadding(
                    baseLeft + safe.left,
                    baseTop + safe.top,
                    baseRight + safe.right,
                    baseBottom);
            return windowInsets;
        });
        requestInsetsWhenAttached(view);
    }

    /** Adds the live navigation-bar and display-cutout safe area to bottom-aligned content. */
    public static void applyBottomInsets(View view, int baseLeft, int baseTop, int baseRight, int baseBottom) {
        applyInsets(view, baseLeft, baseTop, baseRight, baseBottom, false, true);
    }

    /** Adds both top and bottom system-bar safe areas to a full-height content view. */
    public static void applyTopAndBottomInsets(View view, int baseLeft, int baseTop,
                                               int baseRight, int baseBottom) {
        applyInsets(view, baseLeft, baseTop, baseRight, baseBottom, true, true);
    }

    /**
     * Keeps scroll content clear of a variable-height fixed bottom view while the viewport itself
     * remains edge-to-edge. The fixed view is expected to consume its own navigation-bar inset.
     */
    public static void applyScrollableBottomInsetsAbove(View content, View fixedBottom,
                                                         int baseLeft, int baseTop,
                                                         int baseRight, int baseBottom) {
        if (content == null || fixedBottom == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(content, (target, windowInsets) -> {
            Insets safe = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.displayCutout());
            int obstructionHeight = Math.max(fixedBottom.getHeight(), safe.bottom);
            target.setPadding(
                    baseLeft + safe.left,
                    baseTop,
                    baseRight + safe.right,
                    baseBottom + obstructionHeight);
            return windowInsets;
        });
        fixedBottom.addOnLayoutChangeListener((view, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top != oldBottom - oldTop) requestInsetsWhenAttached(content);
        });
        requestInsetsWhenAttached(content);
    }

    private static void applyInsets(View view, int baseLeft, int baseTop, int baseRight, int baseBottom,
                                    boolean includeTop, boolean includeBottom) {
        if (view == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets topSafe = includeTop
                    ? windowInsets.getInsets(
                            WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout())
                    : Insets.NONE;
            Insets bottomSafe = includeBottom
                    ? windowInsets.getInsetsIgnoringVisibility(
                            WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.displayCutout())
                    : Insets.NONE;
            target.setPadding(
                    baseLeft + Math.max(topSafe.left, bottomSafe.left),
                    baseTop + topSafe.top,
                    baseRight + Math.max(topSafe.right, bottomSafe.right),
                    baseBottom + bottomSafe.bottom);
            return windowInsets;
        });
        requestInsetsWhenAttached(view);
    }

    private static void requestInsetsWhenAttached(View view) {
        if (ViewCompat.isAttachedToWindow(view)) {
            ViewCompat.requestApplyInsets(view);
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View attachedView) {
                    attachedView.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(attachedView);
                }

                @Override
                public void onViewDetachedFromWindow(View detachedView) {}
            });
        }
    }

    public static void applyLightActivity(Window window, int navigationBarColor, boolean edgeToEdge) {
        if (window == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(!edgeToEdge);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            // Immersive activities draw their own background beneath the transient gesture handle.
            // Non-edge-to-edge windows keep the caller-provided navigation bar color.
            window.setNavigationBarColor(edgeToEdge ? Color.TRANSPARENT : navigationBarColor);
        }
        disableContrast(window);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (edgeToEdge) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        flags |= lightSystemBarFlags(true);
        window.getDecorView().setSystemUiVisibility(flags);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        if (edgeToEdge) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.navigationBars());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
        window.setBackgroundDrawableResource(android.R.color.transparent);
        relayoutSystemBars(window);
    }

    /** Keeps content edge-to-edge while showing light-background system bars. */
    public static void applyLightEdgeToEdgeVisible(Window window) {
        if (window == null) return;
        WindowCompat.setDecorFitsSystemWindows(window, false);
        allowDisplayCutout(window);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        disableContrast(window);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | lightSystemBarFlags(true);
        window.getDecorView().setSystemUiVisibility(flags);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        controller.show(WindowInsetsCompat.Type.systemBars());
        relayoutSystemBars(window);
    }

    /** Makes the in-app camera preview fully immersive across status and navigation bars. */
    public static void applyCameraImmersive(Window window) {
        if (window == null) return;
        WindowCompat.setDecorFitsSystemWindows(window, false);
        allowDisplayCutout(window);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        disableContrast(window);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        window.getDecorView().setSystemUiVisibility(flags);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        relayoutSystemBars(window);
    }

    private static void allowDisplayCutout(Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attributes);
    }

    private static void relayoutSystemBars(Window window) {
        ViewCompat.requestApplyInsets(window.getDecorView());
        window.getDecorView().requestLayout();
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
