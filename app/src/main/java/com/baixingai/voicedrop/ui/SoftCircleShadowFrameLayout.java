package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.FrameLayout;

public final class SoftCircleShadowFrameLayout extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int shadowRadius;

    public SoftCircleShadowFrameLayout(Context context) {
        super(context);
        shadowRadius = dp(8);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        paint.setColor(0xffffffff);
        paint.setShadowLayer(shadowRadius, 0f, 0f, 0x33000000);
        setPadding(shadowRadius, shadowRadius, shadowRadius, shadowRadius);
        setClickable(true);
    }

    @Override protected void onDraw(Canvas canvas) {
        float radius = Math.min(getWidth(), getHeight()) / 2f - shadowRadius;
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);
        super.onDraw(canvas);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
