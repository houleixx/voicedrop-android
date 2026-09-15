package com.baixingai.voicedrop;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.UsageStore;
import com.baixingai.voicedrop.data.UsagePageLoader;
import com.baixingai.voicedrop.core.SubscriptionRefreshSession;
import com.baixingai.voicedrop.data.WechatLogin;
import com.baixingai.voicedrop.data.WechatSubscriptionStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.ui.WechatSubscriptionUi;
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram;
import com.tencent.mm.opensdk.modelbiz.WXOpenBusinessWebview;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UsageActivity extends Activity {
    private UsageStore usageStore;
    private WechatSubscriptionStore subscriptionStore;
    private LinearLayout content;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private UsageStore.Balance balance = new UsageStore.Balance(0, 0);
    private UsageStore.Summary summary = new UsageStore.Summary(new ArrayList<>(), new ArrayList<>());
    private final List<UsageStore.Entry> entries = new ArrayList<>();
    private String nextCursor = "";
    private boolean loadingMore;
    private boolean usageLoaded;
    private boolean usageRefreshing = true;
    private WechatSubscriptionStore.Status subscriptionStatus;
    private boolean subscriptionLoaded;
    private boolean subscriptionInProgress;
    private boolean refreshSubscriptionOnResume;
    private boolean watchingPayment;
    private boolean resumed;
    private boolean awaitingPurchaseOutcome;
    private long purchaseBaselineExpiry;
    private Dialog subscriptionDialog;
    private int ledgerGeneration;
    private UsagePageLoader pageLoader;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final SubscriptionRefreshSession refreshSession = new SubscriptionRefreshSession();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthStore auth = new AuthStore(this);
        HttpClient http = new HttpClient();
        usageStore = new UsageStore(auth, http);
        subscriptionStore = new WechatSubscriptionStore(auth, http);
        pageLoader = new UsagePageLoader(new UsagePageLoader.Source() {
            public UsageStore.Balance balance() throws Exception { return usageStore.balance(); }
            public UsageStore.Summary summary() throws Exception { return usageStore.summary(); }
            public UsageStore.LedgerPage ledger() throws Exception { return usageStore.ledger(); }
            public WechatSubscriptionStore.Status subscription() throws Exception { return subscriptionStore.status(); }
        });
        refreshSubscriptionOnResume = savedInstanceState != null && savedInstanceState.getBoolean("wechatSigning", false);
        if (savedInstanceState != null) {
            awaitingPurchaseOutcome = savedInstanceState.getBoolean("awaitingPurchaseOutcome", false);
            purchaseBaselineExpiry = savedInstanceState.getLong("purchaseBaselineExpiry", 0);
        }
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
        SystemBarDefaults.applyTopInsets(top, dp(12), dp(8), dp(16), dp(8));
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
        TextView topTitle = text("算力", 24, Theme.INK, Typeface.BOLD);
        topTitle.setGravity(Gravity.CENTER);
        top.addView(topTitle, new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        BouncyScrollView scroll = new BouncyScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        SystemBarDefaults.applyBottomInsets(content, dp(16), dp(6), dp(16), dp(40));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderLoading();
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

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        watchingPayment = refreshSubscriptionOnResume;
        refreshSubscriptionOnResume = false;
        load();
    }

    @Override
    protected void onPause() {
        resumed = false;
        refreshSession.stop();
        refreshHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("wechatSigning", refreshSubscriptionOnResume || watchingPayment);
        outState.putBoolean("awaitingPurchaseOutcome", awaitingPurchaseOutcome);
        outState.putLong("purchaseBaselineExpiry", purchaseBaselineExpiry);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        refreshSession.stop();
        refreshHandler.removeCallbacksAndMessages(null);
        if (subscriptionDialog != null) subscriptionDialog.dismiss();
        io.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        if (!resumed || isDestroyed()) return;
        refreshHandler.removeCallbacksAndMessages(null);
        usageRefreshing = true;
        render(usageLoaded);
        refreshPage(refreshSession.start());
    }

    private void refreshPage(int generation) {
        if (!refreshSession.accepts(generation)) return;
        final int ledgerVersion = ++ledgerGeneration;
        loadingMore = false;
        io.execute(() -> {
            UsagePageLoader.Result result = pageLoader.load();
            runOnUiThread(() -> {
                if (!refreshSession.accepts(generation) || isDestroyed()) return;
                if (!result.usageFailed && ledgerVersion == ledgerGeneration) {
                    balance = result.balance;
                    summary = result.summary;
                    entries.clear();
                    entries.addAll(result.ledger.entries);
                    nextCursor = result.ledger.nextCursor;
                    usageLoaded = true;
                }
                usageRefreshing = false;
                subscriptionLoaded = !result.subscriptionFailed;
                if (subscriptionLoaded) subscriptionStatus = result.subscription;
                render(usageLoaded);
                boolean newPayment = subscriptionLoaded && awaitingPurchaseOutcome
                        && subscriptionStatus.hasNewCoverage(purchaseBaselineExpiry);
                if (newPayment) {
                    awaitingPurchaseOutcome = false;
                    watchingPayment = false;
                    if (subscriptionDialog != null) subscriptionDialog.dismiss();
                    subscriptionDialog = WechatSubscriptionUi.showSuccess(this, subscriptionStatus);
                }
                boolean waiting = !newPayment && (watchingPayment || result.subscriptionFailed
                        || (subscriptionStatus != null && (subscriptionStatus.paymentPending || subscriptionStatus.pending()
                        || (subscriptionStatus.canCancel && !subscriptionStatus.active
                        && !subscriptionStatus.renewalStopped
                        && subscriptionStatus.scheduledChargeAt <= System.currentTimeMillis()))));
                if (refreshSession.completed(generation, waiting)) {
                    refreshHandler.postDelayed(() -> refreshPage(generation), SubscriptionRefreshSession.DELAY_MS);
                } else {
                    watchingPayment = false;
                    if (result.usageFailed || result.subscriptionFailed) toast("部分数据未更新，请稍后重新进入此页");
                }
            });
        });
    }

    private void renderLoading() {
        render(false);
    }

    private void render(boolean loaded) {
        content.removeAllViews();
        content.addView(heroCard(balance, loaded), matchWrap(0, 0, 0, dp(22)));
        // 与 iOS 一致：R2 售卖开关关闭时，未签约用户不显示微信订阅入口；
        // 已签约、已解约但仍有当期权益、或待签约用户仍可查看/管理自己的状态。
        if (subscriptionStatus != null && (subscriptionStatus.enabled || subscriptionStatus.active || subscriptionStatus.hasContract() || subscriptionStatus.appleActive)) {
            content.addView(subscriptionCard(subscriptionStatus, subscriptionLoaded), matchWrap(0, 0, 0, dp(22)));
        }
        if (!summary.granted.isEmpty()) {
            addSection(content, "算力来源");
            content.addView(summaryCard(summary.granted, "+", 0xff2f9b68), matchWrap(0, 0, 0, dp(22)));
        }
        if (!summary.spent.isEmpty()) {
            addSection(content, "花费总结");
            content.addView(summaryCard(summary.spent, "-", Theme.RED), matchWrap(0, 0, 0, dp(22)));
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
        row.addView(text(loaded ? String.valueOf((int) Math.round(balance.suanli)) : "—", 42, 0xffffffff, Typeface.BOLD));
        TextView capacity = text(loaded ? "≈ " + UsageStore.articleCapacity(balance.suanli) + " 篇" : "", 14, 0xffe2b871, Typeface.BOLD);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(-2, -2);
        capLp.setMargins(dp(8), 0, 0, dp(7));
        row.addView(capacity, capLp);
        card.addView(row);

        String foot = loaded
                ? "累计获赠 " + (int) Math.round(balance.suanli + balance.spentSuanli) + " · 已用 " + (int) Math.round(balance.spentSuanli)
                : usageRefreshing ? "加载中…" : "加载未完成，请稍后重新进入此页";
        TextView footer = text(foot, 13, 0xffc9bfae, Typeface.NORMAL);
        footer.setPadding(0, dp(14), 0, 0);
        card.addView(footer);
        return card;
    }

    private View subscriptionCard(WechatSubscriptionStore.Status status, boolean loaded) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(16));
        card.setBackground(strokedRound(Theme.CARD, 11, Theme.BORDER_CHROME));
        boolean paid = loaded && status.active && !status.paymentPending && !status.renewalStopped && !status.cancelled();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(paid ? R.drawable.ic_check_flat : R.drawable.ic_settings_bolt);
        icon.setImageTintList(ColorStateList.valueOf(paid ? 0xffffffff : Theme.AMBER));
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        icon.setBackground(round(paid ? WechatSubscriptionUi.SUCCESS : Theme.AMBER_BG, paid ? 20 : 10));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setBaselineAligned(true);
        titleRow.addView(text(loaded ? status.displayTitle() : "包月算力", 16, Theme.INK, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1));
        if (!paid) {
            TextView price = text("¥19.9/月", 20, Theme.INK, Typeface.BOLD);
            android.text.SpannableString priceLabel = new android.text.SpannableString("¥19.9/月");
            priceLabel.setSpan(new android.text.style.RelativeSizeSpan(0.65f), 5, priceLabel.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            priceLabel.setSpan(new android.text.style.ForegroundColorSpan(Theme.SECONDARY), 5, priceLabel.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            price.setText(priceLabel);
            LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(-2, -2);
            priceLp.leftMargin = dp(8);
            titleRow.addView(price, priceLp);
        }
        labels.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        TextView benefit = text("每月 " + fmt(status.monthlySuanli) + " 算力", 13, Theme.SECONDARY, Typeface.NORMAL);
        benefit.setPadding(0, dp(3), 0, 0);
        if (!paid) labels.addView(benefit);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1);
        labelsLp.setMargins(dp(12), 0, 0, 0);
        row.addView(labels, labelsLp);
        card.addView(row);
        if (paid) {
            View divider = new View(this);
            divider.setBackgroundColor(Theme.BORDER_CHROME);
            LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1));
            line.topMargin = dp(20);
            line.bottomMargin = dp(8);
            card.addView(divider, line);
        }
        boolean needsNotice = !loaded || status.appleActive || status.paymentPending || status.renewalStopped
                || status.cancelled() || status.pending() || "payment-failed".equals(status.paymentIssue)
                || (status.canCancel && !status.active);
        if (needsNotice) {
            String message = !loaded ? "订阅状态未更新，请稍后重新进入此页再购买。" : status.appleActive
                    ? "已有 Apple 订阅，请在 Apple 订阅设置中管理。" : status.paymentNotice();
            TextView notice = text(message, 12, Theme.SECONDARY, Typeface.NORMAL);
            notice.setLineSpacing(dp(2), 1f);
            notice.setPadding(0, dp(10), 0, 0);
            card.addView(notice);
        }
        if (loaded && status.expiresAt > 0) {
            TextView expiry = text("当期权益有效至  " + WechatSubscriptionUi.date(status.expiresAt), 12, Theme.SECONDARY, Typeface.NORMAL);
            expiry.setPadding(0, dp(12), 0, 0);
            card.addView(expiry);
        }
        String buttonLabel = !loaded ? "订阅状态未更新" : subscriptionInProgress ? "正在处理…"
                : status.canCancel ? "管理订阅" : status.actionLabel();
        TextView button = text(buttonLabel, 15, 0xffffffff, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        boolean canAct = loaded && !subscriptionInProgress && (status.canCancel || status.canSubscribe());
        if (loaded && status.canCancel) {
            button.setTextColor(Theme.AMBER);
            button.setBackground(strokedRound(Theme.CARD, 9, Theme.AMBER));
        } else {
            button.setBackground(round(Theme.AMBER, 9));
        }
        button.setAlpha(canAct ? 1f : 0.45f);
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.topMargin = dp(16);
        card.addView(button, btnLp);
        button.setEnabled(canAct);
        button.setOnClickListener(v -> {
            if (status.canCancel) {
                subscriptionDialog = WechatSubscriptionUi.showManagement(this, status,
                        () -> confirmWechatCancellation(subscriptionStatus), () -> {
                            if (subscriptionDialog != null) subscriptionDialog.dismiss();
                            load();
                        });
            } else {
                subscriptionDialog = WechatSubscriptionUi.showPurchase(this, status, this::beginWechatSubscription);
            }
        });
        if (!paid && loaded && status.canSubscribe()) {
            TextView terms = text("按订阅周期到期 · 随时可取消自动续费", 12, Theme.SECONDARY, Typeface.NORMAL);
            terms.setGravity(Gravity.CENTER);
            terms.setPadding(0, dp(10), 0, 0);
            card.addView(terms, new LinearLayout.LayoutParams(-1, -2));
        }
        return card;
    }

    private void confirmWechatCancellation(WechatSubscriptionStore.Status status) {
        String until = status.expiresAt > 0 ? "已付算力仍可使用至 " + formatDate(status.expiresAt) + "。" : "已付算力不会立即失效。";
        subscriptionDialog = IosDialog.showConfirmation(this, "解除自动续费", until + "\n\n解除后不再发起新的续费扣款。解约前已提交的订单仍可能完成支付，并正常发放算力。解除自动续费不会自动退款。",
                "解除", this::cancelWechatSubscription, "暂不解除", null, WechatSubscriptionUi.PRIMARY);
    }

    private void cancelWechatSubscription() {
        if (subscriptionInProgress || !resumed || isFinishing() || isDestroyed()) return;
        refreshSession.stop();
        refreshHandler.removeCallbacksAndMessages(null);
        subscriptionInProgress = true;
        render(usageLoaded);
        io.execute(() -> {
            try {
                subscriptionStore.cancel();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    subscriptionInProgress = false;
                    subscriptionLoaded = false;
                    render(usageLoaded);
                    toast("已解除自动续费");
                    load();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    subscriptionInProgress = false;
                    render(usageLoaded);
                    toast(WechatSubscriptionStore.userMessage(e));
                    load();
                });
            }
        });
    }

    private void beginWechatSubscription() {
        if (subscriptionInProgress || !resumed || isFinishing() || isDestroyed()) return;
        if (!WechatLogin.api(this).isWXAppInstalled()) {
            IosDialog.show(this, "未安装微信", "请安装微信后再开通包月算力。", WechatSubscriptionUi.PRIMARY);
            return;
        }
        refreshSession.stop();
        refreshHandler.removeCallbacksAndMessages(null);
        subscriptionInProgress = true;
        render(usageLoaded);
        io.execute(() -> {
            try {
                // Recheck both channels immediately before creating a signing session.
                WechatSubscriptionStore.Status latest = subscriptionStore.status();
                if (!latest.canSubscribe()) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        subscriptionInProgress = false;
                        subscriptionStatus = latest;
                        subscriptionLoaded = true;
                        render(usageLoaded);
                        toast(latest.canCancel ? "已签约，请刷新查看扣款结果" : latest.actionLabel());
                    });
                    return;
                }
                WechatSubscriptionStore.Contract contract = subscriptionStore.createContract();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (!resumed) { subscriptionInProgress = false; return; }
                    purchaseBaselineExpiry = latest.expiresAt;
                    launchWechatSigning(contract);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    subscriptionInProgress = false;
                    render(usageLoaded);
                    toast(WechatSubscriptionStore.userMessage(e));
                });
            }
        });
    }

    private void launchWechatSigning(WechatSubscriptionStore.Contract contract) {
        subscriptionInProgress = false;
        if (contract.expiresAt > 0 && contract.expiresAt <= System.currentTimeMillis()) {
            render(usageLoaded);
            toast("签约页面已过期，请重新发起");
            return;
        }
        if (!WechatLogin.api(this).isWXAppInstalled()) {
            render(usageLoaded);
            IosDialog.show(this, "未安装微信", "请安装微信后再开通包月算力。", WechatSubscriptionUi.PRIMARY);
            return;
        }
        boolean sent;
        if (contract.canLaunchWechat()) {
            WXLaunchMiniProgram.Req request = new WXLaunchMiniProgram.Req();
            request.userName = contract.miniProgramUserName;
            request.path = contract.miniProgramPath;
            request.miniprogramType = WXLaunchMiniProgram.Req.MINIPTOGRAM_TYPE_RELEASE;
            request.extData = contract.contractCode;
            sent = WechatLogin.api(this).sendReq(request);
        } else if (contract.preEntrustwebId != null && !contract.preEntrustwebId.isEmpty()) {
            // Old approved templates use OpenBusinessWebview instead of the signing mini program.
            WXOpenBusinessWebview.Req request = new WXOpenBusinessWebview.Req();
            request.businessType = 12; // WeChat's approved APP pure-signing business type.
            request.queryInfo = new HashMap<>();
            request.queryInfo.put("pre_entrustweb_id", contract.preEntrustwebId);
            sent = WechatLogin.api(this).sendReq(request);
        } else {
            render(usageLoaded);
            IosDialog.show(this, "暂时无法打开微信签约", "服务端尚未返回微信签约会话。请稍后重试。", WechatSubscriptionUi.PRIMARY);
            return;
        }
        render(usageLoaded);
        if (sent) {
            refreshSubscriptionOnResume = true;
            awaitingPurchaseOutcome = true;
            toast("已打开微信，请确认金额并完成签约");
        } else {
            render(usageLoaded);
            toast("微信未能打开签约页面，请重试");
        }
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(timestamp));
    }

    private View summaryCard(List<UsageStore.SummaryRow> rows, String sign, int amountColor) {
        LinearLayout card = card();
        for (int i = 0; i < rows.size(); i++) {
            UsageStore.SummaryRow rowData = rows.get(i);
            LinearLayout row = plainRow();
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.HORIZONTAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            labels.addView(text(rowData.reason, 15, Theme.INK, Typeface.NORMAL));
            if (rowData.count > 1) {
                TextView count = text(rowData.count + " 笔", 12, Theme.FAINT, Typeface.NORMAL);
                LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
                countLp.setMargins(dp(6), 0, 0, 0);
                labels.addView(count, countLp);
            }
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(sign + fmt(rowData.suanli), 15, amountColor, Typeface.BOLD));
            card.addView(row);
            if (i < rows.size() - 1) card.addView(divider());
        }
        return card;
    }

    private View ledgerCard(List<UsageStore.Entry> entries, boolean loaded) {
        LinearLayout card = card();
        if (entries.isEmpty()) {
            TextView empty = text(loaded ? "暂无记录" : usageRefreshing ? "加载中…" : "暂时无法加载明细，请稍后重新进入此页", 14, Theme.SECONDARY, Typeface.NORMAL);
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
            if (i < entries.size() - 1 || !nextCursor.isEmpty()) card.addView(divider());
        }
        if (!nextCursor.isEmpty()) {
            TextView more = text(loadingMore ? "加载中…" : "加载更早的记录", 14, Theme.SECONDARY, Typeface.NORMAL);
            more.setGravity(Gravity.CENTER);
            more.setPadding(dp(15), dp(13), dp(15), dp(13));
            more.setClickable(!loadingMore);
            more.setOnClickListener(v -> loadMore());
            card.addView(more);
        }
        return card;
    }

    private void loadMore() {
        if (loadingMore || nextCursor.isEmpty()) return;
        loadingMore = true;
        render(true);
        final String cursor = nextCursor;
        final int ledgerVersion = ledgerGeneration;
        io.execute(() -> {
            try {
                UsageStore.LedgerPage page = usageStore.ledger(cursor);
                runOnUiThread(() -> {
                    if (isDestroyed() || ledgerVersion != ledgerGeneration) return;
                    entries.addAll(page.entries);
                    nextCursor = page.nextCursor;
                    loadingMore = false;
                    render(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || ledgerVersion != ledgerGeneration) return;
                    loadingMore = false;
                    toast("加载更多失败：" + e.getMessage());
                    render(true);
                });
            }
        });
    }

    private String label(UsageStore.Entry e) {
        if ("subscription".equals(e.reason)) return "包月订阅";
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
        card.setBackground(strokedRound(Theme.CARD, 11, Theme.BORDER_CHROME));
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

    private void configureEdgeToEdge() {
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void toast(String message) {
        runOnUiThread(() -> SimpleToast.show(this, message));
    }

}
