package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.baixingai.voicedrop.core.ArticlePhotoInsert;
import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.PrivacyConsent;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.share.DatasetItem;
import com.baixingai.voicedrop.share.ShareApi;
import com.baixingai.voicedrop.share.ShareDatasetUi;
import com.baixingai.voicedrop.share.ShareExtraction;
import com.baixingai.voicedrop.share.ShareKind;
import com.baixingai.voicedrop.share.SharePayload;
import com.baixingai.voicedrop.share.ShareRouter;
import com.baixingai.voicedrop.share.SilentAudio;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.PrivacyConsentDialog;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Receives Android system shares and mirrors the iOS/HarmonyOS share flow. */
public final class ShareCollectActivity extends Activity {
    private enum IncomingState { SAVING, DONE, FAILED }

    private static final class IncomingItem {
        final String type;
        final Uri uri;
        final String rawText;
        String title;
        String meta;
        int chars;
        boolean canRetry = true;
        IncomingState state = IncomingState.SAVING;

        IncomingItem(String type, String title, String meta, String rawText, Uri uri) {
            this.type = type;
            this.title = title;
            this.meta = meta;
            this.rawText = rawText;
            this.uri = uri;
        }
    }

    private static final class WebPage {
        final String title;
        final String text;
        final String source;
        WebPage(String title, String text, String source) {
            this.title = title;
            this.text = text;
            this.source = source;
        }
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<DatasetItem> existingItems = new ArrayList<>();
    private final List<IncomingItem> incomingItems = new ArrayList<>();
    private AuthStore auth;
    private HttpClient http;
    private ShareApi shareApi;
    private FrameLayout root;
    private SharePayload payload;
    private ShareKind kind;
    private boolean busy;
    private boolean datasetCollecting;
    private boolean datasetStarted;
    private boolean clearAfter = true;
    private boolean businessInitialized;
    private String message = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarDefaults.applyLightEdgeToEdgeVisible(getWindow());
        root = new FrameLayout(this);
        root.setBackgroundColor(0x66000000);
        root.setOnClickListener(v -> finish());
        setContentView(root);
        PrivacyConsent consent = new PrivacyConsent(this);
        if (consent.isAccepted()) {
            continueAfterPrivacyConsent(savedInstanceState);
            return;
        }
        PrivacyConsentDialog.show(this, () -> {
            consent.accept();
            continueAfterPrivacyConsent(savedInstanceState);
        }, this::finishAndRemoveTask);
    }

