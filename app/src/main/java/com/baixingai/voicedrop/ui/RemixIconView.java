package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.baixingai.voicedrop.R;

/** Text view configured to render the bundled Remix Icon 2.5.0 font. */
public final class RemixIconView extends TextView {
    public RemixIconView(Context context) {
        super(context);
        initialize();
    }

    public RemixIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public RemixIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        Typeface iconFont = ResourcesCompat.getFont(getContext(), R.font.remixicon);
        if (iconFont != null) setTypeface(iconFont);
        setGravity(Gravity.CENTER);
        setIncludeFontPadding(false);
    }

    public void setIcon(String glyph) {
        setText(glyph);
    }
}
