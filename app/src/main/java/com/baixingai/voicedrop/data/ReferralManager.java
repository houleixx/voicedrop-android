package com.baixingai.voicedrop.data;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReferralManager {
    private static final String PREFS = "voicedrop.referral";
    private static final String DONE = "done";
    private static final String FIRST_LAUNCH_AT = "firstLaunchAt";
    private static final long WINDOW_MS = 24L * 60L * 60L * 1000L;
    // 邀请码链接（voicedrop.cn/i/<码>，2026-07-16 上线的「邀请好友」落地页）比分享
    // 短链更窄，先判——与 iOS ReferralManager.shareToken 的模式顺序保持一致。
    private static final Pattern INVITE = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:voicedrop\\.cn|jianshuo\\.dev/voicedrop)/i/([A-Za-z0-9]{6,16})");
    private static final Pattern TOKEN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:voicedrop\\.cn/|jianshuo\\.dev/voicedrop/)([A-Za-z0-9_-]{6,16})");

    private final Context context;
    private final AuthStore auth;
    private final HttpClient http;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    // 跨实例互斥：Application 启动与 Activity 焦点重试各自 new 实例，别并发 claim。
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public ReferralManager(Context context) {
        this(context, new AuthStore(context), new HttpClient());
    }

    ReferralManager(Context context, AuthStore auth, HttpClient http) {
        this.context = context.getApplicationContext();
        this.auth = auth;
        this.http = http;
    }

    /** @return true = 已处理（终局/过窗/已排队跑），false = 互斥占用中（调用方可稍后再试）。 */
    public boolean runOnLaunch() {
        if (done() || !withinWindow()) return true;
        if (!RUNNING.compareAndSet(false, true)) return false;
        io.execute(() -> {
            try {
                if (claim("hello", null)) return;
                if (done()) return;   // hello 被服务端终局否定（not-new 等）→ 不再探剪贴板
                String token = clipboardToken();
                if (token != null) claim("clipboard", token);
            } finally {
                RUNNING.set(false);
            }
        });
        return true;
    }

    public void noteShareToken(String id) {
        if (id == null || id.trim().isEmpty() || done() || !withinWindow()) return;
        io.execute(() -> claim("link", id.trim()));
    }

    private boolean claim(String source, String token) {
        try {
            String bearer = auth.bearer();
            if (bearer == null || bearer.isEmpty()) return false;
            JSONObject body = new JSONObject().put("source", source);
            if (token != null && !token.isEmpty()) body.put("token", token);
            HttpClient.Response response = http.postJson(Api.agentBase() + "/referral/claim",
                    bearer, body.toString().getBytes("UTF-8"));
            if (!response.ok()) return false;
            JSONObject json = new JSONObject(response.text());
            boolean attributed = json.optBoolean("attributed", false);
            String reason = json.optString("reason", "");
            if (attributed || "not-new".equals(reason) || "device-used".equals(reason) || "disabled".equals(reason)) {
                setDone(true);
            }
            return attributed;
        } catch (Exception e) {
            return false;
        }
    }

    private String clipboardToken() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
        ClipData data = clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) return null;
        CharSequence text = data.getItemAt(0).coerceToText(context);
        return shareToken(text == null ? "" : text.toString());
    }

    private boolean withinWindow() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long first = prefs.getLong(FIRST_LAUNCH_AT, 0);
        long now = System.currentTimeMillis();
        if (first == 0) {
            first = now;
            prefs.edit().putLong(FIRST_LAUNCH_AT, first).apply();
        }
        return now - first < WINDOW_MS;
    }

    private boolean done() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DONE, false);
    }

    private void setDone(boolean value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DONE, value).apply();
    }

    public static String shareToken(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher invite = INVITE.matcher(text);
        if (invite.find()) return invite.group(1);
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!"privacy".equals(id) && !"welcome".equals(id) && !"help".equals(id)) return id;
        }
        return null;
    }
}
