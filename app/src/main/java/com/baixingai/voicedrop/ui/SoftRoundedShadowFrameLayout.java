package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.FrameLayout;

public final class SoftRoundedShadowFrameLayout extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int shadowRadius;
    private final int cornerRadius;

    public SoftRoundedShadowFrameLayout(Context context) {
        super(context);
        shadowRadius = dp(8);
        cornerRadius = dp(16);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        paint.setColor(0xffffffff);
        paint.setShadowLayer(shadowRadius, 0f, 0f, 0x22000000);
        setPadding(shadowRadius, shadowRadius, shadowRadius, shadowRadius);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(true);
    }

    @Override protected void onDraw(Canvas canvas) {
        rect.set(shadowRadius, shadowRadius, getWidth() - shadowRadius, getHeight() - shadowRadius);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        super.onDraw(canvas);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
