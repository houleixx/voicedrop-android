package com.baixingai.voicedrop.data;

import android.net.Uri;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class UsageStore {
    public static final double SUANLI_PER_ARTICLE = 9.0;
    private final AuthStore auth;
    private final HttpClient http;

    public UsageStore(AuthStore auth, HttpClient http) {
        this.auth = auth;
        this.http = http;
    }

    public Balance balance() throws Exception {
        HttpClient.Response response = http.get(Api.agentBase() + "/usage/balance", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("usage HTTP " + response.code);
        JSONObject obj = new JSONObject(response.text());
        return new Balance(obj.optDouble("suanli", 0), obj.optDouble("spent_suanli", 0));
    }

    public Summary summary() throws Exception {
        HttpClient.Response response = http.get(Api.agentBase() + "/usage/summary", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("usage summary HTTP " + response.code);
        JSONObject obj = new JSONObject(response.text());
        return new Summary(summaryRows(obj.optJSONArray("granted")), summaryRows(obj.optJSONArray("spent")));
    }

    public LedgerPage ledger() throws Exception {
        return ledger(null);
    }

    public LedgerPage ledger(String before) throws Exception {
        String url = Api.agentBase() + "/usage/ledger?limit=50";
        if (before != null && !before.isEmpty()) url += "&before=" + Uri.encode(before);
        HttpClient.Response response = http.get(url, auth.bearer());
        if (!response.ok()) throw new IllegalStateException("ledger HTTP " + response.code);
        JSONObject body = new JSONObject(response.text());
        JSONArray arr = body.optJSONArray("entries");
        List<Entry> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                out.add(new Entry(e.optLong("id"), e.optLong("ts"), e.optString("kind"), e.optString("reason"),
                        e.optDouble("suanli"), e.optDouble("balance_suanli")));
            }
        }
        String next = body.optBoolean("has_more", false) ? body.optString("next", "") : "";
        return new LedgerPage(out, next);
    }

    private static List<SummaryRow> summaryRows(JSONArray arr) throws Exception {
        List<SummaryRow> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.getJSONObject(i);
            out.add(new SummaryRow(row.optString("reason_code"), row.optString("reason"),
                    row.optDouble("suanli"), row.optInt("count", 1)));
        }
        return out;
    }

    public static int articleCapacity(double balance) {
        return Math.max(0, (int) Math.floor(balance / SUANLI_PER_ARTICLE));
    }

    public static final class Balance {
        public final double suanli;
        public final double spentSuanli;
        public Balance(double suanli, double spentSuanli) {
            this.suanli = suanli;
            this.spentSuanli = spentSuanli;
        }
    }

    public static final class Entry {
        public final long id;
        public final long ts;
        public final String kind;
        public final String reason;
        public final double suanli;
        public final double balanceSuanli;
        Entry(long id, long ts, String kind, String reason, double suanli, double balanceSuanli) {
            this.id = id;
            this.ts = ts;
            this.kind = kind;
            this.reason = reason;
            this.suanli = suanli;
            this.balanceSuanli = balanceSuanli;
        }
    }

    public static final class LedgerPage {
        public final List<Entry> entries;
        public final String nextCursor;

        public LedgerPage(List<Entry> entries, String nextCursor) {
            this.entries = entries;
            this.nextCursor = nextCursor;
        }
    }

    public static final class Summary {
        public final List<SummaryRow> granted;
        public final List<SummaryRow> spent;

        public Summary(List<SummaryRow> granted, List<SummaryRow> spent) {
            this.granted = granted;
            this.spent = spent;
        }
    }

    public static final class SummaryRow {
        public final String reasonCode;
        public final String reason;
        public final double suanli;
        public final int count;

        public SummaryRow(String reasonCode, String reason, double suanli, int count) {
            this.reasonCode = reasonCode;
            this.reason = reason;
            this.suanli = suanli;
            this.count = count;
        }
    }
}
