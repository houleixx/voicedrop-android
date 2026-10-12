package com.baixingai.voicedrop.ui;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.baixingai.voicedrop.core.ReadingFontScale;
import com.baixingai.voicedrop.data.Prefs;

public final class ReadingFont {
    private ReadingFont() {}
    public static float scale(Context context) { return new Prefs(context).readingFontScale(); }
    public static String label(Context context) {
        return I18n.text(context, ReadingFontScale.label(ReadingFontScale.index(scale(context))));
    }
    public static void show(Activity activity, Runnable onChanged) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int selected = ReadingFontScale.index(scale(activity));
        IosDialog[] sheet = new IosDialog[1];
        for (int i = 0; i < ReadingFontScale.count(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(activity, 24), 0, dp(activity, 24), 0);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setSelected(i == selected);
            TextView label = new TextView(activity);
            label.setText(I18n.text(activity, ReadingFontScale.label(i)));
            label.setTextSize(17);
            label.setTextColor(Theme.INK);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            RemixIconView check = new RemixIconView(activity);
            check.setIcon(RemixIconGlyph.CHECK);
            check.setTextSize(22);
            check.setTextColor(Theme.ACCENT);
            check.setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(check, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)));
            row.setOnClickListener(v -> sheet[0].dismissAnimated(() -> {
                new Prefs(activity).setReadingFontScale(ReadingFontScale.value(index));
                if (onChanged != null) onChanged.run();
            }));
            content.addView(row, new LinearLayout.LayoutParams(-1, dp(activity, 56)));
            if (i < ReadingFontScale.count() - 1) {
                View divider = new View(activity);
                divider.setBackgroundColor(Theme.BORDER_CHROME);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(activity, 1));
                dividerParams.setMargins(dp(activity, 24), 0, dp(activity, 24), 0);
                content.addView(divider, dividerParams);
            }
        }
        sheet[0] = IosDialog.showBottomSheet(activity, "阅读字号", content,
                ReadingFontScale.count() * 56 + ReadingFontScale.count() - 1,
                null, null, null, null, true);
        sheet[0].setTitleDividerInset(24);
        DialogWindowDefaults.hideNavigationBar(sheet[0].getWindow());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
