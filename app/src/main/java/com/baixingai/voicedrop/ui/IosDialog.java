package com.baixingai.voicedrop.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * iOS-style alert dialog: rounded card, centered, with styled buttons.
 */
public final class IosDialog extends Dialog {

    public IosDialog(Context context) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
    }

    /** Simple message dialog */
    public static void show(Context ctx, String title, String message) {
        show(ctx, title, message, "好", null);
    }

    /** Two-button dialog (positive + cancel) */
    public static void show(Context ctx, String title, String message,
                            String positiveText, Runnable onPositive) {
        show(ctx, title, message, positiveText, onPositive, null, null);
    }

    /** Three-button dialog (positive + neutral + cancel) */
    public static void show(Context ctx, String title, String message,
                            String positiveText, Runnable onPositive,
                            String neutralText, Runnable onNeutral) {
        IosDialog dialog = new IosDialog(ctx);
        dialog.build(ctx, title, message, positiveText, onPositive, neutralText, onNeutral);
        dialog.show();
    }

    /** Dialog with custom view */
    public static void show(Context ctx, String title, View customView,
                            String positiveText, Runnable onPositive) {
        show(ctx, title, customView, positiveText, onPositive, null, null);
    }

    public static void show(Context ctx, String title, View customView,
                            String positiveText, Runnable onPositive,
                            String neutralText, Runnable onNeutral) {
        IosDialog dialog = new IosDialog(ctx);
        dialog.buildWithView(ctx, title, customView, dp(ctx, 200), positiveText, onPositive,
                neutralText, onNeutral, false, false);
        dialog.show();
    }

    public static IosDialog showBottomSheet(Context ctx, String title, View customView,
                                            String positiveText, Runnable onPositive,
                                            String neutralText, Runnable onNeutral) {
        return showBottomSheet(ctx, title, customView, 260, positiveText, onPositive,
                neutralText, onNeutral);
    }

    public static IosDialog showBottomSheet(Context ctx, String title, View customView,
                                            int contentHeightDp,
                                            String positiveText, Runnable onPositive,
                                            String neutralText, Runnable onNeutral) {
        return showBottomSheet(ctx, title, customView, contentHeightDp, positiveText, onPositive,
                neutralText, onNeutral, false);
    }

    public static IosDialog showBottomSheet(Context ctx, String title, View customView,
                                            int contentHeightDp,
                                            String positiveText, Runnable onPositive,
                                            String neutralText, Runnable onNeutral,
                                            boolean showCloseButton) {
        IosDialog dialog = new IosDialog(ctx);
        dialog.buildWithView(ctx, title, customView, dp(ctx, contentHeightDp), positiveText, onPositive,
                neutralText, onNeutral, showCloseButton, true);
        dialog.show();
        return dialog;
    }

    public static void show(Context ctx, String title, View customView, int contentHeightDp,
                            String positiveText, Runnable onPositive) {
        show(ctx, title, customView, contentHeightDp, positiveText, onPositive, false);
    }

    public static void show(Context ctx, String title, View customView, int contentHeightDp,
                            String positiveText, Runnable onPositive, boolean showCloseButton) {
        IosDialog dialog = new IosDialog(ctx);
        dialog.buildWithView(ctx, title, customView, dp(ctx, contentHeightDp),
                positiveText, onPositive, null, null, showCloseButton, false);
        dialog.show();
    }

    private void build(Context ctx, String title, String message,
                       String positiveText, Runnable onPositive,
                       String neutralText, Runnable onNeutral) {
        TextView messageView = new TextView(ctx);
        messageView.setText(message);
        messageView.setTextSize(15);
        messageView.setTextColor(Theme.INK);
        messageView.setLineSpacing(dp(ctx, 4), 1.0f);
        messageView.setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 18));
        buildWithView(ctx, title, messageView, dp(ctx, 200), positiveText, onPositive,
                neutralText, onNeutral, false, false);
    }

    private void buildWithView(Context ctx, String title, View contentView,
                               int contentHeight,
                               String positiveText, Runnable onPositive,
                               String neutralText, Runnable onNeutral,
                               boolean showCloseButton,
                               boolean bottomSheet) {
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                        | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            }
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            }
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.dimAmount = 0f;
            window.setAttributes(lp);
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(bottomSheet ? Gravity.BOTTOM : Gravity.CENTER);
        if (bottomSheet) {
            root.setPadding(0, dp(ctx, 40), 0, 0);
        } else {
            root.setPadding(dp(ctx, 24), dp(ctx, 40), dp(ctx, 24), dp(ctx, 40));
        }
        root.setBackgroundColor(0x66000000);
        root.setOnClickListener(v -> dismiss());

        // Card
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bottomSheet ? bottomSheetCard(ctx) : roundCard(ctx));
        card.setPadding(0, 0, 0, bottomSheet ? dp(ctx, 20) + navigationBarHeight(ctx) : 0);
        card.setClickable(true);
        if (bottomSheet) {
            card.setTranslationY(ctx.getResources().getDisplayMetrics().heightPixels);
        }

        // Title
        if (title != null && !title.isEmpty()) {
            FrameLayout header = new FrameLayout(ctx);
            TextView titleView = new TextView(ctx);
            titleView.setText(title);
            titleView.setTextSize(18);
            titleView.setTextColor(Theme.INK);
            titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(dp(ctx, 48), dp(ctx, 22), dp(ctx, 48), dp(ctx, 10));
            header.addView(titleView, new FrameLayout.LayoutParams(-1, -2));
            if (showCloseButton) {
                TextView close = new TextView(ctx);
                close.setText("×");
                close.setTextSize(28);
                close.setTextColor(Theme.SECONDARY);
                close.setGravity(Gravity.CENTER);
                close.setContentDescription("关闭");
                close.setOnClickListener(v -> dismiss());
                FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44),
                        Gravity.RIGHT | Gravity.TOP);
                closeLp.topMargin = dp(ctx, 8);
                closeLp.rightMargin = dp(ctx, 8);
                header.addView(close, closeLp);
            }
            card.addView(header);
        }

        if (title != null && !title.isEmpty()) {
            View divider = new View(ctx);
            divider.setBackgroundColor(0x1a000000);
            card.addView(divider, new LinearLayout.LayoutParams(-1, dp(ctx, 1)));
        }

        // Content (scrollable if tall)
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(contentView);
        int resolvedContentHeight = bottomSheet
                ? Math.min(contentHeight, ctx.getResources().getDisplayMetrics().heightPixels - dp(ctx, 96))
                : contentHeight;
        card.addView(scroll, new LinearLayout.LayoutParams(-1, resolvedContentHeight));

        boolean hasButtons = (neutralText != null && !neutralText.isEmpty()) || positiveText != null;
        LinearLayout btnRow = null;
        if (hasButtons) {
            View btnDivider = new View(ctx);
            btnDivider.setBackgroundColor(0x1a000000);
            card.addView(btnDivider, new LinearLayout.LayoutParams(-1, dp(ctx, 1)));

            btnRow = new LinearLayout(ctx);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, 0, 0, 0);
        }

        if (neutralText != null && !neutralText.isEmpty()) {
            // Neutral button (left)
            btnRow.addView(makeButton(ctx, neutralText, Theme.SECONDARY, v -> {
                dismiss();
                if (onNeutral != null) onNeutral.run();
            }), new LinearLayout.LayoutParams(0, dp(ctx, 50), 1));
            // Divider
            View midDiv = new View(ctx);
            midDiv.setBackgroundColor(0x1a000000);
            btnRow.addView(midDiv, new LinearLayout.LayoutParams(dp(ctx, 1), -1));
            // Cancel button (middle)
            btnRow.addView(makeButton(ctx, "取消", Theme.RED, v -> dismiss()),
                    new LinearLayout.LayoutParams(0, dp(ctx, 50), 1));
            // Divider
            View midDiv2 = new View(ctx);
            midDiv2.setBackgroundColor(0x1a000000);
            btnRow.addView(midDiv2, new LinearLayout.LayoutParams(dp(ctx, 1), -1));
            // Positive button (right)
            btnRow.addView(makeButton(ctx, positiveText, Theme.RED, v -> {
                dismiss();
                if (onPositive != null) onPositive.run();
            }), new LinearLayout.LayoutParams(0, dp(ctx, 50), 1));
        } else if (positiveText != null) {
            // Single positive button
            btnRow.addView(makeButton(ctx, positiveText, Theme.RED, v -> {
                dismiss();
                if (onPositive != null) onPositive.run();
            }), new LinearLayout.LayoutParams(-1, dp(ctx, 50)));
        }

        if (btnRow != null) card.addView(btnRow);
        int cardWidth = bottomSheet
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.82);
        root.addView(card, new LinearLayout.LayoutParams(cardWidth, -2));
        setContentView(root);

        if (bottomSheet) {
            card.post(() -> {
                card.animate()
                        .translationY(0)
                        .setDuration(240)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            });
        }
    }

    private static TextView makeButton(Context ctx, String label, int color, View.OnClickListener onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(label);
        btn.setTextSize(17);
        btn.setTextColor(color);
        btn.setGravity(Gravity.CENTER);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btn.setOnClickListener(onClick);
        return btn;
    }

    private static GradientDrawable roundCard(Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Theme.CARD);
        d.setCornerRadius(dp(ctx, 16));
        return d;
    }

    private static GradientDrawable bottomSheetCard(Context ctx) {
        GradientDrawable d = new GradientDrawable();
        float radius = dp(ctx, 18);
        d.setColor(Theme.CARD);
        d.setCornerRadii(new float[] {
                radius, radius,
                radius, radius,
                0, 0,
                0, 0
        });
        return d;
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private static int navigationBarHeight(Context ctx) {
        int id = ctx.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? ctx.getResources().getDimensionPixelSize(id) : 0;
    }
}
