package com.baixingai.voicedrop.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public final class IosSwitch extends View {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(IosSwitch button, boolean checked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private boolean checked;
    private float progress;
    private OnCheckedChangeListener listener;
    private ValueAnimator animator;

    public IosSwitch(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setMinimumWidth(dp(50));
        setMinimumHeight(dp(30));
        setOnClickListener(v -> setChecked(!checked));
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        if (this.checked == checked) return;
        this.checked = checked;
        animateTo(checked ? 1f : 0f);
        if (listener != null) listener.onCheckedChanged(this, checked);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(dp(50), widthMeasureSpec);
        int height = resolveSize(dp(30), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float inset = dp(1);
        float radius = height / 2f;
        track.set(inset, inset, width - inset, height - inset);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(checked ? Theme.RED : 0xffd8d1c7, isEnabled() ? 255 : 120));
        canvas.drawRoundRect(track, radius, radius, paint);

        float thumbRadius = (height - dp(6)) / 2f;
        float start = dp(3) + thumbRadius;
        float end = width - dp(3) - thumbRadius;
        float cx = start + (end - start) * progress;
        float cy = height / 2f;

        paint.setColor(withAlpha(0xffffffff, isEnabled() ? 255 : 180));
        canvas.drawCircle(cx, cy, thumbRadius, paint);
    }

    private void animateTo(float target) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(160);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
