package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.baixingai.voicedrop.core.BookWritingResult;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.ReferralManager;
import com.baixingai.voicedrop.data.UsageStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.ui.WechatShareLoadingDialog;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android counterpart of iOS BookWritingSheet. */
public final class BookWritingActivity extends Activity {
    static final String API = "https://lab.jianshuo.dev/api/book";
    static final String SHELF = "https://voicedrop.cn/books/";
    static final int SHELF_ICON_RES_ID = R.drawable.ic_about_books_vertical;
    static final int POWER_ICON_RES_ID = R.drawable.ic_settings_bolt;
    private static final int PRICE = 320;
    private static final int META = 0xffa89e8e;
    private static final int SECTION = 0xffa79f93;
    private static final int AMBER = 0xffc98a2e;
    private static final int AMBER_SOFT = 0xfffbead2;
    private static final int DIVIDER = 0xfff0e8da;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private LinearLayout bottomBar;
    private EditText seed;
    private TextView submit;
    private TextView status;
    private WechatShareLoadingDialog submitLoading;
    private boolean sending;
    private boolean submitted;
    private Double balance;
    private ReferralManager.InviteLink invite;

    /** Opens with the same horizontal page transition used by detail screens. */
    public static void open(Activity source) {
        source.startActivity(new Intent(source, BookWritingActivity.class));
        source.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomBar = buildBottomBar();
        page.addView(bottomBar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        renderForm("");
        loadWritingContext();
    }

    private View buildHeader() {
        return new PageTitleBar(this, "写书", this::finishWithPageTransition);
    }

    private LinearLayout buildBottomBar() {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackground(topStrokeBackground(Theme.BG, Theme.BORDER_CHROME));
        SystemBarDefaults.applyBottomInsets(bottom, dp(18), dp(12), dp(18), dp(10));
        submit = text("开始写书 · 320 算力", 16, 0xffffffff, Typeface.BOLD);
        submit.setGravity(Gravity.CENTER);
        submit.setOnClickListener(v -> startBook());
        bottom.addView(submit, new LinearLayout.LayoutParams(-1, dp(50)));
        TextView hint = text("提交后就可以关 App · 10–30 分钟写完，出现在「写书」书架", 13, META, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(9);
        bottom.addView(hint, hintParams);
        return bottom;
    }

    private void renderForm(String preservedSeed) {
        content.removeAllViews();
        content.setGravity(Gravity.NO_GRAVITY);
        content.addView(priceHero(), matchWrap());
        if (balance != null && balance < PRICE) content.addView(earnSection(), topMargin(dp(18)));
        content.addView(seedSection(preservedSeed), topMargin(dp(18)));
        content.addView(pipelineSection(), topMargin(dp(18)));
        status = text("", 13, Theme.RED, Typeface.NORMAL);
        status.setVisibility(View.GONE);
        content.addView(status, topMargin(dp(12)));
        updateSubmitState();
    }

    private View priceHero() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundWithStroke(AMBER_SOFT, 8, 0xffebd9b8, 1));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout priceLine = new LinearLayout(this);
        priceLine.setGravity(Gravity.BOTTOM);
        ImageView powerIcon = new ImageView(this);
        powerIcon.setImageResource(POWER_ICON_RES_ID);
        powerIcon.setColorFilter(Theme.AMBER);
        powerIcon.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams powerIconParams = new LinearLayout.LayoutParams(dp(24), dp(40));
        powerIconParams.bottomMargin = dp(1);
        priceLine.addView(powerIcon, powerIconParams);
        TextView price = text("320", 34, Theme.INK, Typeface.BOLD);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(-2, -2);
        priceParams.leftMargin = dp(4);
        priceLine.addView(price, priceParams);
        TextView unit = text("算力", 15, Theme.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(-2, -2);
        unitParams.leftMargin = dp(4);
        unitParams.bottomMargin = dp(4);
        priceLine.addView(unit, unitParams);
        left.addView(priceLine);
        left.addView(text("写一本书的价钱，提交时一次扣清", 13, META, Typeface.NORMAL));
        card.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT);
        if (balance == null) {
            TextView loading = text("加载中…", 13, Theme.FAINT, Typeface.NORMAL);
            loading.setGravity(Gravity.RIGHT);
            right.addView(loading);
        } else {
            TextView value = text(format(balance), 24, balance >= PRICE ? Theme.GREEN : Theme.RED, Typeface.BOLD);
            value.setGravity(Gravity.RIGHT);
            right.addView(value);
            TextView label = text("你现在的算力", 13, META, Typeface.NORMAL);
            label.setGravity(Gravity.RIGHT);
            right.addView(label);
        }
        card.addView(right, new LinearLayout.LayoutParams(-2, -2));
        return card;
    }

