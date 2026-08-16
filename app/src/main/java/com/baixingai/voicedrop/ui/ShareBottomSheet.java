package com.baixingai.voicedrop.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Shared four-column share target sheet used by detail pages. */
public final class ShareBottomSheet {
    public static final int WECHAT_GREEN = 0xff07c160;
    public static final int XIAOHONGSHU_RED = 0xffff2442;
    public static final int NEUTRAL_BACKGROUND = 0xfff3f1ed;

    private static final int COLUMN_COUNT = 4;
    private static final int CIRCLE_SIZE_DP = 56;
    private static final int DEFAULT_DRAWABLE_SIZE_DP = 26;
    private static final int DEFAULT_GLYPH_SIZE_DP = 27;
    private static final int TARGET_HEIGHT_DP = 92;
    private static final int FIRST_ROW_EXTRA_BOTTOM_DP = 11;
    private static final int ROW_SEPARATOR_HEIGHT_DP = 17;

    private ShareBottomSheet() {}

    public static Item drawable(String label, int iconResId, int backgroundColor,
                                int iconColor, Runnable action) {
        return drawable(label, iconResId, backgroundColor, iconColor,
                DEFAULT_DRAWABLE_SIZE_DP, action);
    }

    /** Allows brand wordmarks and small utility glyphs to be optically balanced. */
    public static Item drawable(String label, int iconResId, int backgroundColor,
                                int iconColor, int iconSizeDp, Runnable action) {
        return new Item(label, iconResId, null, backgroundColor, iconColor, iconSizeDp, action);
    }

    public static Item remix(String label, String glyph, int backgroundColor,
                             int iconColor, Runnable action) {
        return new Item(label, 0, glyph, backgroundColor, iconColor,
                DEFAULT_GLYPH_SIZE_DP, action);
    }

    public static void show(Activity activity, List<Item> sourceItems) {
        if (activity == null || activity.isFinishing() || sourceItems == null || sourceItems.isEmpty()) return;
        List<Item> items = new ArrayList<>(sourceItems);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(COLUMN_COUNT);
        int rows = (items.size() + COLUMN_COUNT - 1) / COLUMN_COUNT;
        grid.setRowCount(rows * 2 - 1);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        grid.setPadding(dp(activity, 16), dp(activity, 4), dp(activity, 16), dp(activity, 4));
        content.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        final IosDialog[] dialogRef = {null};
        for (int index = 0; index < items.size(); index++) {
            if (index > 0 && index % COLUMN_COUNT == 0) {
                FrameLayout dividerSlot = new FrameLayout(activity);
                View divider = new View(activity);
                divider.setBackgroundColor(0x14000000);
                dividerSlot.addView(divider, new FrameLayout.LayoutParams(-1, dp(activity, 1), Gravity.TOP));
                GridLayout.LayoutParams dividerParams = new GridLayout.LayoutParams(
                        GridLayout.spec((index / COLUMN_COUNT) * 2 - 1),
                        GridLayout.spec(0, COLUMN_COUNT, 1f));
                dividerParams.width = 0;
                dividerParams.height = dp(activity, ROW_SEPARATOR_HEIGHT_DP);
                dividerParams.leftMargin = dp(activity, 10);
                dividerParams.rightMargin = dp(activity, 10);
                grid.addView(dividerSlot, dividerParams);
            }
            Item item = items.get(index);
            View target = targetView(activity, item, () -> {
                if (dialogRef[0] != null) dialogRef[0].dismissAnimated(item.action);
                else item.action.run();
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec((index / COLUMN_COUNT) * 2),
                    GridLayout.spec(index % COLUMN_COUNT, 1f));
            params.width = 0;
            int extraBottom = rows > 1 && index < COLUMN_COUNT
                    ? FIRST_ROW_EXTRA_BOTTOM_DP : 0;
            params.height = dp(activity, TARGET_HEIGHT_DP + extraBottom);
            grid.addView(target, params);
        }

        int contentHeight = 8 + rows * TARGET_HEIGHT_DP
                + (rows > 1 ? FIRST_ROW_EXTRA_BOTTOM_DP : 0)
                + Math.max(0, rows - 1) * ROW_SEPARATOR_HEIGHT_DP;
        dialogRef[0] = IosDialog.showBottomSheet(activity, null, content,
                contentHeight, null, null, null, null);
    }

    private static View targetView(Activity activity, Item item, Runnable action) {
        LinearLayout target = new LinearLayout(activity);
        target.setOrientation(LinearLayout.VERTICAL);
        target.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        target.setClickable(true);
        target.setFocusable(true);
        target.setContentDescription(item.label);
        target.setPadding(dp(activity, 3), 0, dp(activity, 3), 0);
        TypedValue ripple = new TypedValue();
        if (activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            target.setBackgroundResource(ripple.resourceId);
        }

        FrameLayout iconCircle = new FrameLayout(activity);
        iconCircle.setBackground(circle(item.backgroundColor));
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(
                dp(activity, CIRCLE_SIZE_DP), dp(activity, CIRCLE_SIZE_DP));
        target.addView(iconCircle, circleParams);

        if (item.glyph != null) {
            RemixIconView icon = new RemixIconView(activity);
            icon.setIcon(item.glyph);
            icon.setTextSize(item.iconSizeDp);
            icon.setTextColor(item.iconColor);
            iconCircle.addView(icon, new FrameLayout.LayoutParams(
                    dp(activity, item.iconSizeDp + 6), dp(activity, item.iconSizeDp + 6), Gravity.CENTER));
        } else {
            ImageView icon = new ImageView(activity);
            icon.setImageResource(item.iconResId);
            icon.setColorFilter(item.iconColor);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconCircle.addView(icon, new FrameLayout.LayoutParams(
                    dp(activity, item.iconSizeDp), dp(activity, item.iconSizeDp), Gravity.CENTER));
        }

        TextView label = new TextView(activity);
        label.setText(item.label);
        label.setTextSize(14);
        label.setTextColor(Theme.SECONDARY);
        label.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(activity, 24));
        labelParams.topMargin = dp(activity, 7);
        target.addView(label, labelParams);
        target.setOnClickListener(ignored -> action.run());
        return target;
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public static final class Item {
        final String label;
        final int iconResId;
        final String glyph;
        final int backgroundColor;
        final int iconColor;
        final int iconSizeDp;
        final Runnable action;

        private Item(String label, int iconResId, String glyph, int backgroundColor,
                     int iconColor, int iconSizeDp, Runnable action) {
            this.label = label;
            this.iconResId = iconResId;
            this.glyph = glyph;
            this.backgroundColor = backgroundColor;
            this.iconColor = iconColor;
            this.iconSizeDp = iconSizeDp;
            this.action = action;
        }
    }
}
