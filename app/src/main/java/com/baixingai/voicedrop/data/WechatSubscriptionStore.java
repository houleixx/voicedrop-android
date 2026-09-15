package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** Server-backed state for the WeChat recurring-payment subscription. */
public final class WechatSubscriptionStore {
    private final AuthStore auth;
    private final HttpClient http;

    public WechatSubscriptionStore(AuthStore auth, HttpClient http) {
        this.auth = auth;
        this.http = http;
    }

    public Status status() throws Exception {
        HttpClient.Response response = http.get(Api.agentBase() + "/wechat-pay/status", auth.bearer());
        if (!response.ok()) throw failure(response);
        JSONObject current = null;
        try {
            HttpClient.Response shared = http.get(Api.agentBase() + "/subscription/status", auth.bearer());
            if (shared.ok()) current = new JSONObject(shared.text());
        } catch (Exception ignored) {
            // Existing contracts remain manageable while the purchase guard is unavailable.
        }
        return Status.fromJson(new JSONObject(response.text()), current);
    }

    /** Creates a server-side contract and its short-lived WeChat signing handoff. */
    public Contract createContract() throws Exception {
        HttpClient.Response response = http.postJson(Api.agentBase() + "/wechat-pay/contract", auth.bearer(),
                "{}".getBytes(StandardCharsets.UTF_8));
        if (!response.ok()) throw failure(response);
        return Contract.fromJson(new JSONObject(response.text()));
    }

    /** Terminates the active WeChat contract server-side. Already-cancelled retries are safe. */
    public Status cancel() throws Exception {
        HttpClient.Response response = http.postJson(Api.agentBase() + "/wechat-pay/cancel", auth.bearer(),
                "{}".getBytes(StandardCharsets.UTF_8));
        if (!response.ok()) throw failure(response);
        return Status.fromJson(new JSONObject(response.text()));
    }

    private static IllegalStateException failure(HttpClient.Response response) {
        String detail = "";
        try { detail = new JSONObject(response.text()).optString("error", ""); } catch (Exception ignored) {}
        return new SubscriptionFailure(failureMessage(detail));
    }

    private static final class SubscriptionFailure extends IllegalStateException {
        SubscriptionFailure(String message) { super(message); }
    }

    public static String userMessage(Exception error) {
        return error instanceof SubscriptionFailure ? error.getMessage()
                : "暂时无法连接订阅服务，请检查网络并刷新结果。";
    }

    public static String failureMessage(String code) {
        switch (code) {
            case "payment-pending": return "有一笔扣款正在确认，请稍后刷新。请勿重复开通。";
            case "already-subscribed": return "已有订阅或有效签约，请刷新后管理订阅。";
            case "disabled": return "包月算力暂未开放购买，已有订阅仍可管理。";
            case "unauthorized": return "登录状态已失效，请重新登录后再试。";
            case "no-active-contract": return "当前没有可解除的签约，请刷新订阅状态。";
            case "cancel-unavailable": return "暂时无法确认解约结果，请刷新查看，也可在微信扣费服务中管理。";
            case "degraded": return "微信订阅服务暂不可用，请稍后再试。";
            default: return "请求未能完成，请刷新确认当前状态后再试。";
        }
    }

    public static final class Status {
        public final boolean active;
        public final boolean enabled;
        public final boolean canCancel;
        public final String status;
        public final String planId;
        public final long expiresAt;
        public final long scheduledChargeAt;
        public final String paymentIssue;
        public final double subscriptionSuanli;
        public final double monthlySuanli;
        public final boolean purchaseStatusKnown;
        public final boolean appleActive;
        public final boolean paymentPending;
        public final boolean renewalStopped;

        Status(boolean active, boolean enabled, boolean canCancel, String status, String planId, long expiresAt, long scheduledChargeAt, String paymentIssue,
               double subscriptionSuanli, double monthlySuanli, boolean purchaseStatusKnown, boolean appleActive,
               boolean paymentPending, boolean renewalStopped) {
            this.active = active;
            this.enabled = enabled;
            this.canCancel = canCancel;
            this.status = status;
            this.planId = planId;
            this.expiresAt = expiresAt;
            this.scheduledChargeAt = scheduledChargeAt;
            this.paymentIssue = paymentIssue;
            this.subscriptionSuanli = subscriptionSuanli;
            this.monthlySuanli = monthlySuanli;
            this.purchaseStatusKnown = purchaseStatusKnown;
            this.appleActive = appleActive;
            this.paymentPending = paymentPending;
            this.renewalStopped = renewalStopped;
        }

        public boolean pending() { return "pending".equals(status); }
        public boolean cancelled() { return "cancelled".equals(status); }
        public boolean hasContract() { return pending() || "active".equals(status) || cancelled(); }
        public boolean canSubscribe() {
            return purchaseStatusKnown && !appleActive && !paymentPending && !canCancel && !"active".equals(status)
                    && (enabled || pending());
        }

