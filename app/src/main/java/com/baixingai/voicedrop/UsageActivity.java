package com.baixingai.voicedrop;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.UsageStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.Theme;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UsageActivity extends Activity {
    private UsageStore usageStore;
    private LinearLayout content;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usageStore = new UsageStore(new AuthStore(this), new HttpClient());
        configureEdgeToEdge();

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        configureEdgeToEdge();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout top = new FrameLayout(this);
        top.setPadding(dp(12), dp(14) + getStatusBarHeight(), dp(16), dp(10));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout backTouch = new FrameLayout(this);
        backTouch.setClickable(true);
        FrameLayout back = new FrameLayout(this);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(Theme.CARD);
        backBg.setCornerRadius(dp(11));
        backBg.setStroke(dp(1), 0xffe0d8cc);
        back.setBackground(backBg);
        back.setElevation(dp(2));
        ImageView backIcon = new ImageView(this);
        AliIconFont.apply(backIcon, AliIconFont.BACK, Theme.INK);
        backIcon.setScaleType(ImageView.ScaleType.CENTER);
        back.addView(backIcon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        backTouch.addView(back, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        backTouch.setOnClickListener(v -> finishWithPageTransition());
        top.addView(backTouch, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        top.addView(text("算力", 24, Theme.INK, Typeface.BOLD), new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        BouncyScrollView scroll = new BouncyScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(40));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderLoading();
        load();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureEdgeToEdge();
    }

    @Override
    public void onBackPressed() {
        finishWithPageTransition();
    }

    private void load() {
        io.execute(() -> {
            try {
                UsageStore.Balance balance = usageStore.balance();
                List<UsageStore.Entry> entries = usageStore.ledger();
                runOnUiThread(() -> render(balance, entries, true));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    toast("算力加载失败：" + e.getMessage());
                    render(new UsageStore.Balance(0, 0), new ArrayList<>(), false);
                });
            }
        });
    }

    private void renderLoading() {
        render(new UsageStore.Balance(0, 0), new ArrayList<>(), false);
    }

    private void render(UsageStore.Balance balance, List<UsageStore.Entry> entries, boolean loaded) {
        content.removeAllViews();
        content.addView(heroCard(balance, loaded), matchWrap(0, 0, 0, dp(22)));
        content.addView(subscriptionCard(), matchWrap(0, 0, 0, dp(22)));
        List<Bucket> buckets = grantBuckets(entries);
        if (!buckets.isEmpty()) {
            addSection(content, "算力来源");
            content.addView(bucketCard(buckets), matchWrap(0, 0, 0, dp(22)));
        }
        addSection(content, "明细");
        content.addView(ledgerCard(entries, loaded), matchWrap(0, 0, 0, 0));
    }

    private View heroCard(UsageStore.Balance balance, boolean loaded) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(22));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff2a2521, 0xff171411});
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);

        TextView label = text("剩余算力", 13, 0xffc9bfae, Typeface.NORMAL);
        label.setLetterSpacing(0.08f);
        card.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(0, dp(6), 0, 0);
        row.addView(text(String.valueOf((int) Math.round(balance.suanli)), 42, 0xffffffff, Typeface.BOLD));
        TextView capacity = text("≈ " + UsageStore.articleCapacity(balance.suanli) + " 篇", 14, 0xffe2b871, Typeface.BOLD);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(-2, -2);
        capLp.setMargins(dp(8), 0, 0, dp(7));
        row.addView(capacity, capLp);
        card.addView(row);

        String foot = loaded
                ? "累计获赠 " + (int) Math.round(balance.suanli + balance.spentSuanli) + " · 已用 " + (int) Math.round(balance.spentSuanli)
                : "加载中…";
        TextView footer = text(foot, 13, 0xffc9bfae, Typeface.NORMAL);
        footer.setPadding(0, dp(14), 0, 0);
        card.addView(footer);
        return card;
    }

    private View subscriptionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(strokedRound(Theme.CARD, 11, 0xffebd9b8));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView bolt = text("⚡", 22, 0xffc98a2e, Typeface.BOLD);
        bolt.setGravity(Gravity.CENTER);
        bolt.setBackground(round(0xfffbead2, 10));
        row.addView(bolt, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("包月算力", 15, Theme.INK, Typeface.BOLD));
        TextView sub = text("每月 200 算力 · 月底清零 · 随时可取消", 13, Theme.SECONDARY, Typeface.NORMAL);
        sub.setPadding(0, dp(2), 0, 0);
        labels.addView(sub);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1);
        labelsLp.setMargins(dp(10), 0, dp(8), 0);
        row.addView(labels, labelsLp);
        row.addView(text("¥19.9/月", 18, Theme.INK, Typeface.BOLD));
        card.addView(row);

        TextView button = text("包月订阅 · 即将上线", 15, 0xffffffff, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(0x88c98a2e, 9));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(46));
        btnLp.setMargins(0, dp(13), 0, 0);
        card.addView(button, btnLp);
        button.setOnClickListener(v -> IosDialog.show(this, "包月订阅即将上线",
                "包月算力还在开发中，敬请期待。现在的算力来自注册与活动赠送，足够日常使用。"));
        return card;
    }

    private View bucketCard(List<Bucket> buckets) {
        LinearLayout card = card();
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            LinearLayout row = plainRow();
            row.addView(text(b.title, 15, Theme.INK, Typeface.NORMAL), new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(String.valueOf((int) Math.round(b.total)), 15, Theme.INK, Typeface.BOLD));
            card.addView(row);
            if (i < buckets.size() - 1) card.addView(divider());
        }
        return card;
    }

    private View ledgerCard(List<UsageStore.Entry> entries, boolean loaded) {
        LinearLayout card = card();
        if (entries.isEmpty()) {
            TextView empty = text(loaded ? "暂无记录" : "加载中…", 14, Theme.SECONDARY, Typeface.NORMAL);
            empty.setPadding(dp(15), dp(16), dp(15), dp(16));
            card.addView(empty);
            return card;
        }
        for (int i = 0; i < entries.size(); i++) {
            UsageStore.Entry e = entries.get(i);
            LinearLayout row = plainRow();
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(text(label(e), 15, Theme.INK, Typeface.NORMAL));
            TextView time = text(timeText(e), 12, Theme.FAINT, Typeface.NORMAL);
            time.setPadding(0, dp(2), 0, 0);
            left.addView(time);
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            boolean grant = "grant".equals(e.kind);
            row.addView(text((grant ? "+" : "-") + fmt(e.suanli), 15,
                    grant ? 0xff2f9b68 : Theme.RED, Typeface.BOLD));
            card.addView(row);
            if (i < entries.size() - 1) card.addView(divider());
        }
        return card;
    }

    private List<Bucket> grantBuckets(List<UsageStore.Entry> entries) {
        Map<String, Double> sums = new LinkedHashMap<>();
        for (UsageStore.Entry e : entries) {
            if (!"grant".equals(e.kind)) continue;
            String key;
            if ("signup".equals(e.reason)) key = "注册赠送";
            else if (e.reason != null && e.reason.startsWith("campaign:")) key = "活动赠送";
            else if ("monthly".equals(e.reason) || "subscription".equals(e.reason)) key = "包月发放";
            else key = label(e);
            Double value = sums.get(key);
            sums.put(key, (value == null ? 0 : value) + e.suanli);
        }
        String[] order = new String[]{"包月发放", "活动赠送", "注册赠送"};
        List<Bucket> out = new ArrayList<>();
        for (String key : order) {
            if (sums.containsKey(key)) {
                out.add(new Bucket(key, sums.remove(key)));
            }
        }
        for (Map.Entry<String, Double> e : sums.entrySet()) out.add(new Bucket(e.getKey(), e.getValue()));
        return out;
    }

    private String label(UsageStore.Entry e) {
        if ("signup".equals(e.reason)) return "注册赠送";
        if ("asr".equals(e.reason)) return "语音转写";
        if ("mine".equals(e.reason)) return "挖文章";
        if ("edit".equals(e.reason)) return "语音修改";
        if (e.reason != null && e.reason.startsWith("campaign:")) return "活动赠送";
        return e.reason == null || e.reason.isEmpty() ? e.kind : e.reason;
    }

    private String fmt(double value) {
        return value < 10 ? String.format(Locale.US, "%.1f", value) : String.valueOf((int) Math.round(value));
    }

    private String timeText(UsageStore.Entry e) {
        return new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(new Date(e.ts));
    }

    private void addSection(LinearLayout parent, String label) {
        TextView section = text(label, 13, Theme.FAINT, Typeface.BOLD);
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(4), 0, 0, dp(8));
        parent.addView(section);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(Theme.CARD, 11));
        return card;
    }

    private LinearLayout plainRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(13), dp(15), dp(13));
        return row;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(0xffeee6db);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(15), 0, dp(15), 0);
        view.setLayoutParams(lp);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable strokedRound(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = round(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : (int) (24 * metrics.density);
    }

    private void configureEdgeToEdge() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Theme.BG);
        }
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void toast(String message) {
        runOnUiThread(() -> SimpleToast.show(this, message));
    }

    private static final class Bucket {
        final String title;
        final double total;
        Bucket(String title, double total) {
            this.title = title;
            this.total = total;
        }
    }
}
