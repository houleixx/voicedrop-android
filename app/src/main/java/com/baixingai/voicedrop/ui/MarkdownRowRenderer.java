package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.core.MarkdownBlock;

/** Applies block Markdown styling while keeping the original source available to editors. */
public final class MarkdownRowRenderer {
    private MarkdownRowRenderer() {}

    public static TextView add(Context context, FrameLayout row, MarkdownBlock block) {
        TextView text = new TextView(context);
        text.setTextColor(0xff5d574f);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        text.setLineSpacing(dp(context, 6), 1f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2);
        switch (block.kind) {
            case H1:
                heading(text, block.content, 22, Typeface.BOLD);
                break;
            case H2:
                heading(text, block.content, 19, Typeface.BOLD);
                break;
            case H3:
                heading(text, block.content, 17, Typeface.BOLD);
                break;
            case BULLET:
                text.setText(prefixed("•  ", block.content));
                break;
            case ORDERED:
                text.setText(prefixed(block.marker + ".  ", block.content));
                break;
            case QUOTE:
                text.setText(block.content);
                text.setTextColor(Theme.SECONDARY);
                params.leftMargin = dp(context, 13);
                View bar = new View(context);
                bar.setBackgroundColor(0x73d8593b);
                row.addView(bar, new FrameLayout.LayoutParams(dp(context, 3), -1));
                break;
            case DIVIDER:
                text.setText("");
                text.setBackgroundColor(Theme.BORDER_CHROME);
                params.height = dp(context, 1);
                params.topMargin = dp(context, 8);
                params.bottomMargin = dp(context, 8);
                break;
            case PLAIN:
            default:
                text.setText(block.content);
                break;
        }
        row.addView(text, params);
        return text;
    }

    private static void heading(TextView view, String content, int sp, int style) {
        view.setText(content);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(Theme.INK);
        view.setTypeface(Typeface.DEFAULT, style);
    }

    private static CharSequence prefixed(String prefix, String content) {
        SpannableString value = new SpannableString(prefix + content);
        value.setSpan(new ForegroundColorSpan(Theme.ACCENT), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new StyleSpan(Typeface.BOLD), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
