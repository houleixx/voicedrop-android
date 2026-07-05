package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

public class PullRefreshLayout extends FrameLayout {
    public interface OnRefreshListener {
        void onRefresh();
    }

    private final ProgressBar spinner;
    private final int touchSlop;
    private final float triggerDistance;
    private View content;
    private OnRefreshListener listener;
    private boolean refreshing;
    private boolean dragging;
    private float downX;
    private float downY;
    private float pullDistance;
    private int spinnerColor;
    private int spinnerBgColor;

    public PullRefreshLayout(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        triggerDistance = dp(76);
        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setVisibility(INVISIBLE);
        spinner.setAlpha(0f);
        LayoutParams spinnerLp = new LayoutParams(dp(34), dp(34), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        spinnerLp.topMargin = dp(10);
        super.addView(spinner, spinnerLp);
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.listener = listener;
    }

    public void setRefreshing(boolean refreshing) {
        this.refreshing = refreshing;
        if (refreshing) {
            spinner.setVisibility(VISIBLE);
            spinner.setAlpha(1f);
            if (content != null) content.animate().translationY(dp(54)).setDuration(140).start();
        } else {
            dragging = false;
            pullDistance = 0f;
            spinner.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                if (!this.refreshing) spinner.setVisibility(INVISIBLE);
            }).start();
            if (content != null) content.animate().translationY(0f).setDuration(160).start();
        }
    }

    public void setColorSchemeColors(int... colors) {
        if (colors.length > 0) {
            spinnerColor = colors[0];
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                spinner.setIndeterminateTintList(ColorStateList.valueOf(colors[0]));
            } else {
                spinner.getIndeterminateDrawable().setColorFilter(colors[0],
                        android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
    }

    public void setProgressBackgroundColorSchemeColor(int color) {
        spinnerBgColor = color;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            spinner.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    @Override
    public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
        if (child == spinner) {
            super.addView(child, index, params);
            return;
        }
        content = child;
        super.addView(child, 0, params);
        spinner.bringToFront();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (refreshing || content == null) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = false;
                    return false;
                }
                if (dy > touchSlop && !content.canScrollVertically(-1)) {
                    dragging = true;
                    return true;
                }
                break;
            default:
                dragging = false;
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (refreshing || content == null) return true;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                pullDistance = Math.max(0f, (ev.getY() - downY) * 0.48f);
                if (pullDistance > 0f) {
                    spinner.setVisibility(VISIBLE);
                    spinner.setAlpha(Math.min(1f, pullDistance / triggerDistance));
                    content.setTranslationY(Math.min(pullDistance, dp(86)));
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging && pullDistance >= triggerDistance) {
                    setRefreshing(true);
                    if (listener != null) listener.onRefresh();
                } else {
                    setRefreshing(false);
                }
                return true;
            default:
                break;
        }
        return true;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
