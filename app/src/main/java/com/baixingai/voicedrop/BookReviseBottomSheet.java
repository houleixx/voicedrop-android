package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.core.BookReviseResult;
import com.baixingai.voicedrop.core.BookReviseThread;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.RemixIconGlyph;
import com.baixingai.voicedrop.ui.RemixIconView;
import com.baixingai.voicedrop.ui.Theme;
import com.umeng.analytics.MobclickAgent;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native conversation UI for the permanent owner-only book revision thread. */
public final class BookReviseBottomSheet {
    static final String HISTORY_API = "https://lab.jianshuo.dev/api/book/history";
    static final String REVISE_API = "https://lab.jianshuo.dev/api/book/revise";
    static final long POLL_INTERVAL_MS = 6_000L;
    static final int DISPLAY_PRICE = 40;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BookReviseThread.Entry> entries = new ArrayList<>();
    private final Runnable poll = () -> loadHistory(false);
    private final Activity activity;
    private final String slug;
    private final Runnable onClosed;

    private IosDialog dialog;
    private ScrollView scroll;
    private LinearLayout threadList;
    private ProgressBar loading;
    private View denied;
    private TextView deniedMessage;
    private TextView error;
    private View composer;
    private EditText input;
    private TextView send;
    private TextView hint;
    private boolean running;
    private boolean sending;
    private boolean loadingHistory;
    private boolean started;
    private boolean destroyed;

    public static BookReviseBottomSheet show(Activity source, String slug, String title,
                                             Runnable onClosed) {
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return null;
        BookReviseBottomSheet sheet = new BookReviseBottomSheet(source, slug, onClosed);
        sheet.present(title);
        return sheet;
    }

    private BookReviseBottomSheet(Activity activity, String slug, Runnable onClosed) {
        this.activity = activity;
        this.slug = slug;
        this.onClosed = onClosed;
    }

