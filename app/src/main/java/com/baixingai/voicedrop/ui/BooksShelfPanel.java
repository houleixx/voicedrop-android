package com.baixingai.voicedrop.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.BookReaderActivity;
import com.baixingai.voicedrop.BookWritingActivity;
import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.net.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Physical two-column book shelf, kept visually aligned with iOS BooksShelfView. */
public final class BooksShelfPanel extends LinearLayout {
    private static final String INDEX = "https://voicedrop.cn/books/?format=json";
    private static final int CREAM = 0xfff7f1df;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final PullRefreshLayout refresher;
    private final LinearLayout shelves;
    private List<BookShelfIndex.Book> books = new ArrayList<>();

    public BooksShelfPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.BG);

        refresher = new PullRefreshLayout(context);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        shelves = new LinearLayout(context);
        shelves.setOrientation(VERTICAL);
        shelves.setClipChildren(false);
        shelves.setPadding(dp(20), dp(6), dp(20), dp(20));
        scroll.addView(shelves, new ScrollView.LayoutParams(-1, -2));
        refresher.addView(scroll, new PullRefreshLayout.LayoutParams(-1, -1));
        refresher.setRefreshTarget(scroll, 0);
        refresher.setColorSchemeColors(Theme.RED);
        refresher.setOnRefreshListener(() -> load(false));
        addView(refresher, new LinearLayout.LayoutParams(-1, 0, 1));

        books = BookShelfIndex.parse(context.getSharedPreferences("voicedrop.books", Context.MODE_PRIVATE)
                .getString("index", ""));
        render();
        load(true);
    }

    private void load(boolean quiet) {
        if (!quiet) refresher.setRefreshing(true);
        io.execute(() -> {
            String raw = null;
            try {
                HttpClient.Response response = new HttpClient().get(INDEX, null);
                if (response.ok()) raw = response.text();
            } catch (Exception ignored) {}
            String result = raw;
            post(() -> {
                if (result != null) {
                    getContext().getSharedPreferences("voicedrop.books", Context.MODE_PRIVATE)
                            .edit().putString("index", result).apply();
                    books = BookShelfIndex.parse(result);
                }
                render();
                refresher.setRefreshing(false);
            });
        });
    }

    private void render() {
        shelves.removeAllViews();
        List<Object> cells = new ArrayList<>();
        cells.add("write");
        cells.addAll(books);
        for (int index = 0; index < cells.size(); index += 2) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setClipChildren(false);
            row.addView(cell(cells.get(index)), weightedCellParams(0));
            if (index + 1 < cells.size()) {
                row.addView(cell(cells.get(index + 1)), weightedCellParams(dp(22)));
            } else {
                View empty = new View(getContext());
                row.addView(empty, weightedCellParams(dp(22)));
            }
            shelves.addView(row, new LinearLayout.LayoutParams(-1, -2));
            shelves.addView(shelfBar(), shelfParams());
        }
    }

    private LinearLayout.LayoutParams weightedCellParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.leftMargin = leftMargin;
        return params;
    }

    private View cell(Object value) {
        return value instanceof BookShelfIndex.Book
                ? bookCell((BookShelfIndex.Book) value)
                : writeCell();
    }

    private View writeCell() {
        LinearLayout cell = cellContainer();
        cell.setOnClickListener(v -> {
            if (getContext() instanceof Activity) {
                BookWritingActivity.open((Activity) getContext());
            } else {
                getContext().startActivity(new Intent(getContext(), BookWritingActivity.class));
            }
        });

        AspectFrame cover = new AspectFrame(getContext());
        GradientDrawable paper = new GradientDrawable();
        paper.setColor(0xfff3ece0);
        paper.setCornerRadius(dp(5));
        paper.setStroke(dpF(1.5f), 0xffcfc0a6, dp(5), dp(4));
        cover.setBackground(paper);

        LinearLayout prompt = new LinearLayout(getContext());
        prompt.setOrientation(VERTICAL);
        prompt.setGravity(Gravity.CENTER);
        TextView plus = text("+", 25, Color.WHITE, Typeface.BOLD, false);
        plus.setGravity(Gravity.CENTER);
        plus.setElevation(dp(5));
        plus.setBackground(round(Theme.RED, 17));
        prompt.addView(plus, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView label = text("写书", 15, 0xff6f685d, Typeface.BOLD, false);
        label.setGravity(Gravity.CENTER);
        label.setLetterSpacing(0.067f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.topMargin = dp(9);
        prompt.addView(label, labelParams);
        cover.addView(prompt, new FrameLayout.LayoutParams(-1, -1));
        cell.addView(cover, new LinearLayout.LayoutParams(-1, -2));
        cell.addView(caption("写一本新书", " "), captionParams());
        return cell;
    }

    private View bookCell(BookShelfIndex.Book book) {
        LinearLayout cell = cellContainer();
        cell.setOnClickListener(v -> {
            if (getContext() instanceof Activity) {
                BookReaderActivity.open((Activity) getContext(), book.slug, book.main);
            }
        });

        PhysicalBookCover cover = new PhysicalBookCover(getContext(), color(book.c), color(book.c2));
        cover.setElevation(dp(7));
        if (book.cover) {
            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.addView(image, new FrameLayout.LayoutParams(-1, -1));
            io.execute(() -> {
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(new java.net.URL(book.coverUrl()).openStream());
                    image.post(() -> image.setImageBitmap(bitmap));
                } catch (Exception ignored) {}
            });
        } else {
            LinearLayout typography = new LinearLayout(getContext());
            typography.setOrientation(VERTICAL);
            typography.setGravity(Gravity.LEFT);
            typography.setPadding(dp(24), dp(26), dp(16), 0);
            TextView title = text(book.main, 22, CREAM, Typeface.BOLD, true);
            title.setLetterSpacing(0.136f);
            title.setLineSpacing(dp(4), 1f);
            typography.addView(title, new LinearLayout.LayoutParams(-1, -2));
            View rule = new View(getContext());
            rule.setBackgroundColor(0x8cf7f1df);
            LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(dp(26), dpF(1));
            ruleParams.setMargins(0, dp(9), 0, dp(9));
            typography.addView(rule, ruleParams);
            if (book.sub != null && !book.sub.isEmpty()) {
                TextView subtitle = text(book.sub, 12, 0xb8f7f1df, Typeface.NORMAL, true);
                subtitle.setLineSpacing(dp(3), 1f);
                typography.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
            }
            cover.addView(typography, new FrameLayout.LayoutParams(-1, -1));
        }
        cell.addView(cover, new LinearLayout.LayoutParams(-1, -2));
        String meta = book.chapters > 0 ? book.chapters + " 章" : book.sub;
        cell.addView(caption(book.main, meta == null || meta.isEmpty() ? " " : meta), captionParams());
        return cell;
    }

    private LinearLayout cellContainer() {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        cell.setClipChildren(false);
        return cell;
    }

    private LinearLayout caption(String titleValue, String metaValue) {
        LinearLayout caption = new LinearLayout(getContext());
        caption.setOrientation(VERTICAL);
        TextView title = text(titleValue, 15, Theme.INK, Typeface.BOLD, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.addView(title, new LinearLayout.LayoutParams(-1, dp(22)));
        TextView meta = text(metaValue, 13, 0xffa69c8c, Typeface.NORMAL, false);
        meta.setSingleLine(true);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.addView(meta, new LinearLayout.LayoutParams(-1, dp(19)));
        return caption;
    }

    private LinearLayout.LayoutParams captionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(43));
        params.topMargin = dp(9);
        return params;
    }

    private View shelfBar() {
        View shelf = new View(getContext());
        GradientDrawable wood = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xffe3d7c2, 0xffc9b99e});
        wood.setCornerRadius(dp(1));
        shelf.setBackground(wood);
        shelf.setElevation(dp(3));
        return shelf;
    }

    private LinearLayout.LayoutParams shelfParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(6));
        params.leftMargin = -dp(6);
        params.rightMargin = -dp(6);
        params.bottomMargin = dp(22);
        return params;
    }

    private TextView text(String value, int size, int color, int style, boolean serif) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create(serif ? "serif" : "sans-serif", style));
        return view;
    }

    private int color(String value) {
        try { return Color.parseColor(value); }
        catch (Exception ignored) { return 0xff8b6652; }
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int dpF(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDetachedFromWindow() {
        io.shutdownNow();
        super.onDetachedFromWindow();
    }

    /** Keeps every cover at the iOS 0.7 width-to-height ratio. */
    private static class AspectFrame extends FrameLayout {
        AspectFrame(Context context) { super(context); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.round(width / 0.7f), MeasureSpec.EXACTLY));
        }
    }

    /** Draws the cloth, rounded book silhouette, curved spine and striped page edge. */
    private static final class PhysicalBookCover extends AspectFrame {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int startColor;
        private final int endColor;
        private final Path clipPath = new Path();

        PhysicalBookCover(Context context, int startColor, int endColor) {
            super(context);
            this.startColor = startColor;
            this.endColor = endColor;
            setWillNotDraw(false);
            setClipChildren(false);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 5 * getResources().getDisplayMetrics().density);
                }
            });
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            float density = getResources().getDisplayMetrics().density;
            float leftRadius = 2 * density;
            float rightRadius = 5 * density;
            clipPath.reset();
            clipPath.addRoundRect(new RectF(0, 0, width, height),
                    new float[]{leftRadius, leftRadius, rightRadius, rightRadius,
                            rightRadius, rightRadius, leftRadius, leftRadius}, Path.Direction.CW);
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.clipPath(clipPath);
            paint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(),
                    startColor, endColor, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(new RadialGradient(getWidth() * .25f, getHeight() * .15f,
                    getWidth(), 0x1affffff, 0x00ffffff, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            canvas.restore();
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            canvas.save();
            canvas.clipPath(clipPath);
            super.dispatchDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float spineWidth = 13 * density;
            paint.setShader(new LinearGradient(0, 0, spineWidth, 0,
                    new int[]{0x5c000000, 0x1a000000, 0x1fffffff},
                    new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, spineWidth, getHeight(), paint);
            paint.setShader(null);
            int pageWidth = Math.max(1, Math.round(3 * density));
            int line = Math.max(1, Math.round(density));
            for (int y = 0; y < getHeight(); y += line * 2) {
                paint.setColor(0xd9ffffff);
                canvas.drawRect(getWidth() - pageWidth, y, getWidth(), y + line, paint);
                paint.setColor(0xe6d6cab4);
                canvas.drawRect(getWidth() - pageWidth, y + line, getWidth(), y + line * 2, paint);
            }
            canvas.restore();
        }
    }
}
