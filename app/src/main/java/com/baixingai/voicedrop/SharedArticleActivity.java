package com.baixingai.voicedrop;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.core.ArticleBody;
import com.baixingai.voicedrop.data.ArticleDoc;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.LibraryStore;
import com.baixingai.voicedrop.data.MinedArticle;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.RoundedImageView;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SharedArticleActivity extends Activity {
    public static final String EXTRA_SHARED_JSON = "sharedJson";
    public static final String EXTRA_ARTICLE_INDEX = "articleIndex";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LibraryStore library;
    private ArticleDoc doc;
    private int articleIndex;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
        library = new LibraryStore(new AuthStore(this), new HttpClient());
        articleIndex = Math.max(0, getIntent().getIntExtra(EXTRA_ARTICLE_INDEX, 0));
        try {
            doc = ArticleDoc.fromJson(getIntent().getStringExtra(EXTRA_SHARED_JSON));
        } catch (Exception e) {
            doc = null;
        }
        render();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        SystemBarDefaults.applyTopInsets(bar, dp(14), dp(8), dp(14), dp(8));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout back = new FrameLayout(this);
        back.setBackground(round(Theme.CARD, 11));
        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, AliIconFont.BACK, Theme.INK);
        back.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        bar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());

        if (doc == null || doc.articles.isEmpty()) {
            TextView empty = text("这篇分享已不可用", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        SystemBarDefaults.applyBottomInsets(content, dp(20), dp(10), dp(20), dp(34));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        int idx = articleIndex >= doc.articles.size() ? 0 : articleIndex;
        if (doc.articles.size() > 1) addChips(content, idx);
        MinedArticle article = doc.articles.get(idx);
        TextView title = text(article.title, 23, Theme.INK, Typeface.BOLD);
        title.setLineSpacing(dp(3), 1f);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        renderBody(content, article.body);
    }

    private void addChips(LinearLayout content, int selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(16));
        for (int i = 0; i < doc.articles.size(); i++) {
            final int index = i;
            TextView chip = text(doc.articles.get(i).title, 13,
                    i == selected ? Theme.RED : Theme.SECONDARY,
                    i == selected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setSingleLine(true);
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            chip.setBackground(round(i == selected ? 0xffffe6df : Theme.CARD, 14));
            chip.setOnClickListener(v -> {
                articleIndex = index;
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(8), 0);
            row.addView(chip, lp);
        }
        content.addView(row);
    }

    private void renderBody(LinearLayout content, String body) {
        for (ArticleBody.Segment segment : ArticleBody.segments(body)) {
            if (segment.type == ArticleBody.Segment.Type.PHOTO) {
                String key = ArticleBody.resolvePhotoKey(segment.value, doc.photos);
                if (key != null) content.addView(photoTile(key), new LinearLayout.LayoutParams(-1, dp(220)));
            } else {
                String t = segment.value == null ? "" : segment.value.trim();
                if (t.isEmpty()) continue;
                TextView p = text(t, 16, 0xff4f4942, Typeface.NORMAL);
                p.setLineSpacing(dp(7), 1f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, dp(18), 0, 0);
                content.addView(p, lp);
            }
        }
    }

    private FrameLayout photoTile(String relKey) {
        FrameLayout frame = new FrameLayout(this);
        TextView loading = text("图片加载中…", 13, Theme.FAINT, Typeface.NORMAL);
        loading.setGravity(Gravity.CENTER);
        frame.addView(loading, new FrameLayout.LayoutParams(-1, -1));
        io.execute(() -> {
            try {
                if (doc.ownerScope == null || doc.ownerScope.isEmpty()) return;
                String scope = doc.ownerScope.endsWith("/") ? doc.ownerScope : doc.ownerScope + "/";
                Bitmap bitmap = library.photoImage(scope + relKey, false);
                if (bitmap == null) return;
                main.post(() -> {
                    frame.removeAllViews();
                    ImageView image = new RoundedImageView(this);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setImageBitmap(bitmap);
                    frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
                    int width = frame.getWidth();
                    if (width > 0 && bitmap.getWidth() > 0) {
                        int height = Math.max(dp(120), Math.round(width * (bitmap.getHeight() / (float) bitmap.getWidth())));
                        ViewGroup.LayoutParams lp = frame.getLayoutParams();
                        if (lp != null) {
                            lp.height = height;
                            frame.setLayoutParams(lp);
                        }
                    }
                });
            } catch (Exception ignored) {
            }
        });
        return frame;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
