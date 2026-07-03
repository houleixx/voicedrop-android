package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.ImageView;

public final class RoundedImageView extends ImageView {
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final float radius;

    public RoundedImageView(Context context) {
        super(context);
        radius = dp(10);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override protected void onDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.onDraw(canvas);
        canvas.restoreToCount(save);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