        public String actionLabel() {
            if (canCancel) return "解除自动续费";
            if (appleActive) return "已通过 Apple 订阅";
            if (!purchaseStatusKnown) return "订阅状态暂不可用";
            if (paymentPending) return "扣款结果确认中";
            if (pending()) return "继续微信签约";
            return cancelled() ? "重新开通" : "微信支付订阅";
        }

        /** Existing paid coverage alone is not evidence that this purchase succeeded. */
        public boolean hasNewCoverage(long previousExpiry) {
            return active && expiresAt > previousExpiry;
        }

        public String displayTitle() {
            if (paymentPending) return "扣款结果确认中";
            if (cancelled()) return "自动续费已解除";
            if (renewalStopped) return "本周期续费已暂停";
            if ("payment-failed".equals(paymentIssue)) return "最近一次扣款未完成";
            if (active) return "包月算力已开通";
            if (canCancel) return "已签约 · 等待扣款";
            if (pending()) return "签约尚未完成";
            if (appleActive) return "已通过 Apple 订阅";
            return "包月算力";
        }

        public String paymentNotice() {
            if (paymentPending) return "微信正在处理扣款，以最终支付结果为准。无需重复开通，可稍后刷新查看。";
            if (cancelled()) return "不再发起新的续费扣款。已付算力保留至各自到期日，解约前提交的订单仍可能完成支付。";
            if (renewalStopped) return "本周期多次扣款未成功，已停止自动重试。请检查微信支付方式；需要恢复时，可解除后重新签约。";
            if ("payment-failed".equals(paymentIssue)) return "最近一次扣款未完成，请在微信中检查支付方式，并稍后刷新结果。";
            if (canCancel && !active) return "签约不等于付款成功。扣款成功后算力才会到账，暂未到账时请稍后刷新。";
            if (pending()) return "如果已在微信完成签约，请稍后刷新；如果中途退出，可重新进入微信继续签约。";
            return "每期付款成功后发放算力，按订阅周期到期。可随时管理自动续费。";
        }

        static Status fromJson(JSONObject body) {
            return fromJson(body, null);
        }

        static Status fromJson(JSONObject body, JSONObject current) {
            boolean known = !body.optBoolean("degraded", false) && current != null && current.has("active") && !current.optBoolean("degraded", false)
                    && (!current.optBoolean("active") || "apple".equals(current.optString("provider"))
                    || "wechat".equals(current.optString("provider")));
            // 与 iOS 的 config/iap.json 语义一致：服务端没有明确给开启开关时，不展示售卖入口。
            return new Status(body.optBoolean("active", false), body.optBoolean("enabled", false), body.optBoolean("can_cancel", false), nullable(body, "status"),
                    nullable(body, "plan_id"), body.optLong("expires_date", 0),
                    body.optLong("scheduled_charge_at", 0),
                    nullable(body, "payment_issue"), body.optDouble("sub_suanli", 0),
                    body.optDouble("monthly_suanli", 200), known,
                    known && current.optBoolean("active") && "apple".equals(current.optString("provider")),
                    body.optBoolean("payment_pending", false), body.optBoolean("renewal_stopped", false));
        }
    }

    public static final class Contract {
        public final String contractCode;
        public final String preEntrustwebId;
        public final String miniProgramUserName;
        public final String miniProgramPath;
        public final long expiresAt;

        Contract(String contractCode, String preEntrustwebId, String miniProgramUserName,
                 String miniProgramPath, long expiresAt) {
            this.contractCode = contractCode;
            this.preEntrustwebId = preEntrustwebId;
            this.miniProgramUserName = miniProgramUserName;
            this.miniProgramPath = miniProgramPath;
            this.expiresAt = expiresAt;
        }

        /**
         * The backend owns all signed WeChat parameters.  The aliases retain compatibility while
         * the service migrates from its original response naming to the Android handoff fields.
         */
        static Contract fromJson(JSONObject body) {
            return new Contract(nullable(body, "contract_code"), first(body,
                    "pre_entrustweb_id", "preEntrustwebId"), first(body,
                    "wechat_mini_program_username", "mini_program_username", "miniProgramUserName"),
                    first(body, "wechat_mini_program_path", "mini_program_path", "miniProgramPath"),
                    body.optLong("expires_at", 0));
        }

        public boolean canLaunchWechat() {
            return !empty(miniProgramUserName) && !empty(miniProgramPath);
        }
    }

    private static String first(JSONObject body, String... names) {
        for (String name : names) {
            String value = nullable(body, name);
            if (!empty(value)) return value;
        }
        return null;
    }

    private static String nullable(JSONObject body, String name) {
        if (body.isNull(name)) return null;
        String value = body.optString(name, "").trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean empty(String value) { return value == null || value.isEmpty(); }
}
