package com.baixingai.voicedrop.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

/**
 * Local 3:4 Xiaohongshu text cards: one title card followed by paged body cards.
 * The copy remains in the clipboard too, so the cards are a readable visual companion.
 */
public final class XhsCards {
    public static final int WIDTH = 1080;
    public static final int HEIGHT = 1440;
    private static final int MAX_BODY_PAGES = 11;
    private static final int HORIZONTAL_PADDING = 110;
    private static final int BODY_TOP = 150;
    private static final int BODY_HEIGHT = 1120;
    private static final int BG = 0xfff5f1e8;
    private static final int INK = 0xff3a352e;
    private static final int META = 0xff8d8372;
    private static final int ACCENT = 0xffb9502e;

    private XhsCards() {}

    public static List<Bitmap> render(String title, String body, String date) {
        List<String> pages = paginate(body == null ? "" : body);
        int total = pages.size() + 1;
        List<Bitmap> cards = new ArrayList<>();
        cards.add(titleCard(title == null ? "" : title, date == null ? "" : date, 1, total));
        for (int i = 0; i < pages.size(); i++) {
            cards.add(bodyCard(pages.get(i), i + 2, total));
        }
        return cards;
    }

    private static Bitmap titleCard(String title, String date, int page, int total) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        fill(canvas);
        TextPaint meta = textPaint(30, META, Typeface.NORMAL);
        meta.setLetterSpacing(0.12f);
        if (!date.trim().isEmpty()) canvas.drawText(date, 112, 230, meta);
        Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        accent.setColor(ACCENT);
        canvas.drawRoundRect(112, 286, 188, 294, 4, 4, accent);
        TextPaint titlePaint = textPaint(78, INK, Typeface.BOLD);
        titlePaint.setLetterSpacing(0.02f);
        drawLayout(canvas, layout(title, titlePaint, WIDTH - 220, 26), 110, 380);
        footer(canvas, page, total);
        return bitmap;
    }

    private static Bitmap bodyCard(String body, int page, int total) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        fill(canvas);
        TextPaint bodyPaint = bodyPaint();
        drawLayout(canvas, layout(body, bodyPaint, WIDTH - 220, 26), HORIZONTAL_PADDING, BODY_TOP);
        footer(canvas, page, total);
        return bitmap;
    }

    private static List<String> paginate(String body) {
        List<String> pages = new ArrayList<>();
        if (body.trim().isEmpty()) return pages;
        TextPaint bodyPaint = bodyPaint();
        int start = 0;
        while (start < body.length() && pages.size() < MAX_BODY_PAGES) {
            String remaining = body.substring(start);
            StaticLayout candidate = layout(remaining, bodyPaint, WIDTH - 220, 26);
            int line = 0;
            while (line + 1 < candidate.getLineCount()
                    && candidate.getLineBottom(line + 1) <= BODY_HEIGHT) {
                line++;
            }
            int end = candidate.getLineEnd(line);
            if (end <= 0) end = Math.min(remaining.length(), 1);
            pages.add(remaining.substring(0, end).trim());
            start += end;
            while (start < body.length() && Character.isWhitespace(body.charAt(start))) start++;
        }
        return pages;
    }

    private static StaticLayout layout(String value, TextPaint paint, int width, float spacing) {
        return StaticLayout.Builder.obtain(value, 0, value.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(spacing, 1f)
                .build();
    }

    private static TextPaint bodyPaint() {
        TextPaint paint = textPaint(42, INK, Typeface.NORMAL);
        paint.setLetterSpacing(0.01f);
        return paint;
    }

    private static TextPaint textPaint(float size, int color, int typefaceStyle) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, typefaceStyle));
        return paint;
    }

    private static void fill(Canvas canvas) {
        canvas.drawColor(BG);
    }

    private static void drawLayout(Canvas canvas, StaticLayout layout, int x, int y) {
        canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restore();
    }

    private static void footer(Canvas canvas, int page, int total) {
        String value = page + " / " + total;
        TextPaint paint = textPaint(26, META, Typeface.NORMAL);
        paint.setLetterSpacing(0.12f);
        float width = paint.measureText(value);
        canvas.drawText(value, (WIDTH - width) / 2f, HEIGHT - 58, paint);
    }
}