    private void continueAfterPrivacyConsent(Bundle savedInstanceState) {
        if (businessInitialized) return;
        businessInitialized = true;
        ((VoiceDropApplication) getApplication()).activateConsentedServices();
        auth = new AuthStore(this);
        http = new HttpClient();
        shareApi = new ShareApi(auth, http);
        payload = loadPayload(getIntent());
        kind = classify(getIntent(), payload);
        if (isDatasetKind()) prepareIncomingItems();
        render();
        if (isDatasetKind()) startDatasetFlow();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private boolean isDatasetKind() {
        return kind != ShareKind.AUDIO && kind != ShareKind.IMAGE;
    }

    private ShareKind classify(Intent intent, SharePayload payload) {
        int streams = (payload.audio == null ? 0 : 1) + payload.images.size() + payload.docs.size();
        String mime = intent == null ? "" : intent.getType();
        String value = payload.text != null ? payload.text : (payload.webUrl == null ? "" : payload.webUrl.toString());
        if (payload.audio != null) return ShareKind.AUDIO;
        if (!payload.images.isEmpty()) return ShareKind.IMAGE;
        return ShareRouter.classify(intent == null ? "" : intent.getAction(), mime, streams,
                payload.text != null, value);
    }

    private SharePayload loadPayload(Intent intent) {
        SharePayload out = new SharePayload();
        if (intent == null) return out;
        out.title = intent.getStringExtra(Intent.EXTRA_TITLE);
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text != null) addSharedText(out, text.toString());
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null) addStream(out, stream, intent.getType());
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) for (Uri stream : streams) addStream(out, stream, intent.getType());
        }
        ClipData clip = intent.getClipData();
        if (clip != null) for (int index = 0; index < clip.getItemCount(); index++) {
            ClipData.Item item = clip.getItemAt(index);
            if (item.getUri() != null) addStream(out, item.getUri(), intent.getType());
            else if (item.getText() != null && out.text == null && out.webUrl == null) {
                addSharedText(out, item.getText().toString());
            }
        }
        return out;
    }

    private void addSharedText(SharePayload out, String value) {
        String url = ShareExtraction.firstWebUrl(value);
        if (!url.isEmpty()) out.webUrl = Uri.parse(url);
        else if (value != null && !value.trim().isEmpty()) out.text = value.trim();
    }

    private void addStream(SharePayload out, Uri uri, String mime) {
        if (uri == null) return;
        if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
            out.webUrl = uri;
            return;
        }
        if (ShareRouter.shouldIgnoreAuxiliaryStream(mime, out.webUrl != null, uri.getScheme())) return;
        String type = mime == null || mime.isEmpty() ? getContentResolver().getType(uri) : mime;
        String lower = type == null ? "" : type.toLowerCase(Locale.US);
        if (lower.startsWith("audio/")) out.audio = uri;
        else if (lower.startsWith("image/")) {
            if (!out.images.contains(uri)) out.images.add(uri);
        } else if (!out.docs.contains(uri)) out.docs.add(uri);
    }

    private void prepareIncomingItems() {
        incomingItems.clear();
        if (payload.text != null && !payload.text.trim().isEmpty()) {
            String value = payload.text.trim();
            incomingItems.add(new IncomingItem("text", "正在保存…",
                    ShareDatasetUi.formatChars(value.length()), value, null));
        }
        for (Uri doc : payload.docs) {
            incomingItems.add(new IncomingItem("doc", "正在读取文档…", fileName(doc), null, doc));
        }
        if (payload.webUrl != null) {
            incomingItems.add(new IncomingItem("web", "正在解析网页…", webHost(payload.webUrl), null, payload.webUrl));
        }
        if (incomingItems.isEmpty()) {
            IncomingItem failed = new IncomingItem("text", "无法读取分享内容", "请关闭后重试", null, null);
            failed.canRetry = false;
            failed.state = IncomingState.FAILED;
            incomingItems.add(failed);
        }
    }

    private void startDatasetFlow() {
        if (datasetStarted) return;
        datasetStarted = true;
        datasetCollecting = true;
        io.execute(() -> {
            try {
                existingItems.clear();
                existingItems.addAll(shareApi.fetchDataset());
            } catch (Exception error) {
                message = "数据集加载失败，可继续保存本次内容";
            }
            runOnUiThread(this::render);
            for (IncomingItem item : incomingItems) {
                if (item.state == IncomingState.FAILED) continue;
                collectIncoming(item);
                runOnUiThread(this::render);
            }
            datasetCollecting = false;
            runOnUiThread(this::render);
        });
    }

    private void collectIncoming(IncomingItem item) {
        try {
            String title;
            String text;
            String source;
            if ("web".equals(item.type)) {
                WebPage page = fetchWebPage(item.uri);
                if (page.text.isEmpty()) throw new IllegalStateException("网页解析失败");
                title = page.title;
                text = page.text;
                source = page.source;
            } else if ("doc".equals(item.type)) {
                text = tryReadDocument(item.uri).trim();
                if (text.isEmpty()) {
                    item.canRetry = false;
                    throw new IllegalStateException("无法读取内容");
                }
                source = fileName(item.uri);
                title = ShareExtraction.firstLineTitle(text, source);
            } else {
                text = item.rawText == null ? "" : item.rawText.trim();
                if (text.isEmpty()) throw new IllegalStateException("文字为空");
                source = "分享文本";
                title = payload.title == null || payload.title.trim().isEmpty()
                        ? ShareExtraction.firstLineTitle(text, "分享的文字") : payload.title.trim();
            }
            if (!shareApi.collectStyle(item.type, title, text, source)) {
                throw new IllegalStateException("保存失败");
            }
            item.title = title;
            item.chars = text.length();
            item.meta = "web".equals(item.type) ? source : ShareDatasetUi.formatChars(item.chars);
            item.state = IncomingState.DONE;
        } catch (Exception error) {
            item.title = "web".equals(item.type) ? "解析失败 · 仅存链接"
                    : (item.canRetry ? "保存失败 · 可重试" : "无法读取内容");
            item.meta = error.getMessage() == null ? "请稍后重试" : error.getMessage();
            item.state = IncomingState.FAILED;
        }
    }

    private void render() {
        if (root == null || isFinishing()) return;
        root.removeAllViews();
        LinearLayout sheet = vertical();
        sheet.setClickable(true);
        sheet.setOnClickListener(v -> {});
        sheet.setBackground(topRounded(Theme.BG, 20));
        SystemBarDefaults.applyBottomInsets(sheet, 0, dp(10), 0, dp(16));
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
        root.addView(sheet, new FrameLayout.LayoutParams(-1, maxHeight, Gravity.BOTTOM));

        if (isDatasetKind()) renderDatasetPage(sheet);
        else renderComposePage(sheet);
        if (busy) {
            ProgressBar progress = new ProgressBar(this);
            root.addView(progress, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        }
    }

    private void renderDatasetPage(LinearLayout sheet) {
        sheet.addView(datasetHeader());
        BouncyScrollView scroll = new BouncyScrollView(this);
        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(2), dp(16), dp(10));
        for (DatasetItem item : existingItems) content.addView(existingRow(item));
        if (!incomingItems.isEmpty()) {
            content.addView(sectionHeader());
            for (IncomingItem item : incomingItems) content.addView(incomingRow(item));
        }
        scroll.addView(content);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        sheet.addView(datasetFooter());
    }

    private View datasetHeader() {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(14), dp(12), dp(12));
        LinearLayout copy = vertical();
        copy.addView(text("风格数据集", 21, Theme.INK, Typeface.BOLD));
        copy.addView(text("已收集 " + collectedCount() + " 项 · "
                + ShareDatasetUi.formatTotalChars(collectedChars()), 13, Theme.SECONDARY, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("关闭", 16, Theme.SECONDARY, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        row.addView(close, new LinearLayout.LayoutParams(dp(70), dp(44)));
        return row;
    }

    private View existingRow(DatasetItem item) {
        LinearLayout outer = vertical();
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(13), dp(4), dp(13));
        row.addView(iconBadge(item.type, false), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = vertical();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(12), 0, 0, 0);
        TextView title = text(item.title, 15, Theme.INK, Typeface.NORMAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);
        String date = ShareDatasetUi.chineseDate(item.collectedAt);
        String subtitle = ShareDatasetUi.typeLabel(item.type) + " · " + ShareDatasetUi.itemMeta(item)
                + (date.isEmpty() ? "" : " · " + date);
        TextView meta = text(subtitle, 12, 0xffa79f93, Typeface.NORMAL);
        meta.setSingleLine(true);
        copy.addView(meta);
        row.addView(copy, copyLp);
        outer.addView(row);
        View divider = new View(this);
        divider.setBackgroundColor(0xffefe7d9);
        outer.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        return outer;
    }

    private View sectionHeader() {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(16), dp(4), dp(6));
        TextView label = text("本次新增", 12, 0xffc0682e, Typeface.BOLD);
        label.setLetterSpacing(0.15f);
        row.addView(label);
        View line = new View(this);
        line.setBackgroundColor(0xffe8c9b8);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(0, dp(1), 1);
        lineLp.setMargins(dp(10), 0, 0, 0);
        row.addView(line, lineLp);
        return row;
    }

    private View incomingRow(IncomingItem item) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(13), dp(12), dp(13));
        boolean done = item.state == IncomingState.DONE;
        String displayType = done ? "text" : item.type;
        row.setBackground(roundStroke(done ? 0xfffbf1e9 : Theme.CARD, 12,
                done ? 0xffe8c9b8 : 0xffe8dfd0, 1));
        row.addView(iconBadge(displayType, done), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = vertical();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(12), 0, dp(8), 0);
        TextView title = text(item.title, 15, done ? 0xff9a4a30 : Theme.SECONDARY,
                done ? Typeface.BOLD : Typeface.NORMAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);
        String displayMeta = done ? ShareDatasetUi.formatChars(item.chars) : item.meta;
        String subtitle = ShareDatasetUi.typeLabel(displayType) + " · " + displayMeta
                + (done ? " · 刚刚" : "");
        TextView meta = text(subtitle, 12, done ? 0xffa9674d : 0xff8a8175, Typeface.NORMAL);
        meta.setSingleLine(true);
        copy.addView(meta);
        row.addView(copy, copyLp);
        if (item.state == IncomingState.SAVING) {
            ProgressBar progress = new ProgressBar(this);
            progress.getIndeterminateDrawable().setColorFilter(Theme.AMBER, PorterDuff.Mode.SRC_IN);
            row.addView(progress, new LinearLayout.LayoutParams(dp(22), dp(22)));
        } else {
            ImageView status = new ImageView(this);
            status.setImageResource(done ? R.drawable.ic_check_flat : R.drawable.ic_settings_info);
            status.setColorFilter(done ? Theme.ACCENT : 0xffa79f93, PorterDuff.Mode.SRC_IN);
            row.addView(status, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(lp);
        if (item.state == IncomingState.FAILED && item.canRetry) {
            row.setOnClickListener(v -> retryIncoming(item));
        }
        return row;
    }

    private void retryIncoming(IncomingItem item) {
        if (busy || datasetCollecting || item.state != IncomingState.FAILED || !item.canRetry) return;
        item.state = IncomingState.SAVING;
        item.title = "web".equals(item.type) ? "正在解析网页…" : "正在保存…";
        datasetCollecting = true;
        message = "";
        render();
        io.execute(() -> {
            collectIncoming(item);
            datasetCollecting = false;
            runOnUiThread(this::render);
        });
    }

    private View datasetFooter() {
        LinearLayout footer = vertical();
        footer.setPadding(dp(20), dp(10), dp(20), 0);
        View divider = new View(this);
        divider.setBackgroundColor(0xffefe7d9);
        footer.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout clearRow = horizontal();
        clearRow.setGravity(Gravity.CENTER_VERTICAL);
        clearRow.setPadding(0, dp(8), 0, dp(8));
        ImageView check = new ImageView(this);
        check.setImageResource(clearAfter ? R.drawable.ic_checkbox_checked_flat : R.drawable.ic_checkbox_unchecked_flat);
        clearRow.addView(check, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView clear = text("提取后清空数据集", 14, 0xff6b6357, Typeface.NORMAL);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, -2, 1);
        clearLp.setMargins(dp(7), 0, 0, 0);
        clearRow.addView(clear, clearLp);
        clearRow.addView(text("下次从零开始", 12, 0xffa79f93, Typeface.NORMAL));
        clearRow.setOnClickListener(v -> { if (!busy) { clearAfter = !clearAfter; render(); } });
        footer.addView(clearRow);
        if (!message.isEmpty()) {
            TextView note = text(message, 12, 0xffc0682e, Typeface.NORMAL);
            note.setGravity(Gravity.CENTER);
            footer.addView(note);
        } else if (collectedCount() > 0 && collectedChars() < ShareDatasetUi.MIN_EXTRACT_CHARS) {
            TextView note = text("素材还太少（共 " + collectedChars() + " 字），攒到 300 字以上再提取",
                    12, 0xffc0682e, Typeface.NORMAL);
            note.setGravity(Gravity.CENTER);
            footer.addView(note);
        }
        LinearLayout actions = horizontal();
        actions.setPadding(0, dp(8), 0, 0);
        TextView more = text("继续收集", 15, Theme.INK, Typeface.BOLD);
        more.setGravity(Gravity.CENTER);
        more.setBackground(roundStroke(Theme.CARD, 11, 0xffe2d8c8, 1));
        more.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(dp(110), dp(50));
        actions.addView(more, moreLp);
        boolean enabled = !busy && !datasetCollecting && collectedChars() >= ShareDatasetUi.MIN_EXTRACT_CHARS;
        View extract = datasetExtractAction(enabled);
        LinearLayout.LayoutParams extractLp = new LinearLayout.LayoutParams(0, dp(50), 1);
        extractLp.setMargins(dp(10), 0, 0, 0);
        actions.addView(extract, extractLp);
        footer.addView(actions);
        return footer;
    }

    private View datasetExtractAction(boolean enabled) {
        LinearLayout action = horizontal();
        action.setGravity(Gravity.CENTER);
        action.setBackground(round(enabled ? Theme.ACCENT : 0xffd9b3a8, 11));
        if (busy) {
            ProgressBar progress = new ProgressBar(this);
            progress.getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(18), dp(18));
            progressLp.setMargins(0, 0, dp(8), 0);
            action.addView(progress, progressLp);
        } else {
            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_extract_style);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
            iconLp.setMargins(0, 0, dp(8), 0);
            action.addView(icon, iconLp);
        }
        action.addView(text(busy ? "处理中…" : "提取文章风格", 16, Color.WHITE, Typeface.BOLD));
        action.setEnabled(enabled);
        action.setOnClickListener(v -> extractStyle());
        return action;
    }

    private View iconBadge(String type, boolean highlighted) {
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(round(highlighted ? 0xfff3ddcb : 0xfff1ebe2, 10));
        ImageView icon = new ImageView(this);
        int iconRes = "web".equals(type) ? R.drawable.ic_globe_flat : R.drawable.ic_text_lines_flat;
        icon.setImageResource(iconRes);
        icon.setColorFilter(highlighted ? 0xffc0682e : 0xff6b6357, PorterDuff.Mode.SRC_IN);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        badge.addView(icon, iconLp);
        return badge;
    }

    private int collectedCount() {
        int count = existingItems.size();
        for (IncomingItem item : incomingItems) if (item.state == IncomingState.DONE) count++;
        return count;
    }

    private int collectedChars() {
        int chars = ShareDatasetUi.totalChars(existingItems);
        for (IncomingItem item : incomingItems) if (item.state == IncomingState.DONE) chars += Math.max(0, item.chars);
        return chars;
    }

    private void extractStyle() {
        if (busy || datasetCollecting || collectedChars() < ShareDatasetUi.MIN_EXTRACT_CHARS) return;
        busy = true;
        message = "";
        render();
        io.execute(() -> {
            try {
                List<DatasetItem> current = shareApi.fetchDataset();
                if (ShareDatasetUi.totalChars(current) < ShareDatasetUi.MIN_EXTRACT_CHARS) {
                    throw new IllegalStateException("素材还太少，至少需要 300 字");
                }
                String taskName = ShareApi.styleExtractTaskName(clearAfter, ZonedDateTime.now());
                HttpClient.Response upload = http.putBytes(Api.filesBase() + "/upload/" + Api.path(taskName),
                        auth.bearer(), "audio/mp4", SilentAudio.data());
                if (!upload.ok()) throw new IllegalStateException("文风提取任务创建失败（" + upload.code + "）");
                shareApi.triggerMine();
                runOnUiThread(() -> {
                    SimpleToast.show(this, "正在提取文章风格，可在“我的录音”查看进度");
                    // SimpleToast is attached to this Activity, so leave enough time for the
                    // confirmation to be read before removing the share window.
                    root.postDelayed(this::finishAndRemoveTask, 1400);
                });
            } catch (Exception error) {
                busy = false;
                message = error.getMessage() == null ? "提取失败，请稍后重试" : error.getMessage();
                runOnUiThread(this::render);
            }
        });
    }

    private void renderComposePage(LinearLayout sheet) {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(14), dp(12), dp(12));
        header.addView(text(kind == ShareKind.AUDIO ? "从这段录音成文" : "看图写一篇",
                21, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("关闭", 16, Theme.SECONDARY, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(70), dp(44)));
        sheet.addView(header);
        BouncyScrollView scroll = new BouncyScrollView(this);
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        if (kind == ShareKind.AUDIO) {
            content.addView(text("确认后上传音频，VoiceDrop 会自动转写并生成文章。", 14,
                    Theme.SECONDARY, Typeface.NORMAL));
            content.addView(infoCard("音频已就绪 · 预计消耗约 " + audioCostEstimate() + " 算力"));
        } else {
            content.addView(text(payload.images.size() + " 张图片，确认后开始看图成文。", 14,
                    Theme.SECONDARY, Typeface.NORMAL));
            LinearLayout grid = horizontal();
            for (Uri uri : payload.images) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                io.execute(() -> {
                    try {
                        Bitmap bitmap = ArticlePhotoInsert.decodeSampledBitmap(readUri(uri), 240);
                        runOnUiThread(() -> image.setImageBitmap(bitmap));
                    } catch (Exception ignored) {}
                });
                LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(dp(76), dp(76));
                imageLp.setMargins(0, dp(12), dp(8), 0);
                grid.addView(image, imageLp);
            }
            content.addView(grid);
        }
        scroll.addView(content);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button action = button(busy ? "处理中…" : "开始生成文章", Color.WHITE, Theme.ACCENT,
                16, Typeface.BOLD);
        action.setEnabled(!busy);
        action.setOnClickListener(v -> runComposeAction());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, dp(50));
        actionLp.setMargins(dp(20), dp(10), dp(20), 0);
        sheet.addView(action, actionLp);
    }

    private void runComposeAction() {
        if (busy) return;
        busy = true;
        render();
        io.execute(() -> {
            try {
                if (kind == ShareKind.AUDIO) generateFromAudio();
                else generateFromImages();
                runOnUiThread(this::finishAndRemoveTask);
            } catch (Exception error) {
                busy = false;
                toast("处理失败：" + error.getMessage());
                runOnUiThread(this::render);
            }
        });
    }

    private void generateFromAudio() throws Exception {
        if (payload.audio == null) throw new IllegalStateException("没有音频");
        String name = RecordingName.make(ZonedDateTime.now(), audioDurationSeconds(payload.audio), null);
        File tmp = copyUri(payload.audio, "share-audio", ".m4a");
        HttpClient.Response upload = http.putFile(Api.filesBase() + "/upload/" + Api.path(name),
                auth.bearer(), "audio/mp4", tmp);
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        if (!upload.ok()) throw new IllegalStateException("音频上传失败（" + upload.code + "）");
        shareApi.triggerMine();
    }

    private void generateFromImages() throws Exception {
        if (payload.images.isEmpty()) throw new IllegalStateException("没有图片");
        ZonedDateTime now = ZonedDateTime.now();
        String sessionTs = RecordingName.timestamp(now);
        int uploaded = 0;
        for (int index = 0; index < payload.images.size(); index++) {
            Bitmap bitmap = ArticlePhotoInsert.decodeSampledBitmap(readUri(payload.images.get(index)), 1440);
            byte[] jpeg = ArticlePhotoInsert.fitJpeg(bitmap, 1440, 900_000, 86);
            if (jpeg == null) continue;
            String key = RecordingName.photoKey(sessionTs, index);
            if (http.putBytes(Api.filesBase() + "/upload/" + Api.path(key), auth.bearer(),
                    "image/jpeg", jpeg).ok()) uploaded++;
        }
        if (uploaded == 0) throw new IllegalStateException("图片上传失败");
        String audioName = RecordingName.make(now, 0, null);
        HttpClient.Response task = http.putBytes(Api.filesBase() + "/upload/" + Api.path(audioName),
                auth.bearer(), "audio/mp4", SilentAudio.data());
        if (!task.ok()) throw new IllegalStateException("生成任务创建失败（" + task.code + "）");
        shareApi.triggerMine();
    }

    private WebPage fetchWebPage(Uri uri) throws Exception {
        String url = uri == null ? "" : uri.toString();
        String host = webHost(uri);
        HttpClient.RequestOptions options = new HttpClient.RequestOptions()
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Mobile Safari/537.36 VoiceDrop/1.0")
                .readTimeoutMs(15_000);
        HttpClient.Response response = http.get(url, "", options);
        if (!response.ok()) return new WebPage(host, "", host);
        String html = new String(response.body, StandardCharsets.UTF_8);
        String text = ShareExtraction.readableHtml(html);
        return new WebPage(ShareExtraction.htmlTitle(html, host), text, host);
    }

    private String tryReadDocument(Uri uri) {
        try {
            byte[] data = readUri(uri);
            if (data.length == 0) return "";
            if (data.length > 3 && data[0] == 'P' && data[1] == 'K') return readDocx(data);
            String value = new String(data, StandardCharsets.UTF_8);
            if (value.indexOf('\0') >= 0) return "";
            if (fileName(uri).toLowerCase(Locale.US).endsWith(".rtf") || value.trim().startsWith("{\\rtf")) {
                value = value.replaceAll("\\\\par[d]?\\s?", "\n")
                        .replaceAll("\\\\'[0-9a-fA-F]{2}", "")
                        .replaceAll("\\\\[a-zA-Z]+-?\\d* ?", "")
                        .replaceAll("[{}]", "");
            }
            return value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readDocx(byte[] data) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = zip.read(buffer)) >= 0) out.write(buffer, 0, count);
                String xml = out.toString(StandardCharsets.UTF_8.name());
                return ShareExtraction.readableHtml(xml.replace("</w:p>", "</p>")
                        .replace("<w:br/>", "<br>").replace("<w:tab/>", " "));
            }
        }
        return "";
    }

    private double audioDurationSeconds(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return ms == null ? 0 : Math.max(0, Long.parseLong(ms) / 1000.0);
        } catch (Exception error) {
            return 0;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private int audioCostEstimate() {
        return Math.max(1, (int) Math.ceil(audioDurationSeconds(payload.audio) / 3600.0 * 0.8 * 23) + 2);
    }

    private File copyUri(Uri uri, String prefix, String suffix) throws Exception {
        File tmp = File.createTempFile(prefix, suffix, getCacheDir());
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IllegalStateException("无法读取分享文件");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
        }
        return tmp;
    }

    private byte[] readUri(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("无法读取分享文件");
            return HttpClient.readAll(in);
        }
    }

    private String fileName(Uri uri) {
        if (uri == null) return "分享文档";
        String name = uri.getLastPathSegment();
        return name == null || name.trim().isEmpty() ? "分享文档" : name;
    }

    private String webHost(Uri uri) {
        if (uri == null) return "网页";
        String host = uri.getHost();
        return host == null || host.trim().isEmpty() ? uri.toString() : host;
    }

    private View infoCard(String value) {
        TextView card = text(value, 15, Theme.INK, Typeface.NORMAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundStroke(Theme.CARD, 12, 0xffe8dfd0, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(lp);
        return card;
    }

    private Button button(String value, int textColor, int backgroundColor, int sp, int style) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(textColor);
        button.setTextSize(sp);
        button.setTypeface(Typeface.DEFAULT, style);
        button.setBackground(round(backgroundColor, 11));
        return button;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(dp(3), 1.0f);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable topRounded(int color, int radiusDp) {
        GradientDrawable drawable = round(color, radiusDp);
        float radius = dp(radiusDp);
        drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return drawable;
    }

    private GradientDrawable roundStroke(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = round(color, radiusDp);
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private void toast(String value) {
        runOnUiThread(() -> SimpleToast.show(this, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
