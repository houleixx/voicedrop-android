package com.baixingai.voicedrop.core;

import org.json.JSONObject;

import java.util.Locale;

/** User-visible result mapping for POST /api/book/revise. */
public final class BookReviseResult {
    public static final double DEFAULT_COST_SUANLI = 40;

    public final int code;
    public final boolean accepted;
    public final String message;
    public final double timestampMs;

    private BookReviseResult(int code, boolean accepted, String message, double timestampMs) {
        this.code = code;
        this.accepted = accepted;
        this.message = message;
        this.timestampMs = timestampMs;
    }

    public static BookReviseResult from(int code, String responseBody) {
        JSONObject body;
        try { body = new JSONObject(responseBody == null ? "{}" : responseBody); }
        catch (Exception ignored) { body = new JSONObject(); }
        if (code == 202) {
            return new BookReviseResult(code, true, "修改已受理", body.optDouble("ts", 0));
        }
        if (code == 402) {
            double need = body.optDouble("need_suanli", DEFAULT_COST_SUANLI);
            double have = body.optDouble("suanli", 0);
            return new BookReviseResult(code, false,
                    "算力不足：改一次要 " + format(need) + " 算力，你现在有 " + format(have) + "。", 0);
        }
        if (code == 401) return new BookReviseResult(code, false, "身份校验没过，请稍后重试。", 0);
        if (code == 403) return new BookReviseResult(code, false, "只有这本书的主人能修改。", 0);
        if (code == 404) return new BookReviseResult(code, false,
                "这本书是早期写的，还没登记主人，暂时不能在线修改。", 0);
        if (code == 409) return new BookReviseResult(code, false, "上一个修改还在进行，等它改完再提。", 0);
        return new BookReviseResult(code, false, code == 0
                ? "没连上服务器，请检查网络后重试。"
                : "服务器返回 " + code + "，请稍后重试。", 0);
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) value = 0;
        double rounded = Math.round(value * 10) / 10.0;
        if (rounded == Math.rint(rounded)) return Long.toString(Math.round(rounded));
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
