package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.data.BookCoverLoader;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.ui.PullRefreshLayout;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native write-book shelf, mirroring iOS BooksShelfView. */
public final class BooksShelfActivity extends Activity {
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private BookCoverLoader coverLoader;
    private PullRefreshLayout refresher;
    private GridLayout grid;
    private TextView state;
    private List<BookShelfIndex.Book> books = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        coverLoader = new BookCoverLoader(this);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(Theme.BG);
        page.addView(topBar(), new LinearLayout.LayoutParams(-1, -2));
        page.addView(tabs(), new LinearLayout.LayoutParams(-1, -2));
        refresher = new PullRefreshLayout(this);
        ScrollView scroll = new ScrollView(this); grid = new GridLayout(this); grid.setColumnCount(2); grid.setPadding(dp(20), dp(8), dp(20), dp(40));
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2)); refresher.addView(scroll, new PullRefreshLayout.LayoutParams(-1, -1));
        refresher.setRefreshTarget(scroll, 0); refresher.setColorSchemeColors(Theme.ACCENT); refresher.setOnRefreshListener(() -> load(false));
        page.addView(refresher, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(page);
        books = BookShelfIndex.parse(getSharedPreferences("voicedrop.books", MODE_PRIVATE).getString("index", ""));
        render(); load(true);
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); SystemBarDefaults.applyTopInsets(bar, dp(20), dp(8), dp(12), 0);
        TextView logo = text("VoiceDrop 口述", 14, Theme.SECONDARY, Typeface.BOLD); bar.addView(logo, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView settings = text("⚙", 24, Theme.SECONDARY, Typeface.NORMAL); settings.setGravity(Gravity.CENTER); settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class))); bar.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44))); return bar;
    }
    private View tabs() {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(20), 0, dp(20), dp(10));
        TextView recordings = tab("我的录音", false); recordings.setOnClickListener(v -> finish()); row.addView(recordings);
        TextView community = tab("VD社区", false); community.setOnClickListener(v -> { Intent i = new Intent(this, RecordingsActivity.class); i.setData(android.net.Uri.parse("voicedrop://community")); startActivity(i); finish(); }); row.addView(community);
        LinearLayout active = new LinearLayout(this); active.setOrientation(LinearLayout.VERTICAL); TextView booksTab = tab("写书", true); active.addView(booksTab); View line = new View(this); line.setBackgroundColor(Theme.RED); active.addView(line, new LinearLayout.LayoutParams(-1, dp(3))); row.addView(active); return row;
    }
    private TextView tab(String label, boolean active) { TextView view = text(label, 20, active ? Theme.INK : Theme.FAINT, Typeface.BOLD); view.setPadding(dp(12), dp(6), dp(12), dp(6)); return view; }

    private void load(boolean quiet) {
        if (!quiet) refresher.setRefreshing(true);
        io.execute(() -> { String raw = null; try { HttpClient.Response response = new HttpClient().get(Api.publicWebBase() + "/books/?format=json", null); if (response.ok()) raw = response.text(); } catch (Exception ignored) {}
            String finalRaw = raw; runOnUiThread(() -> { if (finalRaw != null) { getSharedPreferences("voicedrop.books", MODE_PRIVATE).edit().putString("index", finalRaw).apply(); books = BookShelfIndex.parse(finalRaw); } render(); refresher.setRefreshing(false); }); });
    }
    private void render() {
        coverLoader.cancelAll();
        grid.removeAllViews(); addCell(writeCell()); for (BookShelfIndex.Book book : books) addCell(bookCell(book));
        if (books.isEmpty()) { state = text("正在整理书架…", 14, Theme.SECONDARY, Typeface.NORMAL); state.setGravity(Gravity.CENTER); grid.addView(state, new GridLayout.LayoutParams(GridLayout.spec(1), GridLayout.spec(0,2))); }
    }
    private void addCell(View view) { GridLayout.LayoutParams lp = new GridLayout.LayoutParams(); lp.width = 0; lp.height = -2; lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); lp.setMargins(dp(8), dp(8), dp(8), dp(20)); grid.addView(view, lp); }
    private View writeCell() {
        LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setOnClickListener(v -> BookWritingActivity.open(this));
        LinearLayout cover = new LinearLayout(this); cover.setOrientation(LinearLayout.VERTICAL); cover.setGravity(Gravity.CENTER); cover.setBackground(roundStroke(0xfffffaf0, 8, 0xffd7bca4, 2)); TextView plus=text("＋",36,0xffffffff,Typeface.NORMAL);plus.setGravity(Gravity.CENTER);plus.setBackground(round(Theme.ACCENT,24));cover.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(48))); cover.addView(text("写书", 18, Theme.ACCENT, Typeface.BOLD)); cell.addView(cover, new LinearLayout.LayoutParams(-1, dp(210))); cell.addView(caption("写一本新书")); cell.addView(shelfBar()); return cell;
    }
    private View bookCell(BookShelfIndex.Book book) {
        LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setOnClickListener(v -> BookReaderActivity.open(this, book));
        FrameLayout cover = new FrameLayout(this); GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{color(book.c), color(book.c2)}); bg.setCornerRadius(dp(7)); cover.setBackground(bg);
        addBookTypography(cover, book);
        if (book.cover) { ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.addView(image, new FrameLayout.LayoutParams(-1,-1)); coverLoader.load(book, book.coverUrl(Api.publicWebBase()), image); }
        cell.addView(cover, new LinearLayout.LayoutParams(-1, dp(210)));
        cell.addView(caption(book.main)); TextView meta = text(book.chapters > 0 ? book.chapters + " 章" : book.sub, 12, Theme.FAINT, Typeface.NORMAL); meta.setPadding(0,dp(3),0,0); cell.addView(meta); cell.addView(shelfBar()); return cell;
    }
    private void addBookTypography(FrameLayout cover, BookShelfIndex.Book book) {
        LinearLayout typography = new LinearLayout(this); typography.setOrientation(LinearLayout.VERTICAL); typography.setGravity(Gravity.CENTER); typography.setPadding(dp(15), dp(15), dp(15), dp(15));
        TextView main = text(book.main, 20, 0xfffff8e9, Typeface.BOLD); main.setGravity(Gravity.CENTER); typography.addView(main); View rule = new View(this); rule.setBackgroundColor(0xccffffff); LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(28), dp(1)); rlp.setMargins(0,dp(12),0,dp(12)); typography.addView(rule, rlp); TextView sub = text(book.sub, 12, 0xfffff8e9, Typeface.NORMAL); sub.setGravity(Gravity.CENTER); typography.addView(sub);
        cover.addView(typography, new FrameLayout.LayoutParams(-1, -1));
    }
    private View shelfBar(){View bar=new View(this);bar.setBackgroundColor(0xff8b5f3d);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(9));lp.setMargins(0,dp(8),0,0);bar.setLayoutParams(lp);return bar;}
    private TextView caption(String value) { TextView v=text(value,14,Theme.INK,Typeface.BOLD); v.setSingleLine(); v.setPadding(0,dp(10),0,0); return v; }
    private int color(String value) { try { return Color.parseColor(value); } catch (Exception e) { return 0xff8b6652; } }
    private TextView text(String v,int s,int c,int style){ TextView t=new TextView(this);t.setText(v);t.setTextSize(s);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,style);return t; }
    private GradientDrawable roundStroke(int c,int r,int sc,int sw){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));d.setStroke(dp(sw),sc);return d;}
    private GradientDrawable round(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){coverLoader.shutdown();io.shutdownNow();super.onDestroy();}
}
