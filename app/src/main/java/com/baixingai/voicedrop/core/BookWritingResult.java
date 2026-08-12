package com.baixingai.voicedrop.core;

import org.json.JSONObject;

import java.util.Locale;

/** User-visible result mapping for POST lab.jianshuo.dev/api/book. */
public final class BookWritingResult {
    public static final double DEFAULT_COST_SUANLI = 320;

    public final int code;
    public final boolean accepted;
    public final String message;
    public final Double balance;

    private BookWritingResult(int code, boolean accepted, String message) {
        this(code, accepted, message, null);
    }

    private BookWritingResult(int code, boolean accepted, String message, Double balance) {
        this.code = code;
        this.accepted = accepted;
        this.message = message;
        this.balance = balance;
    }

    public static BookWritingResult from(int code, String responseBody) {
        if (code == 202) {
            return new BookWritingResult(code, true,
                    "开始写了！现在可以关闭 App，稍后下拉刷新「写书」书架查看。");
        }
        if (code == 402) {
            double need = DEFAULT_COST_SUANLI;
            double have = 0;
            try {
                JSONObject body = new JSONObject(responseBody == null ? "{}" : responseBody);
                need = body.optDouble("need_suanli", DEFAULT_COST_SUANLI);
                have = body.optDouble("suanli", 0);
            } catch (Exception ignored) {}
            return new BookWritingResult(code, false,
                    "算力不足：写一本书要 " + formatSuanli(need) + " 算力，你现在有 "
                            + formatSuanli(have) + "。", have);
        }
        if (code == 401) {
            return new BookWritingResult(code, false, "身份校验没过，请稍后重试。");
        }
        return new BookWritingResult(code, false, code == 0
                ? "没连上服务器，请检查网络后重试。"
                : "服务器返回 " + code + "，请稍后重试。");
    }

    private static String formatSuanli(double value) {
        if (!Double.isFinite(value)) value = 0;
        double rounded = Math.round(value * 10) / 10.0;
        if (rounded == Math.rint(rounded)) return Long.toString(Math.round(rounded));
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
