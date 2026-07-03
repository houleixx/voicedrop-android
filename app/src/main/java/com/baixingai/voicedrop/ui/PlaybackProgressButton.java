package com.baixingai.voicedrop.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

public final class PlaybackProgressButton extends FrameLayout {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private final ImageView icon;
    private final ProgressBar loading;
    private AudioPlaybackState.Mode mode = AudioPlaybackState.Mode.IDLE;
    private float progress;

    public PlaybackProgressButton(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);

        int buttonSize = dp(34);
        int stroke = dp(3);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0x33df5b49);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Theme.RED);

        FrameLayout button = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.RED);
        bg.setCornerRadius(dp(17));
        button.setBackground(bg);
        button.setElevation(dp(2));
        addView(button, new FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.CENTER));

        icon = new ImageView(context);
        AliIconFont.apply(icon, AliIconFont.PLAY, 0xffffffff);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        button.addView(icon, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));

        loading = new ProgressBar(context);
        loading.setIndeterminate(true);
        loading.setVisibility(GONE);
        button.addView(loading, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
    }

    public void setState(AudioPlaybackState.Mode mode, float progress) {
        this.mode = mode;
        this.progress = progress;
        loading.setVisibility(mode == AudioPlaybackState.Mode.LOADING ? VISIBLE : GONE);
        icon.setVisibility(mode == AudioPlaybackState.Mode.LOADING ? GONE : VISIBLE);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int inset = dp(4);
        arcBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        if (mode == AudioPlaybackState.Mode.LOADING || mode == AudioPlaybackState.Mode.PLAYING || progress > 0f) {
            canvas.drawArc(arcBounds, -90, 360, false, trackPaint);
        }
        if (progress > 0f) {
            canvas.drawArc(arcBounds, -90, 360f * progress, false, progressPaint);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
