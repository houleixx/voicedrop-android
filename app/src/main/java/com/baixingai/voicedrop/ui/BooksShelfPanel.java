package com.baixingai.voicedrop.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.baixingai.voicedrop.BookReaderActivity;
import com.baixingai.voicedrop.BookWritingActivity;
import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.core.BookShelfLoadingPolicy;
import com.baixingai.voicedrop.core.BookShelfSearch;
import com.baixingai.voicedrop.core.BookShelfSearchState;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.BookShelfCache;
import com.baixingai.voicedrop.data.BookCoverLoader;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.net.Api;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Physical two-column book shelf, kept visually aligned with iOS BooksShelfView. */
public final class BooksShelfPanel extends LinearLayout {
    private static final int CREAM = 0xfff7f1df;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final BookCoverLoader coverLoader;
    private final PullRefreshLayout refresher;
    private final RecyclerView shelves;
    private final ShelfAdapter shelfAdapter;
    private final AuthStore auth;
    private BookShelfCache shelfCache;
    private List<BookShelfIndex.Book> books = new ArrayList<>();
    private boolean initialLoadPending;
    private boolean shelfFailed;
    private boolean disposed;
    private boolean searching;
    private String query = "";
    private String selectedFilter = BookShelfSearch.ALL;
    private List<BookShelfSearch.Match> visible = new ArrayList<>();
    private final BookShelfSearchState searchState = new BookShelfSearchState();
    private final LinearLayout filterRow;
    private final TextView searchStatus;
    private final Runnable searchRequest = this::loadSearchIndex;

