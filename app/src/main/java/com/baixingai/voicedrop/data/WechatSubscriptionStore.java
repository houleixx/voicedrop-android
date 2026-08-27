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
        if (!response.ok()) throw failure("subscription status", response);
        return Status.fromJson(new JSONObject(response.text()));
    }

    /** Creates a server-side contract and its short-lived WeChat signing handoff. */
    public Contract createContract() throws Exception {
        HttpClient.Response response = http.postJson(Api.agentBase() + "/wechat-pay/contract", auth.bearer(),
                "{}".getBytes(StandardCharsets.UTF_8));
        if (!response.ok()) throw failure("subscription contract", response);
        return Contract.fromJson(new JSONObject(response.text()));
    }

    private static IllegalStateException failure(String operation, HttpClient.Response response) {
        String detail = "";
        try { detail = new JSONObject(response.text()).optString("error", ""); } catch (Exception ignored) {}
        return new IllegalStateException(operation + " HTTP " + response.code
                + (detail.isEmpty() ? "" : " (" + detail + ")"));
    }

    public static final class Status {
        public final boolean active;
        public final String status;
        public final String planId;
        public final long expiresAt;
        public final String paymentIssue;
        public final double subscriptionSuanli;
        public final double monthlySuanli;

        Status(boolean active, String status, String planId, long expiresAt, String paymentIssue,
               double subscriptionSuanli, double monthlySuanli) {
            this.active = active;
            this.status = status;
            this.planId = planId;
            this.expiresAt = expiresAt;
            this.paymentIssue = paymentIssue;
            this.subscriptionSuanli = subscriptionSuanli;
            this.monthlySuanli = monthlySuanli;
        }

        public boolean pending() { return "pending".equals(status); }

        static Status fromJson(JSONObject body) {
            return new Status(body.optBoolean("active", false), nullable(body, "status"),
                    nullable(body, "plan_id"), body.optLong("expires_date", 0),
                    nullable(body, "payment_issue"), body.optDouble("sub_suanli", 0),
                    body.optDouble("monthly_suanli", 200));
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
        String value = body.optString(name, "").trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean empty(String value) { return value == null || value.isEmpty(); }
}