    private void present(String title) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);

        FrameLayout content = new FrameLayout(activity);
        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        threadList = new LinearLayout(activity);
        threadList.setOrientation(LinearLayout.VERTICAL);
        threadList.setPadding(dp(18), dp(6), dp(18), dp(12));
        scroll.addView(threadList, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(activity);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
                dp(36), dp(36), Gravity.CENTER);
        loading.setTranslationY(-dp(180));
        content.addView(loading, loadingParams);

        LinearLayout deniedContent = new LinearLayout(activity);
        deniedContent.setOrientation(LinearLayout.VERTICAL);
        deniedContent.setGravity(Gravity.CENTER);
        deniedContent.setPadding(dp(36), dp(30), dp(36), dp(30));
        deniedContent.setTranslationY(-dp(180));
        deniedContent.setVisibility(View.GONE);
        RemixIconView deniedIcon = new RemixIconView(activity);
        deniedIcon.setIcon(RemixIconGlyph.LOCK);
        deniedIcon.setTextSize(42);
        deniedIcon.setTextColor(Theme.SECONDARY);
        deniedContent.addView(deniedIcon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        deniedMessage = text("", 14, Theme.SECONDARY, Typeface.NORMAL);
        deniedMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams deniedMessageParams = new LinearLayout.LayoutParams(-1, -2);
        deniedMessageParams.topMargin = dp(18);
        deniedContent.addView(deniedMessage, deniedMessageParams);
        denied = deniedContent;
        content.addView(denied, new FrameLayout.LayoutParams(-1, -1));
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        composer = buildInputBar();
        composer.setVisibility(View.GONE);
        page.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        int screenHeightDp = Math.round(activity.getResources().getDisplayMetrics().heightPixels
                / activity.getResources().getDisplayMetrics().density);
        int contentHeightDp = Math.max(360, Math.round(screenHeightDp * 0.84f) - 74);
        dialog = IosDialog.showBottomSheet(activity, "修改《" + safeTitle(title) + "》", page,
                contentHeightDp, null, null, null, null, true, true);
        dialog.setOnDismissListener(ignored -> destroy());
        started = true;
        loadHistory(true);
        track("修改书");
    }

    private View buildInputBar() {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(12));
        bar.setBackground(stroked(Theme.BG, 0, Theme.BORDER_CHROME, 1));

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.BOTTOM);
        input = new EditText(activity);
        input.setHint("想怎么改这本书？比如：第三章开头太啰嗦，删一半");
        input.setTextSize(15);
        input.setTextColor(Theme.INK);
        input.setHintTextColor(Theme.FAINT);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setPadding(dp(13), dp(10), dp(13), dp(10));
        input.setBackground(stroked(Theme.CARD, 9, Theme.ACCENT, 1));
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        send = text("↑", 23, 0xffffffff, Typeface.BOLD);
        send.setGravity(Gravity.CENTER);
        send.setContentDescription("提交修改");
        send.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        sendParams.leftMargin = dp(10);
        row.addView(send, sendParams);
        bar.addView(row, new LinearLayout.LayoutParams(-1, -2));

        hint = text("", 12, Theme.SECONDARY, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(7);
        bar.addView(hint, hintParams);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateComposer(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        updateComposer();
        return bar;
    }

    private void loadHistory(boolean showSpinner) {
        if (!started || destroyed || loadingHistory || activity.isFinishing()
                || activity.isDestroyed()) return;
        loadingHistory = true;
        if (showSpinner) loading.setVisibility(View.VISIBLE);
        String bearer = new AuthStore(activity).bearer();
        io.execute(() -> {
            HttpClient.Response response = null;
            try {
                String encoded = URLEncoder.encode(slug, StandardCharsets.UTF_8.name());
                response = new HttpClient().get(HISTORY_API + "?slug=" + encoded, bearer,
                        new HttpClient.RequestOptions().readTimeoutMs(20_000));
            } catch (Exception ignored) {}
            HttpClient.Response finalResponse = response;
            activity.runOnUiThread(() -> applyHistory(finalResponse));
        });
    }

    private void applyHistory(HttpClient.Response response) {
        loadingHistory = false;
        if (!started || destroyed) return;
        loading.setVisibility(View.GONE);
        handler.removeCallbacks(poll);
        if (response == null) {
            if (entries.isEmpty()) showInlineError("没连上服务器，重开再试。");
        } else if (response.code == 200) {
            BookReviseThread thread = BookReviseThread.parse(response.text());
            entries.clear();
            entries.addAll(thread.entries);
            running = thread.running;
            denied.setVisibility(View.GONE);
            scroll.setVisibility(View.VISIBLE);
            composer.setVisibility(View.VISIBLE);
            input.setEnabled(true);
            renderThread(null);
        } else if (response.code == 401) {
            showDenied("登录状态不对，重启 App 再试。");
        } else if (response.code == 403) {
            showDenied("只有这本书的主人能修改。");
        } else if (response.code == 404) {
            showDenied("这本书是早期写的，还没登记主人，暂时不能在线修改。");
        } else if (entries.isEmpty()) {
            showInlineError("服务器返回 " + response.code + "，稍后再试。");
        }
        updateComposer();
        if (running && started && !destroyed && !activity.isFinishing()) {
            handler.postDelayed(poll, POLL_INTERVAL_MS);
        }
    }

    private void submit() {
        String instruction = input.getText().toString().trim();
        if (instruction.isEmpty() || sending || running) return;
        sending = true;
        updateComposer();
        hideKeyboard();
        renderThread(null);
        track("修书发起");
        String bearer = new AuthStore(activity).bearer();
        io.execute(() -> {
            BookReviseResult result = BookReviseResult.from(0, "");
            try {
                byte[] body = new JSONObject().put("slug", slug).put("instruction", instruction)
                        .toString().getBytes(StandardCharsets.UTF_8);
                HttpClient.Response response = new HttpClient().postJson(REVISE_API, bearer, body,
                        new HttpClient.RequestOptions().readTimeoutMs(30_000));
                result = BookReviseResult.from(response.code, response.text());
            } catch (Exception ignored) {}
            BookReviseResult finalResult = result;
            activity.runOnUiThread(() -> applySubmit(finalResult, instruction));
        });
    }

    private void applySubmit(BookReviseResult result, String instruction) {
        if (destroyed) return;
        sending = false;
        if (result.accepted) {
            input.setText("");
            double timestamp = result.timestampMs > 0 ? result.timestampMs : System.currentTimeMillis();
            entries.add(new BookReviseThread.Entry(timestamp, "revise", instruction,
                    "running", null, null));
            running = true;
            renderThread(null);
            track("修书已受理");
            handler.removeCallbacks(poll);
            if (started && !destroyed) handler.postDelayed(poll, POLL_INTERVAL_MS);
        } else {
            renderThread(result.message);
            if (result.code == 409) loadHistory(false);
        }
        updateComposer();
    }

    private void renderThread(String errorMessage) {
        threadList.removeAllViews();
        for (BookReviseThread.Entry entry : entries) addEntry(entry);
        error = text(errorMessage == null ? "" : errorMessage, 13, Theme.RED, Typeface.NORMAL);
        error.setVisibility(errorMessage == null ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        threadList.addView(error, params);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addEntry(BookReviseThread.Entry entry) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(entry.creation() ? "开书种子" : "修改指令", 11, Theme.SECONDARY, Typeface.NORMAL);
        label.setGravity(Gravity.RIGHT);
        block.addView(label, new LinearLayout.LayoutParams(-1, -2));

        TextView instruction = text(entry.instruction, 15, 0xffffffff, Typeface.NORMAL);
        instruction.setPadding(dp(14), dp(10), dp(14), dp(10));
        instruction.setBackground(stroked(Theme.ACCENT, 9, Theme.ACCENT, 0));
        instruction.setMaxWidth(activity.getResources().getDisplayMetrics().widthPixels * 4 / 5);
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(-2, -2);
        instructionParams.gravity = Gravity.RIGHT;
        instructionParams.topMargin = dp(3);
        block.addView(instruction, instructionParams);

        TextView stamp = text(stamp(entry.timestampMs), 11, Theme.FAINT, Typeface.NORMAL);
        stamp.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams stampParams = new LinearLayout.LayoutParams(-1, -2);
        stampParams.topMargin = dp(3);
        block.addView(stamp, stampParams);

        String replyText;
        int replyColor = Theme.INK;
        if (entry.running()) {
            replyText = entry.creation() ? "正在写这本书…" : "正在修改…（改完这里会出现修改说明）";
            replyColor = Theme.SECONDARY;
        } else if (entry.failed()) {
            replyText = entry.creation() ? "这本书当时没写完就中断了" : "这次修改没有完成（没扣的算力不会少），可以再试一次";
            replyColor = Theme.RED;
        } else if (entry.reply != null) {
            replyText = entry.reply;
        } else {
            replyText = entry.creation() ? "书写好了，上架在「写书」书架。" : "改好了。";
            replyColor = Theme.SECONDARY;
        }
        TextView reply = text(replyText, entry.reply == null ? 14 : 15, replyColor, Typeface.NORMAL);
        reply.setPadding(dp(14), dp(10), dp(14), dp(10));
        reply.setBackground(stroked(Theme.CARD, 9, Theme.BORDER_CHROME, 1));
        reply.setMaxWidth(activity.getResources().getDisplayMetrics().widthPixels * 4 / 5);
        LinearLayout.LayoutParams replyParams = new LinearLayout.LayoutParams(-2, -2);
        replyParams.gravity = Gravity.LEFT;
        replyParams.topMargin = dp(8);
        block.addView(reply, replyParams);

        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
        blockParams.bottomMargin = dp(14);
        threadList.addView(block, blockParams);
    }

    private void showDenied(String message) {
        running = false;
        scroll.setVisibility(View.GONE);
        deniedMessage.setText(message);
        denied.setVisibility(View.VISIBLE);
        composer.setVisibility(View.GONE);
        input.setEnabled(false);
    }

    private void showInlineError(String message) {
        denied.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        renderThread(message);
    }

    private void updateComposer() {
        boolean canSend = input != null && input.getText().toString().trim().length() > 0
                && !sending && !running && denied.getVisibility() != View.VISIBLE;
        if (send != null) {
            send.setEnabled(canSend);
            send.setBackground(circle(canSend ? Theme.ACCENT : Theme.FAINT));
            send.setAlpha(canSend ? 1f : 0.75f);
        }
        if (hint != null) hint.setText(running
                ? "有一个修改正在进行，等它改完再提下一个"
                : "每次修改 " + DISPLAY_PRICE + " 算力 · 提交后可以关掉，改完这里会留下修改说明");
    }

    private void track(String event) {
        MobclickAgent.onEvent(activity, event);
    }

    private void hideKeyboard() {
        input.clearFocus();
        InputMethodManager manager = (InputMethodManager) activity.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private static String stamp(double timestampMs) {
        if (!Double.isFinite(timestampMs) || timestampMs <= 0) return "";
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date((long) timestampMs));
    }

    private String safeTitle(String value) {
        return value == null || value.trim().isEmpty() ? "未命名" : value.trim();
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private GradientDrawable stroked(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    public void onHostStart() {
        if (destroyed || dialog == null || !dialog.isShowing()) return;
        started = true;
        loadHistory(entries.isEmpty());
    }

    public void onHostStop() {
        started = false;
        handler.removeCallbacks(poll);
    }

    private void destroy() {
        if (destroyed) return;
        destroyed = true;
        started = false;
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (onClosed != null) onClosed.run();
    }
}
