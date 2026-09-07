package com.baixingai.voicedrop;

import android.app.Activity;
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
    private WechatSubscriptionStore.Status subscriptionStatus;
    private boolean subscriptionLoaded;
    private boolean subscriptionInProgress;
    private boolean refreshSubscriptionOnResume;
    private boolean watchingPayment;
    private boolean resumed;
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
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        refreshSession.stop();
        refreshHandler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        if (!resumed || isDestroyed()) return;
        refreshHandler.removeCallbacksAndMessages(null);
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
                subscriptionLoaded = !result.subscriptionFailed;
                if (subscriptionLoaded) subscriptionStatus = result.subscription;
                render(usageLoaded);
                boolean waiting = watchingPayment || result.subscriptionFailed
                        || (subscriptionStatus != null && (subscriptionStatus.pending()
                        || (subscriptionStatus.canCancel && !subscriptionStatus.active)));
                if (refreshSession.completed(generation, waiting)) {
                    refreshHandler.postDelayed(() -> refreshPage(generation), SubscriptionRefreshSession.DELAY_MS);
                } else {
                    watchingPayment = false;
                    if (result.usageFailed || result.subscriptionFailed) toast("部分数据未更新，请点击刷新重试");
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
        TextView refresh = text("刷新算力与订阅状态", 13, Theme.SECONDARY, Typeface.NORMAL);
        refresh.setGravity(Gravity.CENTER);
        refresh.setMinHeight(dp(48));
        refresh.setEnabled(!subscriptionInProgress);
        refresh.setOnClickListener(v -> load());
        content.addView(refresh, matchWrap(0, 0, 0, dp(12)));
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

    private View subscriptionCard(WechatSubscriptionStore.Status status, boolean loaded) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(strokedRound(Theme.CARD, 11, Theme.BORDER_CHROME));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView bolt = text("⚡", 22, 0xffc98a2e, Typeface.BOLD);
        bolt.setGravity(Gravity.CENTER);
        bolt.setBackground(round(0xfffbead2, 10));
        row.addView(bolt, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(status != null && status.active ? "包月算力 · 订阅中" : "包月算力", 15, Theme.INK, Typeface.BOLD));
        String subtitle;
        if (!loaded) subtitle = "订阅状态未更新，请点击刷新重试";
        else if (status.appleActive && !status.canCancel) subtitle = "已通过 Apple 开通，请在 Apple 订阅设置中管理";
        else if (status != null && status.scheduledChargeAt > System.currentTimeMillis()) subtitle = "预计于 " + formatDate(status.scheduledChargeAt) + " 发起扣款 · 成功后算力立即到账";
        else if (status != null && status.canCancel) subtitle = "每月自动充入 " + fmt(status.monthlySuanli) + " 算力 · 可随时取消";
        else if (status != null && status.cancelled() && status.expiresAt > System.currentTimeMillis()) subtitle = "自动续费已解除 · 算力仍可使用至到期日";
        else if (status != null && status.pending()) subtitle = "正在等待微信完成签约";
        else subtitle = "每月 200 算力 · 按订阅周期到期 · 随时可取消";
        TextView sub = text(subtitle, 13, Theme.SECONDARY, Typeface.NORMAL);
        sub.setPadding(0, dp(2), 0, 0);
        labels.addView(sub);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1);
        labelsLp.setMargins(dp(10), 0, dp(8), 0);
        row.addView(labels, labelsLp);
        row.addView(text("¥19.9/月", 18, Theme.INK, Typeface.BOLD));
        card.addView(row);

        String buttonLabel;
        if (!loaded) buttonLabel = "订阅状态待刷新";
        else if (subscriptionInProgress) buttonLabel = "正在处理…";
        else buttonLabel = status.actionLabel();
        TextView button = text(buttonLabel, 15, 0xffffffff, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        boolean canAct = loaded && !subscriptionInProgress && (status.canCancel || status.canSubscribe());
        button.setBackground(round(canAct ? 0xffc98a2e : 0x88c98a2e, 9));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(46));
        btnLp.setMargins(0, dp(13), 0, 0);
        card.addView(button, btnLp);
        button.setEnabled(canAct);
        button.setOnClickListener(v -> {
            if (status != null && status.canCancel) {
                confirmWechatCancellation(status);
            } else if (status != null && status.pending()) {
                beginWechatSubscription();
            } else if (status != null && status.cancelled() && status.expiresAt > System.currentTimeMillis()) {
                confirmWechatResubscription(status);
            } else {
                beginWechatSubscription();
            }
        });
        if (status != null && "payment-failed".equals(status.paymentIssue)) {
            TextView issue = text("最近一次续费未完成，请在微信中检查支付方式。", 12, Theme.RED, Typeface.NORMAL);
            issue.setPadding(0, dp(10), 0, 0);
            card.addView(issue);
        } else if (status != null && status.expiresAt > 0 && status.canCancel) {
            TextView renew = text("当期权益有效至：" + formatDate(status.expiresAt), 12, Theme.SECONDARY, Typeface.NORMAL);
            renew.setPadding(0, dp(10), 0, 0);
            card.addView(renew);
        } else if (status != null && status.cancelled() && status.expiresAt > System.currentTimeMillis()) {
            TextView renew = text("权益有效至：" + formatDate(status.expiresAt), 12, Theme.SECONDARY, Typeface.NORMAL);
            renew.setPadding(0, dp(10), 0, 0);
            card.addView(renew);
        }
        return card;
    }

    private void confirmWechatCancellation(WechatSubscriptionStore.Status status) {
        String until = status.expiresAt > 0 ? "已付算力仍可使用至 " + formatDate(status.expiresAt) + "。" : "已付算力不会立即失效。";
        IosDialog.showConfirmation(this, "解除自动续费", until + "\n\n解除后将不再自动扣款，之后可随时重新开通。",
                "解除", this::cancelWechatSubscription, "暂不解除", null);
    }

    private void confirmWechatResubscription(WechatSubscriptionStore.Status status) {
        IosDialog.showConfirmation(this, "重新开通自动续费",
                "当前算力有效至 " + formatDate(status.expiresAt) + "。扣款计划在到期日前第 3 个北京时间自然日凌晨 2 点申请；若已过该时间，签约后立即申请。实际扣款成功后立即新增 200 算力，可提前使用；旧算力仍在原到期日失效，新算力在下一订阅周期结束时失效。",
                "去微信签约", this::beginWechatSubscription, "暂不重新开通", null);
    }

    private void cancelWechatSubscription() {
        if (subscriptionInProgress) return;
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
                    toast("解除订阅失败，请稍后重试");
                });
            }
        });
    }

    private void beginWechatSubscription() {
        if (subscriptionInProgress) return;
        if (!WechatLogin.api(this).isWXAppInstalled()) {
            IosDialog.show(this, "未安装微信", "请安装微信后再开通包月算力。");
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
                    launchWechatSigning(contract);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    subscriptionInProgress = false;
                    render(usageLoaded);
                    toast("无法发起微信订阅：" + e.getMessage());
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
            IosDialog.show(this, "未安装微信", "请安装微信后再开通包月算力。");
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
            IosDialog.show(this, "暂时无法打开微信签约", "服务端尚未返回微信签约会话。请稍后重试。");
            return;
        }
        render(usageLoaded);
        if (sent) {
            refreshSubscriptionOnResume = true;
            toast("已打开微信，请完成包月签约");
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
