package com.baixingai.voicedrop.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;

public class BouncyScrollView extends ScrollView {
    private static final float DRAG_RESISTANCE = 0.42f;
    private static final int MAX_TRANSLATION_DP = 72;

    private float lastY;
    private ValueAnimator returnAnimator;

    public BouncyScrollView(Context context) {
        super(context);
        init();
    }

    public BouncyScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BouncyScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelReturnAnimation();
                lastY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float y = event.getY();
                float dy = y - lastY;
                lastY = y;
                applyEdgeDrag(dy);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animateBack();
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private void applyEdgeDrag(float dy) {
        boolean pullingPastTop = dy > 0 && !canScrollVertically(-1);
        boolean pullingPastBottom = dy < 0 && !canScrollVertically(1);
        if (!pullingPastTop && !pullingPastBottom) return;

        View child = bouncyChild();
        if (child == null) return;
        float max = dp(MAX_TRANSLATION_DP);
        float next = child.getTranslationY() + (dy * DRAG_RESISTANCE);
        if (next > max) next = max;
        if (next < -max) next = -max;
        child.setTranslationY(next);
    }

    private void animateBack() {
        View child = bouncyChild();
        if (child == null) return;
        float start = child.getTranslationY();
        if (start == 0f) return;

        cancelReturnAnimation();
        returnAnimator = ValueAnimator.ofFloat(start, 0f);
        returnAnimator.setDuration(360);
        returnAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        returnAnimator.addUpdateListener(animation ->
                child.setTranslationY((float) animation.getAnimatedValue()));
        returnAnimator.start();
    }

    private void cancelReturnAnimation() {
        if (returnAnimator != null) {
            returnAnimator.cancel();
            returnAnimator = null;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View bouncyChild() {
        return getChildCount() == 0 ? null : getChildAt(0);
    }
}
