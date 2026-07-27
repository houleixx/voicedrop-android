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
            "(?:https?://)?(?:www\\.)?(?:voicedrop\\.cn|jianshuo\\.dev/voicedrop)/i/([A-Za-z0-9]{6,16})(?![A-Za-z0-9_/-])");
    private static final Pattern TOKEN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:voicedrop\\.cn/|jianshuo\\.dev/voicedrop/)([A-Za-z0-9_-]{6,16})");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.atomic.AtomicBoolean LAUNCH_STARTED =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean FOCUSED_CLIPBOARD_STARTED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final Context context;
    private final AuthStore auth;
    private final HttpClient http;

    public ReferralManager(Context context) {
        this(context, new AuthStore(context), new HttpClient());
    }

    ReferralManager(Context context, AuthStore auth, HttpClient http) {
        this.context = context.getApplicationContext();
        this.auth = auth;
        this.http = http;
    }

    /** Records the launch-level attribution check once per process. */
    public void runOnLaunch() {
        if (done() || !withinWindow() || !LAUNCH_STARTED.compareAndSet(false, true)) return;
        IO.execute(() -> {
            if (!done() && withinWindow()) claim("hello", null);
        });
    }

    /**
     * Reads the clipboard only after an Activity has a window focus. The shared serial executor
     * makes this run after the launch check even when focus arrives before its network call ends.
     */
    public void runWhenWindowFocused() {
        runOnLaunch();
        if (done() || !withinWindow() || !FOCUSED_CLIPBOARD_STARTED.compareAndSet(false, true)) return;
        IO.execute(() -> {
            if (done() || !withinWindow()) return;
            String token = clipboardToken();
            if (token != null) claim("clipboard", token);
        });
    }

    public void noteShareToken(String id) {
        if (id == null || id.trim().isEmpty() || done() || !withinWindow()) return;
        IO.execute(() -> claim("link", id.trim()));
    }

    public InviteLink inviteLink() throws Exception {
        HttpClient.Response response = http.get(Api.agentBase() + "/referral/link", auth.bearer());
        if (!response.ok()) throw new IllegalStateException("invite link HTTP " + response.code);
        JSONObject json = new JSONObject(response.text());
        String url = json.optString("url", "").trim();
        if (url.isEmpty()) throw new IllegalStateException("邀请链接暂不可用");
        return new InviteLink(url, json.optString("name", "").trim(),
                json.optInt("suanliInviter", 0), json.optInt("suanliFriend", 0));
    }

    public static final class InviteLink {
        public final String url;
        public final String name;
        public final int suanliInviter;
        public final int suanliFriend;

        InviteLink(String url, String name, int suanliInviter, int suanliFriend) {
            this.url = url;
            this.name = name;
            this.suanliInviter = suanliInviter;
            this.suanliFriend = suanliFriend;
        }
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