    public BooksShelfPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.BG);
        auth = new AuthStore(context);
        shelfCache = new BookShelfCache(context, auth.libraryCacheIdentity());
        coverLoader = new BookCoverLoader(context);

        filterRow = new LinearLayout(context);
        filterRow.setOrientation(HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterRow.setPadding(dp(18), 0, dp(18), 0);
        filterRow.setBackgroundColor(Theme.FILTER_BG);
        addView(filterRow, new LinearLayout.LayoutParams(-1, dp(44)));
        searchStatus = text("", 12, Theme.SECONDARY, Typeface.NORMAL, false);
        searchStatus.setPadding(dp(20), dp(2), dp(20), dp(6));
        searchStatus.setVisibility(GONE);
        addView(searchStatus, new LinearLayout.LayoutParams(-1, -2));

        refresher = new PullRefreshLayout(context);
        shelves = new RecyclerView(context);
        shelves.setLayoutManager(new LinearLayoutManager(context));
        shelves.setClipChildren(false);
        shelves.setClipToPadding(false);
        shelves.setPadding(dp(20), dp(6), dp(20), dp(20));
        shelfAdapter = new ShelfAdapter();
        shelves.setAdapter(shelfAdapter);
        refresher.addView(shelves, new PullRefreshLayout.LayoutParams(-1, -1));
        refresher.setRefreshTarget(shelves, 0);
        refresher.setColorSchemeColors(Theme.RED);
        refresher.setOnRefreshListener(() -> load(false));
        addView(refresher, new LinearLayout.LayoutParams(-1, 0, 1));

        books = BookShelfIndex.parse(shelfCache.read());
        initialLoadPending = books.isEmpty();
        render();
        load(true);
    }

    private void load(boolean quiet) {
        if (disposed) return;
        removeCallbacks(searchRequest);
        long requestGeneration = searchState.invalidate();
        shelfFailed = false;
        if (!quiet) refresher.setRefreshing(true);
        String requestIdentity = auth.libraryCacheIdentity();
        if (!shelfCache.matches(requestIdentity)) {
            shelfCache = new BookShelfCache(getContext(), requestIdentity);
            books = BookShelfIndex.parse(shelfCache.read());
            initialLoadPending = books.isEmpty();
            selectedFilter = BookShelfSearch.ALL;
        }
        render();
        BookShelfCache requestCache = shelfCache;
        String bearer = auth.bearer();
        io.execute(() -> {
            String raw = null;
            try {
                HttpClient.Response response = new HttpClient().get(
                        Api.publicWebBase() + "/books/?format=json", bearer);
                if (response.ok()) raw = response.text();
            } catch (Exception ignored) {}
            String result = raw;
            post(() -> {
                if (disposed || !searchState.isCurrent(requestGeneration)) return;
                if (!requestCache.matches(auth.libraryCacheIdentity())) {
                    refreshForCurrentAccount();
                    return;
                }
                shelfCache = requestCache;
                if (result != null) {
                    requestCache.store(result);
                    books = BookShelfIndex.parse(result);
                }
                shelfFailed = result == null;
                initialLoadPending = false;
                render();
                refresher.setRefreshing(false);
                scheduleSearch();
            });
        });
    }

    /** Called by the hosting activity after login, logout, or an account import. */
    public void refreshForCurrentAccount() {
        load(true);
    }

    private void scheduleSearch() {
        removeCallbacks(searchRequest);
        if (disposed || BookShelfSearch.query(query).isEmpty() || searchState.index() != null
                || searchState.loading()) return;
        postDelayed(searchRequest, Math.max(300, searchState.retryDelay(SystemClock.uptimeMillis())));
    }

    private void loadSearchIndex() {
        if (disposed || BookShelfSearch.query(query).isEmpty()) return;
        if (!shelfCache.matches(auth.libraryCacheIdentity())) {
            refreshForCurrentAccount();
            return;
        }
        long request = searchState.begin(SystemClock.uptimeMillis());
        if (request < 0) return;
        String requestIdentity = auth.libraryCacheIdentity();
        String bearer = auth.bearer();
        render();
        io.execute(() -> {
            Map<String, BookShelfSearch.Entry> parsed = null;
            try {
                HttpClient.Response response = new HttpClient().get(
                        Api.publicWebBase() + "/books/?format=search", bearer);
                if (response.ok()) parsed = BookShelfSearch.parse(response.text());
            } catch (Exception ignored) {}
            Map<String, BookShelfSearch.Entry> result = parsed;
            post(() -> {
                if (disposed || !searchState.isCurrent(request)) return;
                if (!requestIdentity.equals(auth.libraryCacheIdentity())) {
                    refreshForCurrentAccount();
                    return;
                }
                if (searchState.complete(request, result, SystemClock.uptimeMillis())) render();
            });
        });
    }

    private void render() {
        List<String> filters = BookShelfSearch.filters(books);
        if (!filters.contains(selectedFilter)) selectedFilter = BookShelfSearch.ALL;
        if (!searching) buildFilters(filters);
        visible = BookShelfSearch.select(books, selectedFilter, query, searchState.index());
        String status = !hasQuery() ? "" : searchState.loading() ? "正在翻章节…"
                : searchState.failed() ? "章节暂时加载失败，已按书名、作者搜索；继续输入或下拉刷新重试" : "";
        searchStatus.setText(I18n.text(getContext(), status));
        searchStatus.setVisibility(status.isEmpty() ? GONE : VISIBLE);
        coverLoader.cancelAll();
        shelfAdapter.notifyDataSetChanged();
    }

    private boolean hasQuery() { return !BookShelfSearch.query(query).isEmpty(); }

    private void buildFilters(List<String> filters) {
        filterRow.removeAllViews();
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(getContext());
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        for (String filter : filters) {
            boolean active = filter.equals(selectedFilter);
            TextView tab = text(filter, 15, active ? Theme.INK : Theme.SECONDARY,
                    active ? Typeface.BOLD : Typeface.NORMAL, false);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, 0, dp(18), 0);
            tab.setSelected(active);
            tab.setOnClickListener(v -> {
                selectedFilter = filter;
                render();
                shelves.scrollToPosition(0);
            });
            tabs.addView(tab, new LinearLayout.LayoutParams(-2, dp(44)));
        }
        scroll.addView(tabs);
        filterRow.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        TextView search = text("⌕", 24, Theme.SECONDARY, Typeface.NORMAL, false);
        search.setGravity(Gravity.CENTER);
        search.setContentDescription(I18n.text(getContext(), "搜索书架"));
        search.setOnClickListener(v -> showSearch());
        filterRow.addView(search, new LinearLayout.LayoutParams(dp(40), dp(40)));
        // Rebuilding the dynamic category list must not scroll a selected trailing tab out of sight.
        scroll.post(() -> {
            int index = filters.indexOf(selectedFilter);
            View active = tabs.getChildAt(index);
            if (active != null) scroll.scrollTo(Math.max(0, active.getRight() - scroll.getWidth()), 0);
        });
    }

    private void showSearch() {
        searching = true;
        filterRow.removeAllViews();
        EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setTextColor(Theme.INK);
        input.setHintTextColor(Theme.SECONDARY);
        input.setHint(I18n.text(getContext(), "搜书名、作者、章节"));
        input.setContentDescription(I18n.text(getContext(), "搜书名、作者、章节"));
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        // Reserve room for the clear control inside the rounded input background.
        input.setPadding(dp(12), 0, dp(44), 0);
        GradientDrawable background = round(Theme.CARD, 20);
        background.setStroke(dp(1), 0xffded5c8);
        input.setBackground(background);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                render();
                shelves.scrollToPosition(0);
                scheduleSearch();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        FrameLayout searchField = new FrameLayout(getContext());
        searchField.addView(input, new FrameLayout.LayoutParams(-1, dp(36), Gravity.CENTER_VERTICAL));
        TextView clear = text("×", 22, Theme.SECONDARY, Typeface.NORMAL, false);
        clear.setGravity(Gravity.CENTER);
        clear.setContentDescription(I18n.text(getContext(), "清空"));
        clear.setOnClickListener(v -> input.setText(""));
        searchField.addView(clear, new FrameLayout.LayoutParams(dp(40), dp(44), Gravity.END | Gravity.CENTER_VERTICAL));
        filterRow.addView(searchField, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView cancel = text("取消", 14, Theme.INK, Typeface.NORMAL, false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> {
            InputMethodManager keyboard = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.clearFocus();
            searching = false;
            query = "";
            removeCallbacks(searchRequest);
            render();
        });
        filterRow.addView(cancel, new LinearLayout.LayoutParams(dp(52), dp(40)));
        input.requestFocus();
        input.post(() -> {
            InputMethodManager keyboard = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null && searching && !disposed) keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private String emptyHint() {
        if (!visible.isEmpty()) return null;
        if (shelfFailed && books.isEmpty()) return "书架没加载出来，下拉刷新重试";
        if (hasQuery()) return searchState.loading() ? "正在翻章节…"
                : I18n.format(getContext(), "没有找到「%s」", BookShelfSearch.query(query));
        if (BookShelfSearch.MINE.equals(selectedFilter)) return !auth.bearer().isEmpty()
                ? "还没有你的书，点「写书」开始" : "登录后这里是你写的书";
        return "书架还没有书，点「写书」开始";
    }

    private int cellCount() { return visible.size() + (hasQuery() ? 0 : 1); }
    private int shelfRowCount() { return (cellCount() + 1) / 2; }

    private LinearLayout.LayoutParams weightedCellParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.leftMargin = leftMargin;
        return params;
    }

    /** RecyclerView gives the shelf iOS-style lazy rows: only nearby covers get a view or load. */
    private final class ShelfAdapter extends RecyclerView.Adapter<ShelfRowHolder> {
        private static final int TYPE_LOADING = 0;
        private static final int TYPE_SHELF_ROW = 1;
        private static final int TYPE_EMPTY = 2;

        @Override public int getItemViewType(int position) {
            return BookShelfLoadingPolicy.shouldShowExclusiveLoading(initialLoadPending, books.size())
                    ? TYPE_LOADING : position >= shelfRowCount() ? TYPE_EMPTY : TYPE_SHELF_ROW;
        }

        @Override public ShelfRowHolder onCreateViewHolder(android.view.ViewGroup parent,
                                                            int viewType) {
            if (viewType == TYPE_LOADING || viewType == TYPE_EMPTY) {
                TextView loading = text("正在整理书架…", 14, Theme.SECONDARY, Typeface.NORMAL, false);
                loading.setGravity(Gravity.CENTER);
                loading.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(220)));
                return new ShelfRowHolder(loading);
            }
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(VERTICAL);
            item.setClipChildren(false);
            item.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new ShelfRowHolder(item);
        }

        @Override public void onBindViewHolder(ShelfRowHolder holder, int position) {
            if (getItemViewType(position) == TYPE_EMPTY) {
                ((TextView) holder.itemView).setText(I18n.text(getContext(), emptyHint()));
                return;
            }
            if (getItemViewType(position) != TYPE_SHELF_ROW) return;
            LinearLayout item = (LinearLayout) holder.itemView;
            item.removeAllViews();
            int firstCell = position * 2;
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setClipChildren(false);
            row.addView(cellAt(firstCell), weightedCellParams(0));
            if (firstCell + 1 < cellCount()) {
                row.addView(cellAt(firstCell + 1), weightedCellParams(dp(22)));
            } else {
                row.addView(new View(getContext()), weightedCellParams(dp(22)));
            }
            item.addView(row, new LinearLayout.LayoutParams(-1, -2));
            item.addView(shelfBar(), shelfParams());
        }

        @Override public int getItemCount() {
            if (BookShelfLoadingPolicy.shouldShowExclusiveLoading(initialLoadPending, books.size())) {
                return 1;
            }
            return shelfRowCount() + (emptyHint() == null ? 0 : 1);
        }
    }

    private View cellAt(int index) {
        if (!hasQuery() && index == 0) return writeCell();
        BookShelfSearch.Match match = visible.get(index - (hasQuery() ? 0 : 1));
        return bookCell(match.book, match.chapter);
    }

    private static final class ShelfRowHolder extends RecyclerView.ViewHolder {
        ShelfRowHolder(View itemView) { super(itemView); }
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

    private View bookCell(BookShelfIndex.Book book, String chapterHit) {
        LinearLayout cell = cellContainer();
        cell.setOnClickListener(v -> {
            if (getContext() instanceof Activity) {
                BookReaderActivity.open((Activity) getContext(), book);
            }
        });

        PhysicalBookCover cover = new PhysicalBookCover(getContext(), color(book.c), color(book.c2));
        cover.setElevation(dp(7));
        addBookTypography(cover, book);
        if (book.cover) {
            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.addView(image, new FrameLayout.LayoutParams(-1, -1));
            coverLoader.load(book, book.coverUrl(Api.publicWebBase()), image);
        }
        if (book.hidden) addHiddenBadge(cover);
        cell.addView(cover, new LinearLayout.LayoutParams(-1, -2));
        String meta = book.chapters > 0 ? I18n.format(getContext(), "%d 章", book.chapters) : book.sub;
        if (!book.category.isEmpty()) meta = (meta == null || meta.isEmpty() ? "" : meta + " · ") + I18n.text(getContext(), book.category);
        cell.addView(caption(book.main, meta == null || meta.isEmpty() ? " " : meta), captionParams());
        if (!chapterHit.isEmpty()) {
            TextView hit = text(I18n.format(getContext(), "章节：%s", chapterHit), 12, Theme.SECONDARY, Typeface.NORMAL, false);
            hit.setMaxLines(2);
            hit.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(hit, new LinearLayout.LayoutParams(-1, -2));
        }
        return cell;
    }

    private void addBookTypography(FrameLayout cover, BookShelfIndex.Book book) {
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

    private void addHiddenBadge(FrameLayout cover) {
        TextView badge = text("隐藏", 10, Color.WHITE, Typeface.BOLD, false);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(7), dp(3), dp(7), dp(3));
        badge.setBackground(round(0x8c000000, 12));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                -2, -2, Gravity.TOP | Gravity.END);
        params.setMargins(dp(6), dp(6), dp(6), dp(6));
        cover.addView(badge, params);
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
        params.topMargin = dp(6);
        params.bottomMargin = dp(22);
        return params;
    }

    private TextView text(String value, int size, int color, int style, boolean serif) {
        TextView view = new TextView(getContext());
        view.setText(I18n.text(getContext(), value));
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
        disposed = true;
        removeCallbacks(searchRequest);
        searchState.invalidate();
        coverLoader.cancelAll();
        coverLoader.shutdown();
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