    private View seedSection(String preservedSeed) {
        LinearLayout section = vertical();
        section.addView(sectionLabel("中心思想"));
        TextView intro = text("一句话说清这本书要讲明白的那一个问题或主张。想法越聚焦，书越好看；也可以贴一整篇文章当种子。", 13, Theme.SECONDARY, Typeface.NORMAL);
        intro.setLineSpacing(dp(2), 1f);
        section.addView(intro, topMargin(dp(8)));
        seed = new EditText(this);
        seed.setGravity(Gravity.TOP | Gravity.LEFT);
        seed.setTextSize(16);
        seed.setTextColor(Theme.INK);
        seed.setHintTextColor(Theme.FAINT);
        String placeholder = "比如：为什么一切都在变乱？\n或：钱不脏，是我一直躲着它。";
        SpannableString hint = new SpannableString(placeholder);
        hint.setSpan(new AbsoluteSizeSpan(14, true), 0, hint.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        seed.setHint(hint);
        seed.setPadding(dp(20), dp(18), dp(20), dp(18));
        seed.setBackground(roundWithStroke(Theme.CARD, 8, Theme.ACCENT, 2));
        seed.setText(preservedSeed);
        seed.setSelection(seed.length());
        seed.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { updateSubmitState(); }
            @Override public void afterTextChanged(Editable value) {}
        });
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, dp(150));
        editorParams.topMargin = dp(8);
        section.addView(seed, editorParams);
        return section;
    }

    private View pipelineSection() {
        LinearLayout section = vertical();
        section.addView(sectionLabel("怎么写成"));
        LinearLayout card = vertical();
        card.setBackground(roundWithStroke(Theme.CARD, 8, Theme.BORDER_CHROME, 1));
        card.addView(pipelineRow("1", "拆大纲", "AI 建筑师把中心思想拆成一环扣一环的章节"));
        card.addView(divider());
        card.addView(pipelineRow("2", "并行写", "每章一个写手，费曼式大白话，名词当场讲人话"));
        card.addView(divider());
        card.addView(pipelineRow("3", "独立评审", "另一个 AI 只看成稿挑错，不过就打回重写"));
        card.addView(divider());
        card.addView(pipelineRow("4", "上你的架", "过一章发一章到「写书」书架，署你的名字（设置里的「名字」）"));
        section.addView(card, topMargin(dp(8)));
        return section;
    }

    private View pipelineRow(String number, String titleValue, String copy) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView numberView = text(number, 15, Theme.ACCENT, Typeface.BOLD);
        numberView.setGravity(Gravity.CENTER);
        numberView.setBackground(round(Theme.ACCENT_SOFT, 8));
        row.addView(numberView, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout labels = vertical();
        TextView title = text(titleValue, 15, Theme.INK, Typeface.BOLD);
        title.setLineSpacing(dp(1), 1f);
        labels.addView(title);
        TextView subtitle = text(copy, 13, Theme.SECONDARY, Typeface.NORMAL);
        subtitle.setLineSpacing(dp(2), 1f);
        labels.addView(subtitle, topMargin(dp(3)));
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelsParams.leftMargin = dp(12);
        row.addView(labels, labelsParams);
        return row;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dpF(1));
        params.setMargins(dp(58), 0, dp(14), 0);
        view.setLayoutParams(params);
        return view;
    }

    private View earnSection() {
        LinearLayout section = vertical();
        section.addView(sectionLabel("算力不够？"));
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(roundWithStroke(Theme.CARD, 8, Theme.BORDER_CHROME, 1));
        double gap = Math.max(0, PRICE - balance);
        card.addView(text("还差 " + format(gap) + " 算力，两条来路：", 14, Theme.INK, Typeface.BOLD));
        int feed = invite == null ? 0 : invite.suanliFeedAuthor;
        int invited = invite == null ? 0 : invite.suanliInviter;
        card.addView(earnRow(R.drawable.ic_settings_bolt, Theme.AMBER, Theme.AMBER_BG,
                feed > 0 ? "请朋友给你的文章「加油」——一次约得 " + feed + " 算力" : "请朋友给你的文章「加油」——作者每次都得算力",
                "把文章分享到 VD社区或发给朋友，读的人点「加油」你就进账"), topMargin(dp(14)));
        card.addView(earnRow(R.drawable.ic_settings_community, Theme.ACCENT, Theme.ACCENT_SOFT,
                invited > 0 ? "邀请朋友装 VoiceDrop——装一个约得 " + invited + " 算力" : "邀请朋友装 VoiceDrop——每装一个你都得算力",
                "朋友通过你的链接安装，双方都到账"), topMargin(dp(14)));
        if (invite != null && invite.url != null && !invite.url.isEmpty()) {
            TextView share = text("把邀请链接发给朋友", 15, 0xffffffff, Typeface.BOLD);
            share.setGravity(Gravity.CENTER);
            share.setBackground(round(Theme.ACCENT, 8));
            share.setOnClickListener(v -> shareInvite());
            LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(-1, dp(44));
            shareParams.topMargin = dp(16);
            card.addView(share, shareParams);
        }
        section.addView(card, topMargin(dp(8)));
        return section;
    }

    private View earnRow(int iconResId, int iconColor, int tileColor, String titleValue, String copy) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setColorFilter(iconColor);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(round(tileColor, 8));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout labels = vertical();
        TextView title = text(titleValue, 14, Theme.INK, Typeface.BOLD);
        title.setLineSpacing(dp(1), 1f);
        labels.addView(title);
        TextView subtitle = text(copy, 12, Theme.SECONDARY, Typeface.NORMAL);
        subtitle.setLineSpacing(dp(2), 1f);
        labels.addView(subtitle, topMargin(dp(3)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = dp(12);
        row.addView(labels, params);
        return row;
    }

    private TextView sectionLabel(String value) {
        TextView label = text(value, 12, SECTION, Typeface.BOLD);
        label.setLetterSpacing(0.16f);
        return label;
    }

    private void loadWritingContext() {
        io.execute(() -> {
            Double loadedBalance = null;
            ReferralManager.InviteLink link = null;
            try { loadedBalance = new UsageStore(new AuthStore(this), new HttpClient()).balance().suanli; } catch (Exception ignored) {}
            if (loadedBalance != null && loadedBalance < PRICE) {
                try { link = new ReferralManager(this).inviteLink(); } catch (Exception ignored) {}
            }
            Double finalBalance = loadedBalance;
            ReferralManager.InviteLink finalLink = link;
            runOnUiThread(() -> {
                balance = finalBalance;
                invite = finalLink;
                String old = seed == null ? "" : seed.getText().toString();
                renderForm(old);
            });
        });
    }

    private void shareInvite() {
        if (invite == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, invite.url);
        startActivity(Intent.createChooser(intent, "分享邀请链接"));
    }

    private void startBook() {
        String value = seed.getText().toString().trim();
        if (value.isEmpty() || sending || submitted) return;
        sending = true;
        showStatus(null);
        updateSubmitState();
        hideKeyboard();
        showSubmitLoading();
        io.execute(() -> {
            BookWritingResult result = BookWritingResult.from(0, "");
            try {
                byte[] body = new JSONObject().put("seed", value).toString().getBytes(StandardCharsets.UTF_8);
                HttpClient.Response response = new HttpClient().postJson(API, new AuthStore(this).bearer(), body,
                        new HttpClient.RequestOptions().readTimeoutMs(30_000));
                result = BookWritingResult.from(response.code, response.text());
            } catch (Exception ignored) {}
            BookWritingResult finalResult = result;
            runOnUiThread(() -> showResult(finalResult));
        });
    }

    private void showResult(BookWritingResult result) {
        hideSubmitLoading();
        sending = false;
        if (result.accepted) {
            submitted = true;
            renderSubmitted();
        } else {
            if (result.balance != null) {
                balance = result.balance;
                String old = seed == null ? "" : seed.getText().toString();
                renderForm(old);
            }
            showStatus(result.message);
        }
        updateSubmitState();
    }

    private void renderSubmitted() {
        content.removeAllViews();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        bottomBar.setVisibility(View.GONE);
        ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_check_flat);
        check.setColorFilter(0xffffffff);
        check.setPadding(dp(10), dp(10), dp(10), dp(10));
        check.setBackground(round(Theme.GREEN, 24));
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        checkParams.topMargin = dp(24);
        content.addView(check, checkParams);
        content.addView(text("开始写了！", 17, Theme.INK, Typeface.BOLD), centeredTopMargin(dp(12)));
        TextView message = text("现在可以关掉 App。书通常 10–30 分钟写完，过稿一章、上架一章——写好就出现在「写书」书架上，下拉刷新就能看到。", 14, Theme.SECONDARY, Typeface.NORMAL);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(2), 1f);
        content.addView(message, centeredTopMargin(dp(10)));
        TextView done = text("好", 16, 0xffffffff, Typeface.BOLD);
        done.setGravity(Gravity.CENTER);
        done.setBackground(round(Theme.ACCENT, 8));
        done.setOnClickListener(v -> finishWithPageTransition());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(96), dp(48));
        doneParams.topMargin = dp(14);
        content.addView(done, doneParams);
    }

    private void updateSubmitState() {
        if (submit == null) return;
        boolean enabled = seed != null && !seed.getText().toString().trim().isEmpty()
                && !sending && !submitted && (balance == null || balance >= PRICE);
        submit.setEnabled(enabled);
        double gap = balance == null ? 0 : Math.max(0, PRICE - balance);
        submit.setText(sending ? "提交中…" : gap > 0 ? "算力不够 · 还差 " + format(gap) : "开始写书 · 320 算力");
        submit.setTextColor(0xffffffff);
        submit.setBackground(round(enabled ? Theme.ACCENT : Theme.FAINT, 8));
        submit.setElevation(enabled ? dp(5) : 0);
    }

    private void showStatus(String message) {
        if (status == null) return;
        status.setText(message == null ? "" : message);
        status.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showSubmitLoading() {
        hideSubmitLoading();
        submitLoading = WechatShareLoadingDialog.show(this, "提交中...");
    }

    private void hideSubmitLoading() {
        if (submitLoading == null) return;
        if (submitLoading.isShowing()) submitLoading.dismiss();
        submitLoading = null;
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }

    @Override public void onBackPressed() { finishWithPageTransition(); }
    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
    @Override protected void onDestroy() { hideSubmitLoading(); io.shutdownNow(); super.onDestroy(); }

    private String format(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? Long.toString(Math.round(rounded)) : Double.toString(rounded);
    }
    private LinearLayout vertical() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private GradientDrawable roundWithStroke(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = round(color, radius);
        drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }
    private android.graphics.drawable.Drawable topStrokeBackground(int color, int strokeColor) {
        return new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint();
            @Override public void draw(android.graphics.Canvas canvas) {
                paint.setColor(color);
                canvas.drawRect(getBounds(), paint);
                paint.setColor(strokeColor);
                canvas.drawRect(getBounds().left, getBounds().top,
                        getBounds().right, getBounds().top + dpF(1), paint);
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
        };
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams topMargin(int margin) { LinearLayout.LayoutParams params = matchWrap(); params.topMargin = margin; return params; }
    private LinearLayout.LayoutParams centeredTopMargin(int margin) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = margin; return params; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int dpF(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
