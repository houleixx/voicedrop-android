package com.baixingai.voicedrop;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
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
import com.baixingai.voicedrop.share.ShareApi;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ShareCollectActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AuthStore auth;
    private HttpClient http;
    private ShareApi shareApi;
    private FrameLayout root;
    private SharePayload payload;
    private ShareKind kind;
    private boolean busy;
    private boolean businessInitialized;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(0x66000000);
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
        render();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private ShareKind classify(Intent intent, SharePayload payload) {
        int streams = (payload.audio == null ? 0 : 1) + payload.images.size() + payload.docs.size();
        String mime = intent == null ? "" : intent.getType();
        String text = payload.text != null ? payload.text : (payload.webUrl == null ? "" : payload.webUrl.toString());
        if (payload.audio != null) return ShareKind.AUDIO;
        if (!payload.images.isEmpty()) return ShareKind.IMAGE;
        if (!payload.docs.isEmpty()) return ShareRouter.classify(intent.getAction(), mime, streams, payload.text != null, text);
        return ShareRouter.classify(intent == null ? "" : intent.getAction(), mime, streams, payload.text != null, text);
    }

    private SharePayload loadPayload(Intent intent) {
        SharePayload out = new SharePayload();
        if (intent == null) return out;
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text != null) {
            String value = text.toString();
            if (value.trim().matches("(?i)^https?://\\S+$")) out.webUrl = Uri.parse(value.trim());
            else out.text = value;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null) addStream(out, stream, intent.getType());
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) for (Uri stream : streams) addStream(out, stream, intent.getType());
        }
        return out;
    }

    private void addStream(SharePayload out, Uri uri, String mime) {
        String type = mime == null ? getContentResolver().getType(uri) : mime;
        String lower = type == null ? "" : type.toLowerCase(Locale.US);
        if (lower.startsWith("audio/")) out.audio = uri;
        else if (lower.startsWith("image/")) out.images.add(uri);
        else out.docs.add(uri);
    }

    private void render() {
        root.removeAllViews();
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        SystemBarDefaults.applyBottomInsets(sheet, dp(20), dp(14), dp(20), dp(22));
        sheet.setBackgroundColor(0xfffaf6ef);
        root.addView(sheet, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        View grabber = new View(this);
        grabber.setBackgroundColor(0xffddd3c2);
        LinearLayout.LayoutParams grabLp = new LinearLayout.LayoutParams(dp(38), dp(5));
        grabLp.gravity = Gravity.CENTER_HORIZONTAL;
        grabLp.setMargins(0, 0, 0, dp(14));
        sheet.addView(grabber, grabLp);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(titleForKind(), 20, Theme.INK, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("关闭", 16, Theme.SECONDARY, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(64), dp(42)));
        sheet.addView(header);

        BouncyScrollView scroll = new BouncyScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (kind == ShareKind.AUDIO) renderAudio(content);
        else if (kind == ShareKind.IMAGE) renderImages(content);
        else renderStyleDataset(content);

        Button action = new Button(this);
        action.setText(actionText());
        action.setTextColor(0xffffffff);
        action.setTextSize(16);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setBackgroundColor(Theme.RED);
        action.setOnClickListener(v -> runAction());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, dp(52));
        actionLp.setMargins(0, dp(12), 0, 0);
        sheet.addView(action, actionLp);
        if (busy) {
            ProgressBar progress = new ProgressBar(this);
            root.addView(progress, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        }
    }

    private void renderAudio(LinearLayout content) {
        content.addView(text("音频已就绪 · 预计消耗约 " + audioCostEstimate() + " 算力", 14, Theme.SECONDARY, Typeface.NORMAL));
        content.addView(card("转写 + 成文一步完成"));
    }

    private void renderImages(LinearLayout content) {
        content.addView(text(payload.images.size() + " 张图片 · 看图成文", 14, Theme.SECONDARY, Typeface.NORMAL));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        for (Uri uri : payload.images) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            io.execute(() -> {
                try {
                    byte[] data = readUri(uri);
                    Bitmap bitmap = ArticlePhotoInsert.decodeSampledBitmap(data, 240);
                    runOnUiThread(() -> image.setImageBitmap(bitmap));
                } catch (Exception ignored) {
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(76));
            lp.setMargins(0, dp(12), dp(8), 0);
            grid.addView(image, lp);
        }
        content.addView(grid);
        content.addView(card("预计消耗约 " + Math.max(1, 2 + payload.images.size()) + " 算力"));
    }

    private void renderStyleDataset(LinearLayout content) {
        String label = payload.webUrl != null ? "网页将作为风格语料收集" : "文本/文档将作为风格语料收集";
        content.addView(text(label, 14, Theme.SECONDARY, Typeface.NORMAL));
        content.addView(card("收集后可提取文章风格；素材少于 300 字时不会开始提取。"));
    }

    private TextView card(String value) {
        TextView tv = text(value, 15, Theme.INK, Typeface.NORMAL);
        tv.setPadding(dp(14), dp(14), dp(14), dp(14));
        tv.setBackgroundColor(0xffffffff);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private String titleForKind() {
        if (kind == ShareKind.AUDIO) return "从这段录音成文";
        if (kind == ShareKind.IMAGE) return "看图写一篇";
        return "风格数据集";
    }

    private String actionText() {
        if (kind == ShareKind.AUDIO || kind == ShareKind.IMAGE) return "开始生成文章";
        return "收集并提取文章风格";
    }

    private void runAction() {
        if (busy) return;
        busy = true;
        render();
        io.execute(() -> {
            try {
                if (kind == ShareKind.AUDIO) generateFromAudio();
                else if (kind == ShareKind.IMAGE) generateFromImages();
                else collectAndExtractStyle();
                toast("已送入 VoiceDrop");
                finish();
            } catch (Exception e) {
                busy = false;
                toast("处理失败：" + e.getMessage());
                runOnUiThread(this::render);
            }
        });
    }

    private void generateFromAudio() throws Exception {
        if (payload.audio == null) throw new IllegalStateException("没有音频");
        ZonedDateTime now = ZonedDateTime.now();
        String name = RecordingName.make(now, audioDurationSeconds(payload.audio), null);
        File tmp = copyUri(payload.audio, "share-audio", ".m4a");
        http.putFile(Api.filesBase() + "/upload/" + Api.path(name), auth.bearer(), "audio/mp4", tmp);
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        shareApi.triggerMine();
    }

    private void generateFromImages() throws Exception {
        if (payload.images.isEmpty()) throw new IllegalStateException("没有图片");
        ZonedDateTime now = ZonedDateTime.now();
        String sessionTs = RecordingName.timestamp(now);
        int uploaded = 0;
        for (int i = 0; i < payload.images.size(); i++) {
            byte[] raw = readUri(payload.images.get(i));
            Bitmap bitmap = ArticlePhotoInsert.decodeSampledBitmap(raw, 1440);
            byte[] jpeg = ArticlePhotoInsert.fitJpeg(bitmap, 1440, 900_000, 86);
            if (jpeg == null) continue;
            String key = RecordingName.photoKey(sessionTs, i);
            if (http.putBytes(Api.filesBase() + "/upload/" + Api.path(key), auth.bearer(), "image/jpeg", jpeg).ok()) uploaded++;
        }
        if (uploaded == 0) throw new IllegalStateException("图片上传失败");
        String audioName = RecordingName.make(now, 0, null);
        http.putBytes(Api.filesBase() + "/upload/" + Api.path(audioName), auth.bearer(), "audio/mp4", SilentAudio.data());
        shareApi.triggerMine();
    }

    private void collectAndExtractStyle() throws Exception {
        int chars = 0;
        if (payload.text != null && !payload.text.trim().isEmpty()) {
            String text = payload.text.trim();
            chars += text.length();
            shareApi.collectStyle("text", ShareExtraction.firstLineTitle(text, "分享的文字"), text, "分享文本");
        }
        if (payload.webUrl != null) {
            String url = payload.webUrl.toString();
            chars += url.length();
            shareApi.collectStyle("web", url, url, payload.webUrl.getHost());
        }
        for (Uri doc : payload.docs) {
            String text = tryReadText(doc);
            if (text.trim().isEmpty()) continue;
            chars += text.length();
            shareApi.collectStyle("doc", ShareExtraction.firstLineTitle(text, doc.getLastPathSegment()), text, doc.getLastPathSegment());
        }
        if (chars < 300) throw new IllegalStateException("素材还太少，至少需要 300 字");
        String taskName = ShareApi.styleExtractTaskName(true, ZonedDateTime.now());
        http.putBytes(Api.filesBase() + "/upload/" + Api.path(taskName), auth.bearer(), "audio/mp4", SilentAudio.data());
        shareApi.triggerMine();
    }

    private double audioDurationSeconds(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return ms == null ? 0 : Math.max(0, Long.parseLong(ms) / 1000.0);
        } catch (Exception e) {
            return 0;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private int audioCostEstimate() {
        return Math.max(1, (int) Math.ceil(audioDurationSeconds(payload.audio) / 3600.0 * 0.8 * 23) + 2);
    }

    private String tryReadText(Uri uri) {
        try {
            byte[] data = readUri(uri);
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private File copyUri(Uri uri, String prefix, String suffix) throws Exception {
        File tmp = File.createTempFile(prefix, suffix, getCacheDir());
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IllegalStateException("无法读取分享文件");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
        return tmp;
    }

    private byte[] readUri(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("无法读取分享文件");
            return HttpClient.readAll(in);
        }
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(dp(3), 1.0f);
        return view;
    }

    private void toast(String message) {
        runOnUiThread(() -> SimpleToast.show(this, message));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
