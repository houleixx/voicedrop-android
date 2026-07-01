package com.baixingai.voicedrop.data;

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

    public List<Entry> ledger() throws Exception {
        HttpClient.Response response = http.get(Api.agentBase() + "/usage/ledger?limit=50", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("ledger HTTP " + response.code);
        JSONArray arr = new JSONObject(response.text()).optJSONArray("entries");
        List<Entry> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                out.add(new Entry(e.optInt("ts"), e.optString("kind"), e.optString("reason"),
                        e.optDouble("suanli"), e.optDouble("balance_suanli")));
            }
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
        public final int ts;
        public final String kind;
        public final String reason;
        public final double suanli;
        public final double balanceSuanli;
        Entry(int ts, String kind, String reason, double suanli, double balanceSuanli) {
            this.ts = ts;
            this.kind = kind;
            this.reason = reason;
            this.suanli = suanli;
            this.balanceSuanli = balanceSuanli;
        }
    }
}
