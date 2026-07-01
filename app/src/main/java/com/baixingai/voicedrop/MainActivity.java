package com.baixingai.voicedrop;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.baixingai.voicedrop.audio.AudioRecorder;
import com.baixingai.voicedrop.audio.AsrDictationSession;
import com.baixingai.voicedrop.audio.Uploader;
import com.baixingai.voicedrop.core.ArticleBody;
import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.data.ArticleDoc;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.BlockStore;
import com.baixingai.voicedrop.data.CommunityStore;
import com.baixingai.voicedrop.data.CommunityTerms;
import com.baixingai.voicedrop.data.DeviceLinkCrypto;
import com.baixingai.voicedrop.data.DeviceLinkSession;
import com.baixingai.voicedrop.data.DeviceLinkStore;
import com.baixingai.voicedrop.data.ExportManager;
import com.baixingai.voicedrop.data.LibraryStore;
import com.baixingai.voicedrop.data.MinedArticle;
import com.baixingai.voicedrop.data.Prefs;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.data.UsageStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.net.ArticleEditSession;
import com.baixingai.voicedrop.net.StatusSession;
import com.baixingai.voicedrop.ui.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private AuthStore auth;
    private Prefs prefs;
    private HttpClient http;
    private LibraryStore library;
    private CommunityStore community;
    private BlockStore blockStore;
    private CommunityTerms communityTerms;
    private SettingsStore settingsStore;
    private UsageStore usageStore;
    private DeviceLinkStore deviceLinkStore;
    private ExportManager exportManager;
    private DeviceLinkSession deviceLinkSession;
    private String pendingLinkPairingId;
    private String pendingLinkPubkey;
    private Uploader uploader;
    private AudioRecorder recorder;
    private StatusSession statusSession;
    private ArticleEditSession editSession;
    private AsrDictationSession dictationSession;
    private MediaPlayer mediaPlayer;
    private ZonedDateTime recordingStart;
    private Recording insertPhotoTarget;
    private FrameLayout root;
    private List<Recording> recordings = new ArrayList<>();
    private List<CommunityStore.Post> posts = new ArrayList<>();
    private final List<CapturedPhoto> capturedPhotos = new ArrayList<>();
    private boolean communityTab;
    private boolean loading;
    private int articleIndex;                // current article section index in multi-article docs
    private String replyToShareId;           // when recording a reply to a community post
    private boolean communityRecording;      // true while recording a community reply
    private final Runnable communityTimerTick = new Runnable() {
        @Override public void run() {
            if (recorder != null && recorder.isRecording() && communityRecording) {
                updateCommunityRecordingTimer();
                main.postDelayed(this, 500);
            }
        }
    };
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (recorder != null && recorder.isRecording()) {
                showRecording(false);
                main.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = new AuthStore(this);
        prefs = new Prefs(this);
        http = new HttpClient();
        library = new LibraryStore(auth, http);
        community = new CommunityStore(auth, http);
        blockStore = new BlockStore(this);
        communityTerms = new CommunityTerms(this);
        settingsStore = new SettingsStore(auth, http);
        usageStore = new UsageStore(auth, http);
        deviceLinkStore = new DeviceLinkStore(auth, http);
        exportManager = new ExportManager(this, auth, http, library);
        uploader = new Uploader(this, auth, prefs, http);
        recorder = new AudioRecorder(this);
        statusSession = new StatusSession(auth, new StatusSession.Listener() {
            @Override public void onPhase(String stem, String status) {
                main.post(() -> markPhase(stem, status));
            }

            @Override public void onDone(String stem, String status) {
                main.post(MainActivity.this::refreshAndDrain);
            }

            @Override public void onLinkRequest(String pairingId, String code, String pubkey) {
                main.post(() -> showDeviceLinkApproval(pairingId, code, pubkey));
            }

            @Override public void onLinkRelease(String pairingId) {
                main.post(() -> releaseDeviceLink(pairingId));
            }

            @Override public void onError(String message) {
                // Silent: the list still refreshes on resume/upload.
            }
        });
        root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        setContentView(root);
        // Immersive status bar: transparent, content extends behind it
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        handleShareIntent(getIntent());
        showHome();
        refreshAndDrain();
        statusSession.connect();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAndDrain();
        if (statusSession != null) statusSession.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (statusSession != null) statusSession.close();
        if (editSession != null) editSession.close();
        if (dictationSession != null) dictationSession.stop();
        if (deviceLinkSession != null) deviceLinkSession.cancel();
        stopPlayback();
        io.shutdownNow();
    }

    private void markPhase(String stem, String status) {
        for (Recording rec : recordings) {
            if (rec.stem().equals(stem)) rec.phase = status;
        }
        if (!communityTab) showHome();
    }

    private void refreshAndDrain() {
        loading = true;
        showHome();
        io.execute(() -> {
            uploader.drainPending();
            try {
                recordings = library.load(uploader.pendingNames());
                if (communityTab) {
                    List<CommunityStore.Post> all = community.list();
                    // Filter out blocked authors (Apple 1.2)
                    posts = new ArrayList<>();
                    for (CommunityStore.Post p : all) {
                        if (!blockStore.isBlocked(p.author)) posts.add(p);
                    }
                }
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            loading = false;
            main.post(this::showHome);
        });
    }

    private void showHome() {
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        top.setPadding(dp(18), dp(16) + getStatusBarHeight(), dp(8), dp(8));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(0, dp(4), 0, 0);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView brand = text("▮▮▮ VoiceDrop 口述", 13, Theme.SECONDARY, Typeface.BOLD);
        brand.setLetterSpacing(0.05f);
        titles.addView(brand);
        TextView title = text("我的录音", 28, Theme.INK, Typeface.NORMAL);
        title.setPadding(0, dp(10), 0, 0);
        titles.addView(title);

        TextView settings = text("⚙", 28, Theme.SECONDARY, Typeface.NORMAL);
        settings.setGravity(Gravity.CENTER);
        top.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        settings.setOnClickListener(v -> showSettings());

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(14), dp(4), dp(14), dp(10));
        page.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        TextView myTab = tab("我的录音", !communityTab);
        tabs.addView(myTab);
        // Spacer between tabs
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        tabs.addView(spacer);
        TextView communityTabView = tab("VD社区", communityTab);
        tabs.addView(communityTabView);
        myTab.setOnClickListener(v -> { communityTab = false; showHome(); });
        communityTabView.setOnClickListener(v -> { communityTab = true; refreshAndDrain(); });

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(6), dp(14), dp(16));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (communityTab) {
            renderCommunityList(list);
        } else if (loading && recordings.isEmpty()) {
            TextView empty = text("正在加载…", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
        } else if (recordings.isEmpty()) {
            TextView empty = text("轻点下方按钮开始第一条录音", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
        } else {
            for (Recording rec : recordings) list.addView(recordingRow(rec));
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(0, dp(10), 0, dp(18));
        if (!communityTab) page.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        // FAB: 68dp circle (use FrameLayout to prevent emoji stretching)
        FrameLayout fabContainer = new FrameLayout(this);
        fabContainer.setLayoutParams(new LinearLayout.LayoutParams(dp(68), dp(68)));
        TextView fab = new TextView(this);
        fab.setText("🎙");
        fab.setTextSize(28);
        fab.setTextColor(0xffffffff);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(round(Theme.RED, 34));
        fab.setPadding(0, dp(8), 0, 0);
        // Fixed size via FrameLayout params, not min
        fab.setLayoutParams(new FrameLayout.LayoutParams(dp(68), dp(68)));
        fabContainer.addView(fab);
        bottom.addView(fabContainer);

        TextView label = text("轻点录音", 12, Theme.SECONDARY, Typeface.NORMAL);
        label.setLetterSpacing(0.08f);
        label.setPadding(0, dp(8), 0, 0);
        label.setGravity(Gravity.CENTER);
        label.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        bottom.addView(label);
        fab.setOnClickListener(v -> startRecordingFlow());
    }

    private TextView tab(String label, boolean selected) {
        TextView view = text(label, 15, selected ? 0xffffffff : Theme.SECONDARY, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(22), dp(10), dp(22), dp(10));
        view.setBackground(round(selected ? Theme.RED : 0x00ffffff, 20));
        return view;
    }

    private void renderCommunityList(LinearLayout list) {
        if (loading && posts.isEmpty()) {
            TextView empty = text("正在加载 VD社区…", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
        } else if (posts.isEmpty()) {
            TextView empty = text("社区暂无文章", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
        } else {
            for (CommunityStore.Post post : posts) list.addView(communityRow(post));
        }
    }

    private View communityRow(CommunityStore.Post post) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(round(Theme.CARD, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(lp);
        row.addView(text(post.title == null || post.title.isEmpty() ? "社区文章" : post.title, 17, Theme.INK, Typeface.BOLD));
        String dateStr = formatCommunityDate(post.firstSharedAt);
        TextView meta = text((post.author == null || post.author.isEmpty() ? "匿名作者" : post.author)
                + (dateStr.isEmpty() ? "" : " · " + dateStr), 13, Theme.FAINT, Typeface.NORMAL);
        meta.setPadding(0, dp(8), 0, 0);
        row.addView(meta);
        row.setOnClickListener(v -> openCommunityPost(post));
        return row;
    }

    private View recordingRow(Recording rec) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        row.setBackground(round(Theme.CARD, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(lp);

        // Wave icon: 3 vertical bars with spacing
        LinearLayout waveIcon = new LinearLayout(this);
        waveIcon.setOrientation(LinearLayout.HORIZONTAL);
        waveIcon.setGravity(Gravity.CENTER);
        waveIcon.setBackground(round(0xfffbeae7, 14));
        waveIcon.setPadding(dp(12), dp(12), dp(12), dp(12));
        addWaveBar(waveIcon, dp(3), dp(11));
        View gap1 = new View(this); gap1.setLayoutParams(new LinearLayout.LayoutParams(dp(4), 1));
        waveIcon.addView(gap1);
        addWaveBar(waveIcon, dp(3), dp(19));
        View gap2 = new View(this); gap2.setLayoutParams(new LinearLayout.LayoutParams(dp(4), 1));
        waveIcon.addView(gap2);
        addWaveBar(waveIcon, dp(3), dp(14));
        row.addView(waveIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(dp(14), 0, dp(8), 0);
        row.addView(meta, new LinearLayout.LayoutParams(0, -2, 1));
        meta.addView(text(rec.rowTitle(), 16, Theme.INK, Typeface.BOLD));

        LinearLayout sub = new LinearLayout(this);
        sub.setOrientation(LinearLayout.HORIZONTAL);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        sub.setPadding(0, dp(7), 0, 0);
        meta.addView(sub);
        sub.addView(text(rec.durationLabel().isEmpty() ? "录音" : rec.durationLabel(), 13, Theme.FAINT, Typeface.NORMAL));
        TextView chip = text("  " + rec.statusLabel() + "  ", 12,
                rec.hasArticles ? Theme.GREEN : Theme.AMBER,
                Typeface.NORMAL);
        chip.setBackground(round(rec.hasArticles ? Theme.GREEN_BG : Theme.AMBER_BG, 8));
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
        chipLp.setMargins(dp(8), 0, 0, 0);
        sub.addView(chip, chipLp);

        // Long-press on status chip to regenerate (iOS: 长按"已成文" badge 重新生成)
        if (rec.hasArticles) {
            chip.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("重新生成文章？")
                        .setMessage("会用相同的文风重新挖这篇文章，原文不变。")
                        .setPositiveButton("重新生成", (d, w) -> io.execute(() -> {
                            try {
                                boolean ok = library.restyle(rec, -1);
                                toast(ok ? "已请求重新生成" : "重新生成请求失败");
                                if (ok) refreshAndDrain();
                            } catch (Exception e) {
                                toast("重新生成请求失败：" + e.getMessage());
                            }
                        }))
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });
        }

        // Swipe-to-delete detection (iOS: swipe-left 删除)
        final float[] startX = {0};
        final boolean[] swiping = {false};
        row.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    startX[0] = event.getX();
                    swiping[0] = false;
                    return false;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - startX[0];
                    if (Math.abs(dx) > dp(40)) {
                        swiping[0] = true;
                        row.setTranslationX(dx < 0 ? dx / 3 : 0);
                        return true;
                    }
                    return false;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (swiping[0] && startX[0] - event.getX() > dp(40)) {
                        // Swiped left → confirm delete
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("删除这条录音？")
                                .setMessage("音频和已挖出的文章都会从云端删除，不可恢复。")
                                .setPositiveButton("删除", (d, w) -> io.execute(() -> {
                                    library.delete(rec);
                                    main.post(MainActivity.this::refreshAndDrain);
                                }))
                                .setNegativeButton("取消", null)
                                .show();
                    }
                    row.setTranslationX(0);
                    swiping[0] = false;
                    return false;
            }
            return false;
        });

        TextView chevron = text("›", 30, 0xffcfc6b6, Typeface.NORMAL);
        row.addView(chevron);
        row.setOnClickListener(v -> openRecording(rec));
        return row;
    }

    private void showRecording(boolean first) {
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        TextView status = text("●  正在录音", 14, Theme.SECONDARY, Typeface.NORMAL);
        status.setLetterSpacing(0.1f);
        status.setPadding(0, dp(18) + getStatusBarHeight(), 0, 0);
        page.addView(status);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        page.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));
        long elapsed = recorder.elapsedSeconds();
        center.addView(text(String.format("%02d:%02d", elapsed / 60, elapsed % 60), 78, Theme.INK, Typeface.NORMAL));
        TextView wave = text("▂▅▇▄█▆▃▇▄█▂▅▃", 32, Theme.RED, Typeface.NORMAL);
        wave.setPadding(0, dp(28), 0, 0);
        center.addView(wave);
        if (!capturedPhotos.isEmpty()) center.addView(recordingFilmstrip());

        FrameLayout bottom = new FrameLayout(this);
        page.addView(bottom, new LinearLayout.LayoutParams(-1, dp(140)));
        LinearLayout stopBox = new LinearLayout(this);
        stopBox.setOrientation(LinearLayout.VERTICAL);
        stopBox.setGravity(Gravity.CENTER);
        bottom.addView(stopBox, match());
        TextView stop = circleText("■", 68, Theme.RED, 26, 0xffffffff);
        stopBox.addView(stop);
        TextView stopLabel = text("点击停止", 12, Theme.SECONDARY, Typeface.NORMAL);
        stopLabel.setPadding(0, dp(8), 0, 0);
        stopBox.addView(stopLabel);
        stop.setOnClickListener(v -> stopRecordingFlow());

        TextView camera = circleText("📷", 48, Theme.CARD, 22, Theme.SECONDARY);
        FrameLayout.LayoutParams camLp = new FrameLayout.LayoutParams(dp(80), dp(80), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        camLp.setMargins(0, 0, dp(34), 0);
        bottom.addView(camera, camLp);
        camera.setOnClickListener(v -> openCamera());

        TextView album = circleText("＋", 44, Theme.CARD, 24, Theme.SECONDARY);
        FrameLayout.LayoutParams albumLp = new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        albumLp.setMargins(dp(34), 0, 0, 0);
        bottom.addView(album, albumLp);
        album.setOnClickListener(v -> pickRecordingPhotos());

        if (first) main.postDelayed(timerTick, 500);
    }

    private View recordingFilmstrip() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(dp(18), dp(18), dp(18), 0);
        scroll.addView(strip);
        for (int i = 0; i < capturedPhotos.size(); i++) {
            CapturedPhoto photo = capturedPhotos.get(i);
            FrameLayout tile = new FrameLayout(this);
            tile.setBackground(round(Theme.CARD, 10));
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(photo.bitmap);
            tile.addView(image, match());
            TextView close = text("×", 18, 0xffffffff, Typeface.BOLD);
            close.setGravity(Gravity.CENTER);
            close.setBackground(round(0x99000000, 16));
            FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.RIGHT | Gravity.TOP);
            tile.addView(close, closeLp);
            final int index = i;
            tile.setOnClickListener(v -> {
                if (index < capturedPhotos.size()) {
                    capturedPhotos.remove(index);
                    showRecording(false);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(74), dp(74));
            lp.setMargins(0, 0, dp(10), 0);
            strip.addView(tile, lp);
        }
        return scroll;
    }

    private void startRecordingFlow() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
            return;
        }
        try {
            recorder.start();
            recordingStart = recorder.startDate();
            capturedPhotos.clear();
            showRecording(true);
        } catch (Exception e) {
            toast("无法开始录音：" + e.getMessage());
        }
    }

    private void stopRecordingFlow() {
        AudioRecorder.Take take = recorder.stop(null);
        List<CapturedPhoto> photos = new ArrayList<>(capturedPhotos);
        capturedPhotos.clear();
        recordingStart = null;
        main.removeCallbacks(timerTick);
        showHome();
        if (take != null) {
            io.execute(() -> {
                uploadCapturedPhotos(photos);
                uploader.upload(take.file);
                try {
                    recordings = library.load(uploader.pendingNames());
                } catch (Exception e) {
                    toast("刷新失败：" + e.getMessage());
                }
                main.post(this::showHome);
            });
        }
    }

    private void openRecording(Recording rec) {
        if (!rec.hasArticles) {
            toast(rec.statusLabel());
            return;
        }
        io.execute(() -> {
            ArticleDoc doc = library.fetchDoc(rec);
            main.post(() -> showArticle(rec, doc));
        });
    }

    private void showArticle(Recording rec, ArticleDoc doc) {
        if (doc == null || doc.articles.isEmpty()) {
            toast("文章暂不可读");
            return;
        }
        articleIndex = 0;
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12) + getStatusBarHeight(), dp(12), dp(8));
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        TextView back = text("‹", 34, Theme.SECONDARY, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> showHome());
        TextView title = text(rec.rowTitle(), 20, Theme.INK, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(14), 0, dp(14), dp(10));
        actionsScroll.addView(actions);
        page.addView(actionsScroll, new LinearLayout.LayoutParams(-1, -2));
        addAction(actions, "播放", () -> playRecordingAudio(rec));
        addAction(actions, "分享", () -> shareRecording(rec, articleIndex));
        addAction(actions, "公众号", () -> publishWechat(rec));
        addAction(actions, "社区", () -> shareCommunityWithTermsGate(rec, null));
        addAction(actions, "改文章", () -> showVoiceEdit(rec));
        addAction(actions, "插入照片", () -> pickArticlePhoto(rec));
        addAction(actions, "撤销", () -> shiftArticleHead(rec, -1));
        addAction(actions, "重做", () -> shiftArticleHead(rec, 1));
        addAction(actions, "文风版本", () -> showStyleVersions(rec));
        addAction(actions, "导出", () -> exportArticle(rec, doc));
        addAction(actions, "删除", () -> deleteRecording(rec));

        // Article section chips (when >1 article)
        if (doc.articles.size() > 1) {
            HorizontalScrollView chipScroll = new HorizontalScrollView(this);
            chipScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            chipRow.setPadding(dp(20), dp(6), dp(20), dp(10));
            chipScroll.addView(chipRow);
            page.addView(chipScroll, new LinearLayout.LayoutParams(-1, -2));
            renderArticleChips(chipRow, doc, rec);
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderCurrentArticle(content, rec, doc);
    }

    private void renderArticleChips(LinearLayout chipRow, ArticleDoc doc, final Recording rec) {
        chipRow.removeAllViews();
        for (int i = 0; i < doc.articles.size(); i++) {
            final int idx = i;
            MinedArticle article = doc.articles.get(i);
            TextView chip = text(article.title, 13,
                    i == articleIndex ? 0xffffffff : Theme.SECONDARY, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(6), dp(14), dp(6));
            chip.setBackground(round(i == articleIndex ? Theme.RED : 0x00ffffff, 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
            lp.setMargins(0, 0, dp(8), 0);
            chipRow.addView(chip, lp);
            chip.setOnClickListener(v -> {
                articleIndex = idx;
                renderArticleChips(chipRow, doc, rec);
                // Re-render content: find the ScrollView content LinearLayout
                FrameLayout rootPage = (FrameLayout) root.getChildAt(0);
                // The ScrollView is the last child before the bottom
                for (int c = 0; c < rootPage.getChildCount(); c++) {
                    View child = rootPage.getChildAt(c);
                    if (child instanceof ScrollView) {
                        ScrollView scroll = (ScrollView) child;
                        LinearLayout content = (LinearLayout) scroll.getChildAt(0);
                        content.removeAllViews();
                        renderCurrentArticle(content, rec, doc);
                        break;
                    }
                }
            });
        }
    }

    private void renderCurrentArticle(LinearLayout content, Recording rec, ArticleDoc doc) {
        content.removeAllViews();
        content.setTag(rec);
        MinedArticle article = doc.articles.get(articleIndex);
        content.addView(text(article.title, 22, Theme.INK, Typeface.BOLD));
        renderArticleBody(content, article.body, doc);
    }

    private void renderArticleBody(LinearLayout content, String bodyText, ArticleDoc doc) {
        for (ArticleBody.Segment segment : ArticleBody.segments(bodyText)) {
            if (segment.type == ArticleBody.Segment.Type.PHOTO) {
                String key = ArticleBody.resolvePhotoKey(segment.value, doc.photos);
                FrameLayout photo = new FrameLayout(this);
                photo.setBackground(round(0xfff1e7db, 10));
                TextView loading = text("图片 · " + (key == null ? segment.value : key), 13, Theme.SECONDARY, Typeface.NORMAL);
                loading.setGravity(Gravity.CENTER);
                photo.addView(loading, match());
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(90));
                p.setMargins(0, dp(12), 0, dp(12));
                content.addView(photo, p);
                if (key != null) loadPhotoInto(photo, key);
            } else {
                TextView body = text(segment.value, 16, Theme.INK, Typeface.NORMAL);
                body.setLineSpacing(dp(4), 1.0f);
                body.setPadding(0, dp(12), 0, dp(18));
                content.addView(body);
            }
        }
    }

    private void loadPhotoInto(FrameLayout frame, String relKey) {
        io.execute(() -> {
            try {
                String scope = library.ownerScope();
                if (scope == null) return;
                byte[] data = library.photoData(scope + relKey);
                if (data == null) return;
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bitmap == null) return;
                main.post(() -> {
                    frame.removeAllViews();
                    ImageView image = new ImageView(this);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setImageBitmap(bitmap);
                    frame.addView(image, match());
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void addAction(LinearLayout actions, String label, Runnable action) {
        TextView button = text(label, 14, Theme.INK, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(round(Theme.CARD, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(0, 0, dp(8), 0);
        actions.addView(button, lp);
        button.setOnClickListener(v -> action.run());
    }

    private void publishWechat(Recording rec) {
        io.execute(() -> {
            try {
                boolean ok = library.publishWechat(rec);
                toast(ok ? "公众号草稿已发布/更新" : "公众号发布失败，请检查配置");
            } catch (Exception e) {
                toast("公众号发布失败：" + e.getMessage());
            }
        });
    }

    private void shareCommunity(Recording rec, String replyTo) {
        shareCommunityWithTermsGate(rec, replyTo);
    }

    private void shareCommunityWithTermsGate(Recording rec, String replyTo) {
        if (!communityTerms.agreed()) {
            showCommunityTermsGate(() -> doShareCommunity(rec, replyTo));
        } else {
            doShareCommunity(rec, replyTo);
        }
    }

    private void doShareCommunity(Recording rec, String replyTo) {
        io.execute(() -> {
            try {
                String shareId = community.share(rec, replyTo);
                toast(shareId == null || shareId.isEmpty() ? "社区分享失败，可能需要 Apple 会话" : "已分享到社区");
            } catch (Exception e) {
                toast("社区分享失败：" + e.getMessage());
            }
        });
    }

    private void playRecordingAudio(Recording rec) {
        io.execute(() -> {
            try {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    main.post(this::stopPlayback);
                    return;
                }
                byte[] audio = library.download(rec.audioName);
                if (audio == null || audio.length == 0) throw new IllegalStateException("无法下载录音");
                File file = new File(getCacheDir(), rec.stem().replaceAll("[^A-Za-z0-9._-]+", "_") + ".m4a");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(audio);
                }
                main.post(() -> startPlayback(file));
            } catch (Exception e) {
                toast("播放失败：" + e.getMessage());
            }
        });
    }

    private void startPlayback(File file) {
        try {
            stopPlayback();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.setOnCompletionListener(mp -> stopPlayback());
            mediaPlayer.prepare();
            mediaPlayer.start();
            toast("正在播放录音");
        } catch (Exception e) {
            toast("播放失败：" + e.getMessage());
        }
    }

    private void stopPlayback() {
        if (mediaPlayer == null) return;
        try {
            mediaPlayer.stop();
        } catch (Exception ignored) {
        }
        mediaPlayer.release();
        mediaPlayer = null;
    }

    private void showVoiceEdit(Recording rec) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setMinLines(3);
        input.setHint("输入你想怎么改这篇文章。会通过 /agent/edit WebSocket 串行提交。");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("语音修改")
                .setView(input)
                .setPositiveButton("提交修改", (d, w) -> startArticleEdit(rec, input.getText().toString()))
                .setNeutralButton("语音听写", null)
                .setNegativeButton("取消", null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> toggleDictation(dialog, input));
    }

    private void toggleDictation(AlertDialog dialog, android.widget.EditText input) {
        if (dictationSession != null && dictationSession.isRunning()) {
            dictationSession.stop();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText("语音听写");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
            toast("请授权麦克风后再开始听写");
            return;
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText("停止听写");
        dictationSession = new AsrDictationSession(auth, new AsrDictationSession.Listener() {
            @Override public void onText(String text, boolean isFinal) {
                main.post(() -> {
                    String existing = input.getText().toString();
                    String spacer = existing.isEmpty() || existing.endsWith("\n") || existing.endsWith(" ") ? "" : " ";
                    input.setText(existing + spacer + text);
                    input.setSelection(input.getText().length());
                });
            }

            @Override public void onState(String state) {
                toast(state);
            }

            @Override public void onError(String message) {
                main.post(() -> {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText("语音听写");
                    toast("听写失败：" + message);
                });
            }
        });
        dictationSession.start();
    }

    private void startArticleEdit(Recording rec, String instruction) {
        if (dictationSession != null) dictationSession.stop();
        if (editSession != null) editSession.close();
        toast("正在连接文章编辑器…");
        editSession = new ArticleEditSession(auth, rec.stem(), new ArticleEditSession.Listener() {
            @Override public void onUpdated(ArticleDoc doc) {
                main.post(() -> {
                    toast("文章已更新");
                    showArticle(rec, doc);
                });
            }

            @Override public void onState(String state) {
                toast(state);
            }

            @Override public void onError(String message) {
                toast("修改失败：" + message);
            }
        });
        editSession.connect();
        editSession.enqueue(instruction);
    }

    private void showStyleVersions(Recording rec) {
        io.execute(() -> {
            try {
                JSONObject history = library.versionHistory(rec);
                main.post(() -> showStyleVersionDialog(rec, history));
            } catch (Exception e) {
                toast("版本历史加载失败：" + e.getMessage());
            }
        });
    }

    private void showStyleVersionDialog(Recording rec, JSONObject history) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), 0, dp(8), 0);
        JSONArray versions = history.optJSONArray("versions");
        if (versions == null) versions = new JSONArray();
        TextView info = text("当前 head: " + history.optInt("head", 0) + "\n版本数: " + versions.length(), 14, Theme.SECONDARY, Typeface.NORMAL);
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("输入文风版本号重挖，例如 8");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        android.widget.EditText headInput = new android.widget.EditText(this);
        headInput.setHint("或输入已有文章版本 head");
        headInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(info);
        for (int i = versions.length() - 1; i >= 0; i--) {
            JSONObject item = versions.optJSONObject(i);
            if (item == null) continue;
            TextView row = text(versionPreview(item, i), 14, Theme.INK, Typeface.NORMAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(round(i == history.optInt("head", 0) ? Theme.GREEN_BG : Theme.CARD, 10));
            final int head = i;
            row.setOnClickListener(v -> switchArticleHead(rec, head));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(8), 0, 0);
            form.addView(row, lp);
        }
        form.addView(input);
        form.addView(headInput);
        new AlertDialog.Builder(this)
                .setTitle("文风 / 版本")
                .setView(form)
                .setPositiveButton("重挖文风", (d, w) -> io.execute(() -> {
                    try {
                        int version = Integer.parseInt(input.getText().toString());
                        boolean ok = library.restyle(rec, version);
                        toast(ok ? "已请求生成该文风版本" : "文风版本请求失败");
                    } catch (Exception e) {
                        toast("文风版本请求失败：" + e.getMessage());
                    }
                }))
                .setNeutralButton("切换 head", (d, w) -> io.execute(() -> {
                    try {
                        int head = Integer.parseInt(headInput.getText().toString());
                        boolean ok = library.patchHead(rec, head);
                        toast(ok ? "已切换文章版本" : "切换失败");
                        if (ok) refreshAndDrain();
                    } catch (Exception e) {
                        toast("切换失败：" + e.getMessage());
                    }
                }))
                .setNegativeButton("取消", null)
                .show();
    }

    private String versionPreview(JSONObject item, int index) {
        StringBuilder out = new StringBuilder();
        out.append("版本 ").append(index);
        String savedAt = item.optString("savedAt", item.optString("createdAt", ""));
        if (!savedAt.isEmpty()) out.append(" · ").append(savedAt);
        JSONArray articles = item.optJSONArray("articles");
        if (articles != null && articles.length() > 0) {
            JSONObject first = articles.optJSONObject(0);
            if (first != null) {
                String title = first.optString("title", "");
                String body = ArticleBody.stripMarkers(first.optString("body", ""));
                if (!title.isEmpty()) out.append("\n").append(title);
                if (!body.isEmpty()) out.append("\n").append(body.length() > 80 ? body.substring(0, 80) + "…" : body);
            }
        } else {
            String source = item.optString("source", "");
            if (!source.isEmpty()) out.append("\n").append(source);
        }
        return out.toString();
    }

    private void switchArticleHead(Recording rec, int head) {
        io.execute(() -> {
            try {
                boolean ok = library.patchHead(rec, head);
                toast(ok ? "已切换到版本 " + head : "切换失败");
                if (ok) refreshAndDrain();
            } catch (Exception e) {
                toast("切换失败：" + e.getMessage());
            }
        });
    }

    private void shiftArticleHead(Recording rec, int delta) {
        io.execute(() -> {
            try {
                JSONObject history = library.versionHistory(rec);
                JSONArray versions = history.optJSONArray("versions");
                int count = versions == null ? 0 : versions.length();
                int current = history.optInt("head", Math.max(0, count - 1));
                int next = current + delta;
                if (next < 0 || next >= count) {
                    toast(delta < 0 ? "没有更早的版本" : "没有更新的版本");
                    return;
                }
                boolean ok = library.patchHead(rec, next);
                toast(ok ? "已切换到版本 " + next : "切换失败");
                if (ok) refreshAndDrain();
            } catch (Exception e) {
                toast("切换失败：" + e.getMessage());
            }
        });
    }

    private void exportArticle(Recording rec, ArticleDoc doc) {
        StringBuilder text = new StringBuilder();
        for (MinedArticle article : doc.articles) {
            text.append(article.title).append("\n\n").append(ArticleBody.stripMarkers(article.body)).append("\n\n---\n\n");
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, rec.rowTitle());
        intent.putExtra(Intent.EXTRA_TEXT, text.toString());
        startActivity(Intent.createChooser(intent, "导出文章"));
    }

    private void openCommunityPost(CommunityStore.Post post) {
        io.execute(() -> {
            try {
                CommunityStore.Post full = community.get(post.shareId);
                CommunityStore.Post selected = full == null ? post : full;
                ArticleDoc doc = library.fetchDocByArticleKey(selected.articleKey);
                main.post(() -> showCommunityPost(selected, doc));
            } catch (Exception e) {
                toast("社区文章加载失败：" + e.getMessage());
            }
        });
    }

    private void showCommunityPost(CommunityStore.Post post, ArticleDoc doc) {
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        // Nav bar with like button and more menu
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12) + getStatusBarHeight(), dp(8), dp(8));
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        TextView back = text("‹", 34, Theme.SECONDARY, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> showHome());
        bar.addView(text("VD社区", 20, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));

        final String shareId = post.shareId;
        final String authorName = post.author == null || post.author.isEmpty() ? "匿名作者" : post.author;
        final boolean[] liked = {false};

        // Like button
        final TextView likeBtn = text("♡", 22, Theme.SECONDARY, Typeface.NORMAL);
        likeBtn.setGravity(Gravity.CENTER);
        likeBtn.setPadding(dp(6), 0, dp(6), 0);
        bar.addView(likeBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        likeBtn.setOnClickListener(v -> {
            liked[0] = !liked[0];
            likeBtn.setText(liked[0] ? "♥" : "♡");
            likeBtn.setTextColor(liked[0] ? Theme.RED : Theme.SECONDARY);
            io.execute(() -> {
                try {
                    community.engage(shareId, "like", liked[0]);
                } catch (Exception ignored) {}
            });
        });

        // ... menu
        TextView moreBtn = text("⋯", 22, Theme.INK, Typeface.BOLD);
        moreBtn.setGravity(Gravity.CENTER);
        moreBtn.setBackground(round(Theme.INK, 8));
        moreBtn.setTextColor(0xffffffff);
        moreBtn.setPadding(0, 0, 0, dp(2));
        bar.addView(moreBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));
        moreBtn.setOnClickListener(v -> showCommunityPostMenu(post, authorName));

        // Actions
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(14), 0, dp(14), dp(10));
        page.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        addAction(actions, "分享", () -> shareCommunityUrl(post));
        addAction(actions, "回复录音", () -> startCommunityReplyRecording(post));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(14), dp(20), dp(24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content.addView(text(post.title == null || post.title.isEmpty() ? "社区文章" : post.title, 24, Theme.INK, Typeface.BOLD));
        TextView meta = text(authorName, 13, Theme.SECONDARY, Typeface.NORMAL);
        meta.setPadding(0, dp(8), 0, dp(18));
        content.addView(meta);

        if (doc != null && !doc.articles.isEmpty()) {
            for (MinedArticle article : doc.articles) {
                if (!article.title.equals(post.title)) {
                    TextView articleTitle = text(article.title, 20, Theme.INK, Typeface.BOLD);
                    articleTitle.setPadding(0, dp(10), 0, 0);
                    content.addView(articleTitle);
                }
                renderArticleBody(content, article.body, doc);
            }
        } else {
            TextView body = text("社区正文暂不可读，请稍后刷新或打开分享链接查看。", 16, Theme.INK, Typeface.NORMAL);
            body.setLineSpacing(dp(4), 1.0f);
            content.addView(body);
        }

        // Replies section (loaded async)
        final LinearLayout repliesSection = new LinearLayout(this);
        repliesSection.setOrientation(LinearLayout.VERTICAL);
        content.addView(repliesSection);

        // Trigger async load of replies and engage
        io.execute(() -> {
            // Engagement tracking
            try { community.engage(shareId, "view"); } catch (Exception ignored) {}

            // Load replies
            try {
                List<CommunityStore.Post> replies = community.replies(shareId);
                main.post(() -> renderReplies(repliesSection, replies, post));
            } catch (Exception ignored) {}
        });

        // Recording bar at the bottom (visible while recording a reply)
        final FrameLayout recordingBarContainer = new FrameLayout(this);
        recordingBarContainer.setVisibility(View.GONE);
        page.addView(recordingBarContainer, new LinearLayout.LayoutParams(-1, -2));

        // Store references for the recording bar
        final CommunityStore.Post finalPost = post;
        root.setTag(new Object[]{post, recordingBarContainer, scroll, content});
    }

    private void startCommunityReplyRecording(CommunityStore.Post post) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
            return;
        }
        replyToShareId = post.shareId;
        communityRecording = true;
        capturedPhotos.clear();
        recordingStart = ZonedDateTime.now();
        try {
            recorder.start();
            showCommunityRecordingBar(post);
            main.postDelayed(communityTimerTick, 500);
        } catch (Exception e) {
            toast("无法开始录音：" + e.getMessage());
            replyToShareId = null;
            communityRecording = false;
        }
    }

    private void showCommunityRecordingBar(CommunityStore.Post post) {
        root.removeAllViews();
        // Rebuild the page with recording bar at bottom
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        // Nav bar (simplified)
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12) + getStatusBarHeight(), dp(8), dp(8));
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        TextView back = text("‹", 34, Theme.SECONDARY, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> {
            stopCommunityReplyRecording();
            showCommunityPost(post, null);
        });
        bar.addView(text("回复: " + (post.title == null ? "社区文章" : post.title), 16, Theme.INK, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1));

        // Article content (scrollable)
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(14), dp(20), dp(24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content.addView(text(post.title == null || post.title.isEmpty() ? "社区文章" : post.title, 24, Theme.INK, Typeface.BOLD));
        String authorName = post.author == null || post.author.isEmpty() ? "匿名作者" : post.author;
        content.addView(text(authorName, 13, Theme.SECONDARY, Typeface.NORMAL));

        // Recording bar at bottom
        LinearLayout recBar = new LinearLayout(this);
        recBar.setOrientation(LinearLayout.HORIZONTAL);
        recBar.setGravity(Gravity.CENTER);
        recBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        recBar.setBackground(round(Theme.CARD, 16));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, -2);
        barLp.setMargins(dp(14), 0, dp(14), dp(16));
        page.addView(recBar, barLp);

        // Red dot + timer
        TextView dot = text("●", 10, Theme.RED, Typeface.BOLD);
        recBar.addView(dot);
        final TextView timerText = text("00:00", 18, Theme.INK, Typeface.NORMAL);
        timerText.setPadding(dp(10), 0, dp(10), 0);
        recBar.addView(timerText);

        // Waveform
        TextView wave = text("▂▅▇▄█▆▃", 22, Theme.RED, Typeface.NORMAL);
        recBar.addView(wave, new LinearLayout.LayoutParams(0, -2, 1));

        // Stop button
        TextView stopBtn = text("■", 16, 0xffffffff, Typeface.NORMAL);
        stopBtn.setGravity(Gravity.CENTER);
        stopBtn.setBackground(round(Theme.RED, 18));
        stopBtn.setMinWidth(dp(36));
        stopBtn.setMinHeight(dp(36));
        stopBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        recBar.addView(stopBtn);
        stopBtn.setOnClickListener(v -> stopCommunityReplyRecording());

        // Store timer reference for updates
        recBar.setTag(timerText);
    }

    private void updateCommunityRecordingTimer() {
        // Find the timer TextView from the current view
        FrameLayout rootPage = (FrameLayout) root.getChildAt(0);
        if (rootPage == null) return;
        // Find the recording bar (last child of page)
        for (int i = rootPage.getChildCount() - 1; i >= 0; i--) {
            View child = rootPage.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) child;
                // Check if this looks like the recording bar (has a tagged timer)
                Object tag = ll.getTag();
                if (tag instanceof TextView) {
                    TextView timerText = (TextView) tag;
                    long elapsed = recorder.elapsedSeconds();
                    timerText.setText(String.format("%02d:%02d", elapsed / 60, elapsed % 60));
                    return;
                }
            }
        }
    }

    private void stopCommunityReplyRecording() {
        communityRecording = false;
        main.removeCallbacks(communityTimerTick);
        AudioRecorder.Take take = recorder.stop(null);
        List<CapturedPhoto> photos = new ArrayList<>(capturedPhotos);
        capturedPhotos.clear();
        String replyId = replyToShareId;
        replyToShareId = null;
        recordingStart = null;

        if (take != null) {
            io.execute(() -> {
                // Upload photos first
                uploadCapturedPhotos(photos);
                // Upload audio
                uploader.upload(take.file);
                // Share as reply
                try {
                    recordings = library.load(uploader.pendingNames());
                    Recording rec = recordings.isEmpty() ? null : recordings.get(0);
                    if (rec != null && replyId != null) {
                        String shareId = community.share(rec, replyId);
                        main.post(() -> {
                            if (shareId != null) toast("已作为回复发布到社区");
                            else toast("上传成功，社区分享失败");
                            refreshAndDrain();
                        });
                    } else {
                        main.post(() -> toast("回复已保存，社区分享失败"));
                    }
                } catch (Exception e) {
                    main.post(() -> toast("回复保存失败：" + e.getMessage()));
                }
            });
        }
    }

    private void renderReplies(LinearLayout section, List<CommunityStore.Post> replies, CommunityStore.Post parentPost) {
        if (replies == null || replies.isEmpty()) return;
        section.removeAllViews();

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(0xffe0d8cc);
        section.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        TextView label = text("回应 (" + replies.size() + ")", 13, Theme.FAINT, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        label.setPadding(dp(4), dp(14), 0, dp(10));
        section.addView(label);

        for (CommunityStore.Post reply : replies) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            row.setBackground(round(Theme.CARD, 10));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

            String replyAuthor = reply.author == null || reply.author.isEmpty() ? "匿名" : reply.author;
            texts.addView(text(replyAuthor, 14, Theme.SECONDARY, Typeface.BOLD));
            String replyTitle = reply.title == null ? "" : " · " + reply.title;
            TextView timeText = text(formatCommunityDate(reply.firstSharedAt) + replyTitle, 12, Theme.FAINT, Typeface.NORMAL);
            timeText.setPadding(0, dp(2), 0, 0);
            texts.addView(timeText);

            row.addView(text("›", 22, 0xffcfc6b6, Typeface.NORMAL));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(6));
            section.addView(row, lp);
            row.setOnClickListener(v -> openCommunityPost(reply));
        }
    }

    private String formatCommunityDate(double ms) {
        try {
            java.util.Date date = new java.util.Date((long) ms);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.getDefault());
            java.util.Date now = new java.util.Date();
            java.text.SimpleDateFormat yearSdf = new java.text.SimpleDateFormat("M月d日", java.util.Locale.getDefault());
            if (date.getYear() == now.getYear()) {
                return yearSdf.format(date);
            }
            return sdf.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    private void showCommunityPostMenu(CommunityStore.Post post, String authorName) {
        String[] items = {"分享", "举报", "屏蔽此用户"};
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0) shareCommunityUrl(post);
                    if (which == 1) showReportConfirm(post);
                    if (which == 2) showBlockConfirm(post, authorName);
                })
                .show();
    }

    private void showReportConfirm(CommunityStore.Post post) {
        new AlertDialog.Builder(this)
                .setTitle("举报这篇分享？")
                .setMessage("举报后这篇会立即从社区下架，并在 24 小时内由人工审核处理。")
                .setPositiveButton("举报并下架", (d, w) -> {
                    io.execute(() -> {
                        try {
                            community.report(post.shareId);
                            main.post(() -> {
                                toast("已举报，内容已下架待审核");
                                // Remove from local list
                                posts.removeIf(p -> p.shareId.equals(post.shareId));
                                showHome();
                            });
                        } catch (Exception e) {
                            main.post(() -> toast("举报失败：" + e.getMessage()));
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBlockConfirm(CommunityStore.Post post, String authorName) {
        new AlertDialog.Builder(this)
                .setTitle("屏蔽此用户？")
                .setMessage("屏蔽后，你将不再看到 " + authorName + " 的任何社区内容。可在「设置」>「关于」>「已屏蔽用户」中取消屏蔽。")
                .setPositiveButton("屏蔽", (d, w) -> {
                    blockStore.block(post.author);
                    // Remove from local list
                    posts.removeIf(p -> (p.author == null ? "" : p.author).equals(post.author));
                    toast("已屏蔽，TA 的内容将不再显示");
                    showHome();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void shareCommunityUrl(CommunityStore.Post post) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, com.baixingai.voicedrop.net.Api.sharePage(post.shareId));
        startActivity(Intent.createChooser(intent, "分享社区文章"));
    }

    private void shareRecording(Recording rec) {
        shareRecording(rec, articleIndex);
    }

    private void shareRecording(Recording rec, int section) {
        io.execute(() -> {
            try {
                String url = library.shareUrl(rec, section);
                if (url == null) throw new IllegalStateException("无法生成链接");
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, url);
                main.post(() -> startActivity(Intent.createChooser(intent, "分享 VoiceDrop")));
            } catch (Exception e) {
                toast("分享失败：" + e.getMessage());
            }
        });
    }

    private void deleteRecording(Recording rec) {
        io.execute(() -> {
            boolean ok = library.delete(rec);
            if (ok) main.post(this::refreshAndDrain);
            else toast("删除失败");
        });
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 11);
            return;
        }
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            toast("没有可用相机");
            return;
        }
        startActivityForResult(intent, 20);
    }

    private void pickRecordingPhotos() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "选择录音场景照片"), 22);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 21 && resultCode == RESULT_OK && data != null && insertPhotoTarget != null) {
            Uri uri = data.getData();
            Recording target = insertPhotoTarget;
            insertPhotoTarget = null;
            if (uri != null) uploadArticlePhoto(target, uri);
            return;
        }
        if (requestCode == 22 && resultCode == RESULT_OK && data != null && recordingStart != null) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    addRecordingPhoto(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                addRecordingPhoto(data.getData());
            }
            toast("已加入照片 " + capturedPhotos.size());
            showRecording(false);
            return;
        }
        if (requestCode != 20 || resultCode != RESULT_OK || data == null || recordingStart == null) return;
        Object extra = data.getExtras() == null ? null : data.getExtras().get("data");
        if (!(extra instanceof Bitmap)) return;
        Bitmap bitmap = (Bitmap) extra;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out);
        byte[] bytes = out.toByteArray();
        long offset = Math.max(0, java.time.Duration.between(recordingStart, ZonedDateTime.now()).getSeconds());
        String key = RecordingName.photoKey(RecordingName.timestamp(recordingStart), (int) offset);
        capturedPhotos.add(new CapturedPhoto(key, bytes, bitmap));
        toast("已加入照片 " + capturedPhotos.size());
        showRecording(false);
    }

    private void addRecordingPhoto(Uri uri) {
        try {
            byte[] bytes;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return;
                bytes = HttpClient.readAll(in);
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out);
            byte[] jpeg = out.toByteArray();
            long offset = Math.max(0, java.time.Duration.between(recordingStart, ZonedDateTime.now()).getSeconds());
            String key = RecordingName.photoKey(RecordingName.timestamp(recordingStart), (int) offset);
            capturedPhotos.add(new CapturedPhoto(key, jpeg, bitmap));
        } catch (Exception e) {
            toast("照片读取失败：" + e.getMessage());
        }
    }

    private void uploadCapturedPhotos(List<CapturedPhoto> photos) {
        for (CapturedPhoto photo : photos) {
            try {
                http.putBytes(com.baixingai.voicedrop.net.Api.filesBase() + "/upload/" + com.baixingai.voicedrop.net.Api.path(photo.key),
                        auth.bearer(), "image/jpeg", photo.bytes);
            } catch (Exception e) {
                toast("照片上传失败：" + e.getMessage());
            }
        }
    }

    private void pickArticlePhoto(Recording rec) {
        insertPhotoTarget = rec;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "选择照片"), 21);
    }

    private void uploadArticlePhoto(Recording rec, Uri uri) {
        io.execute(() -> {
            try {
                RecordingName.Parsed parsed = RecordingName.parse(rec.stem());
                if (parsed == null) throw new IllegalStateException("无法识别录音时间戳");
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IllegalStateException("无法读取图片");
                    bytes = HttpClient.readAll(in);
                }
                String key = RecordingName.photoKey(parsed.sessionTs, 0);
                http.putBytes(com.baixingai.voicedrop.net.Api.filesBase() + "/upload/" + com.baixingai.voicedrop.net.Api.path(key),
                        auth.bearer(), "image/jpeg", bytes);
                toast("照片已上传，可在语音修改中引用：" + key);
            } catch (Exception e) {
                toast("照片上传失败：" + e.getMessage());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            toast("权限被拒绝");
            return;
        }
        if (requestCode == 10) startRecordingFlow();
        if (requestCode == 11) openCamera();
    }

    private void showSettings() {
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(14) + getStatusBarHeight(), dp(16), dp(10));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView back = text("‹", 34, Theme.SECONDARY, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> showHome());
        top.addView(text("设置", 24, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addSection(content, "账户");
        addSettingRow(content, "匿名身份", auth.anonId(), () -> showTokenImport());
        addSettingRow(content, "设备登录", "token 导入、X25519 加密迁移", this::showDeviceLogin);
        addSettingRow(content, "重置匿名身份", "会切到新的云端空间", () -> {
            auth.resetAnonymous();
            toast("已重置匿名身份");
            refreshAndDrain();
        });

        addSection(content, "创作");
        addSettingRow(content, "文风", "读取 /style，保存 CLAUDE.json 版本", this::showWritingStyle);
        addSettingRow(content, "公众号", "配置 AppID / Secret，发布草稿", this::showWechatSettings);
        addSettingRow(content, "算力", "余额、消耗明细、约可成文篇数", this::showUsage);

        addSection(content, "同步与社区");
        addSettingRow(content, "自动分享到 VD社区", "CONFIG.json", () -> toggleAutoShare());
        addSettingRow(content, "社区公约", "举报、投诉、UGC 规则", this::showCommunityTerms);
        addSettingRow(content, "系统权限", "麦克风、相机、通知", () ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        addSection(content, "本地");
        addSettingRow(content, "导出全部数据", "打包文章、音频、字幕和索引", this::exportAllData);
        addSettingRow(content, "上传后删除本地", prefs.deleteLocalAfterUpload() ? "开" : "关", () -> {
            prefs.setDeleteLocalAfterUpload(!prefs.deleteLocalAfterUpload());
            showSettings();
        });
        addSection(content, "其他");
        addSettingRow(content, "关于", "隐私、公约、屏蔽、联系", this::showAbout);
        addSettingRow(content, "关于 VoiceDrop", "Android parity build", () -> showTextDialog("关于", "VoiceDrop Android\n以 iOS 功能和接口为标准迁移。"));
    }

    private void exportAllData() {
        toast("正在打包导出…");
        io.execute(() -> {
            try {
                File zip = exportManager.exportAll(recordings);
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", zip);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                main.post(() -> startActivity(Intent.createChooser(intent, "导出 VoiceDrop 数据")));
            } catch (Exception e) {
                toast("导出失败：" + e.getMessage());
            }
        });
    }

    private void addSection(LinearLayout content, String label) {
        TextView section = text(label, 13, Theme.FAINT, Typeface.BOLD);
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(4), dp(20), 0, dp(8));
        content.addView(section);
    }

    private void addSettingRow(LinearLayout content, String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(round(Theme.CARD, 12));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        texts.addView(text(title, 16, Theme.INK, Typeface.BOLD));
        TextView sub = text(subtitle == null ? "" : subtitle, 12, Theme.SECONDARY, Typeface.NORMAL);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);
        row.addView(text("›", 28, 0xffcfc6b6, Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(row, lp);
        row.setOnClickListener(v -> action.run());
    }

    private void showWritingStyle() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), 0, dp(8), 0);

        // Version bar
        final LinearLayout versionBar = new LinearLayout(this);
        versionBar.setOrientation(LinearLayout.HORIZONTAL);
        versionBar.setGravity(Gravity.CENTER_VERTICAL);
        versionBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        versionBar.setBackground(round(Theme.CARD, 10));
        final TextView versionLabel = text("加载版本历史…", 13, Theme.SECONDARY, Typeface.NORMAL);
        versionBar.addView(versionLabel, new LinearLayout.LayoutParams(0, -2, 1));
        final TextView versionChevron = text("▾", 16, Theme.FAINT, Typeface.NORMAL);
        versionBar.addView(versionChevron);
        form.addView(versionBar);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setMinLines(8);
        input.setGravity(Gravity.TOP);
        input.setHint("写下你的公众号文风。保存后会写入 /style，服务端生成版本。");
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, 0, 1);
        inputLp.setMargins(0, dp(10), 0, dp(10));
        form.addView(input, inputLp);

        // Version dropdown panel (hidden initially)
        final LinearLayout versionPanel = new LinearLayout(this);
        versionPanel.setOrientation(LinearLayout.VERTICAL);
        versionPanel.setBackground(round(Theme.CARD, 10));
        versionPanel.setPadding(0, dp(6), 0, dp(6));
        versionPanel.setVisibility(View.GONE);
        form.addView(versionPanel, new LinearLayout.LayoutParams(-1, -2));

        final int[] headVersion = {-1};
        final String[] currentStyle = {""};

        io.execute(() -> {
            try {
                SettingsStore.Style style = settingsStore.loadStyle();
                main.post(() -> {
                    input.setText(style.style);
                    currentStyle[0] = style.style;
                });
                // Load version history
                try {
                    org.json.JSONObject history = settingsStore.loadStyleHistory();
                    org.json.JSONArray versions = history.optJSONArray("versions");
                    headVersion[0] = history.optInt("head", versions != null ? versions.length() - 1 : -1);
                    main.post(() -> {
                        if (versions != null && versions.length() > 0) {
                            versionLabel.setText("v" + headVersion[0] + " · 共 " + versions.length() + " 版");
                        } else {
                            versionLabel.setText("暂无历史版本");
                        }
                    });
                } catch (Exception ignored) {
                    main.post(() -> versionLabel.setText("版本历史"));
                }
            } catch (Exception ignored) {}
        });

        // State for multi-style compare mode
        final boolean[] compareMode = {false};
        final java.util.Set<Integer> selectedStyles = new java.util.HashSet<>();

        versionBar.setOnClickListener(v -> {
            if (versionPanel.getVisibility() == View.VISIBLE) {
                versionPanel.setVisibility(View.GONE);
            } else {
                // Build version list
                versionPanel.removeAllViews();
                io.execute(() -> {
                    try {
                        org.json.JSONObject history = settingsStore.loadStyleHistory();
                        org.json.JSONArray versions = history.optJSONArray("versions");
                        int head = history.optInt("head", versions != null ? versions.length() - 1 : -1);
                        headVersion[0] = head;

                        // Try to load server styles (multi-style compare selection)
                        SettingsStore.Style style = settingsStore.loadStyle();
                        if (style.selectedStyles != null && !style.selectedStyles.isEmpty()) {
                            main.post(() -> {
                                selectedStyles.addAll(style.selectedStyles);
                                if (!selectedStyles.isEmpty()) compareMode[0] = true;
                            });
                        }

                        main.post(() -> {
                            // Multi-style compare toggle at top
                            LinearLayout compareToggle = new LinearLayout(MainActivity.this);
                            compareToggle.setOrientation(LinearLayout.HORIZONTAL);
                            compareToggle.setGravity(Gravity.CENTER_VERTICAL);
                            compareToggle.setPadding(dp(14), dp(10), dp(14), dp(10));
                            compareToggle.setBackground(round(0xfff5f0e8, 8));

                            TextView compareLabel = text("多风格对比", 14, Theme.INK, Typeface.BOLD);
                            compareToggle.addView(compareLabel, new LinearLayout.LayoutParams(0, -2, 1));

                            TextView compareStatus = text(
                                    compareMode[0] ? "已开 (已选 " + selectedStyles.size() + "/3)" : "已关",
                                    13, compareMode[0] ? Theme.RED : Theme.FAINT, Typeface.NORMAL);
                            compareStatus.setPadding(0, 0, dp(8), 0);
                            compareToggle.addView(compareStatus);

                            TextView toggleIcon = text(compareMode[0] ? "◉" : "○", 18,
                                    compareMode[0] ? Theme.RED : Theme.FAINT, Typeface.NORMAL);
                            compareToggle.addView(toggleIcon);

                            compareToggle.setOnClickListener(vv -> {
                                compareMode[0] = !compareMode[0];
                                if (!compareMode[0]) {
                                    selectedStyles.clear();
                                    try { settingsStore.saveStyleSelection(new ArrayList<>()); } catch (Exception ignored) {}
                                }
                                // Rebuild panel
                                versionPanel.performClick();
                            });
                            versionPanel.addView(compareToggle);

                            // Divider
                            View divider = new View(MainActivity.this);
                            divider.setBackgroundColor(0xffe0d8cc);
                            versionPanel.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

                            if (versions == null || versions.length() == 0) {
                                versionPanel.addView(text("暂无历史版本", 14, Theme.SECONDARY, Typeface.NORMAL));
                            } else {
                                for (int i = versions.length() - 1; i >= 0; i--) {
                                    org.json.JSONObject item = versions.optJSONObject(i);
                                    if (item == null) continue;
                                    String savedAt = item.optString("savedAt", item.optString("createdAt", ""));
                                    String styleText = item.optString("style", "");
                                    String preview = "v" + i + " · " + styleText.length() + " 字";
                                    if (!savedAt.isEmpty()) {
                                        try {
                                            long epochMs = (long) Double.parseDouble(savedAt);
                                            java.text.SimpleDateFormat sdf =
                                                    new java.text.SimpleDateFormat("M月d日", java.util.Locale.getDefault());
                                            preview += " · " + sdf.format(new java.util.Date(epochMs));
                                        } catch (Exception ignored) {}
                                    }

                                    final int idx = i;
                                    final String styleBody = styleText;

                                    if (compareMode[0]) {
                                        // Compare mode: checkbox style
                                        LinearLayout row = new LinearLayout(MainActivity.this);
                                        row.setOrientation(LinearLayout.HORIZONTAL);
                                        row.setGravity(Gravity.CENTER_VERTICAL);
                                        row.setPadding(dp(14), dp(10), dp(14), dp(10));
                                        boolean sel = selectedStyles.contains(idx);
                                        row.setBackground(round(sel ? Theme.GREEN_BG : Theme.CARD, 8));

                                        TextView check = text(sel ? "☑" : "☐", 18,
                                                sel ? Theme.GREEN : Theme.FAINT, Typeface.NORMAL);
                                        row.addView(check);

                                        TextView rowText = text(preview, 14, sel ? Theme.GREEN : Theme.INK, Typeface.NORMAL);
                                        rowText.setPadding(dp(10), 0, 0, 0);
                                        row.addView(rowText, new LinearLayout.LayoutParams(0, -2, 1));

                                        row.setOnClickListener(vv -> {
                                            if (selectedStyles.contains(idx)) {
                                                selectedStyles.remove(idx);
                                            } else if (selectedStyles.size() < 3) {
                                                selectedStyles.add(idx);
                                            }
                                            if (selectedStyles.isEmpty()) {
                                                try { settingsStore.saveStyleSelection(new ArrayList<>()); } catch (Exception ignored) {}
                                            } else {
                                                List<Integer> list = new ArrayList<>(selectedStyles);
                                                try { settingsStore.saveStyleSelection(list); } catch (Exception ignored) {}
                                            }
                                            versionPanel.performClick(); // rebuild
                                        });
                                        versionPanel.addView(row);
                                    } else {
                                        // Single-select mode
                                        TextView row = text(preview, 14, i == head ? Theme.GREEN : Theme.INK, Typeface.NORMAL);
                                        row.setPadding(dp(14), dp(10), dp(14), dp(10));
                                        if (i == head) row.setBackground(round(Theme.GREEN_BG, 8));
                                        row.setOnClickListener(vv -> {
                                            input.setText(styleBody);
                                            versionLabel.setText("v" + idx + " · 已切换");
                                            versionPanel.setVisibility(View.GONE);
                                        });
                                        versionPanel.addView(row);
                                    }
                                }

                                // Footer hint for compare mode
                                if (compareMode[0]) {
                                    View divider2 = new View(MainActivity.this);
                                    divider2.setBackgroundColor(0xffe0d8cc);
                                    versionPanel.addView(divider2, new LinearLayout.LayoutParams(-1, dp(1)));
                                    TextView hint = text("勾选 2–3 个版本，成文时各生成一篇并排挑。最多选 3 个。",
                                            12, Theme.FAINT, Typeface.NORMAL);
                                    hint.setPadding(dp(14), dp(8), dp(14), dp(8));
                                    versionPanel.addView(hint);
                                }
                            }
                            versionPanel.setVisibility(View.VISIBLE);
                        });
                    } catch (Exception e) {
                        main.post(() -> {
                            versionPanel.addView(text("加载失败", 14, Theme.RED, Typeface.NORMAL));
                            versionPanel.setVisibility(View.VISIBLE);
                        });
                    }
                });
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("文风")
                .setView(form)
                .setPositiveButton("保存", (d, w) -> io.execute(() -> {
                    try {
                        String currentText = input.getText().toString().trim();
                        String baseline = currentStyle[0].trim();
                        int selectedVersion = headVersion[0];

                        // If text unchanged from loaded version → just move head (no new version)
                        if (!currentText.isEmpty() && currentText.equals(baseline) && selectedVersion >= 0) {
                            settingsStore.saveStyleHead(selectedVersion);
                            toast("已切换到版本 v" + selectedVersion);
                        } else if (!currentText.isEmpty()) {
                            settingsStore.saveStyle(currentText);
                            toast("文风已保存");
                        }
                    } catch (Exception e) {
                        toast("文风保存失败：" + e.getMessage());
                    }
                }))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showWechatSettings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), 0, dp(8), 0);
        android.widget.EditText appid = new android.widget.EditText(this);
        appid.setHint("AppID（wx...）");
        android.widget.EditText secret = new android.widget.EditText(this);
        secret.setHint("AppSecret");
        form.addView(appid);
        form.addView(secret);
        io.execute(() -> {
            try {
                JSONObject cfg = settingsStore.loadWechat();
                main.post(() -> {
                    appid.setText(cfg.optString("appid"));
                    secret.setText(cfg.optString("secret"));
                });
            } catch (Exception ignored) {}
        });
        new AlertDialog.Builder(this)
                .setTitle("公众号配置")
                .setMessage("请把 66.42.45.128 加入公众号 IP 白名单。")
                .setView(form)
                .setPositiveButton("保存并启用", (d, w) -> io.execute(() -> {
                    try {
                        String validation = settingsStore.validateWechatCreds(appid.getText().toString(), secret.getText().toString());
                        if (validation != null) {
                            toast(validation);
                            return;
                        }
                        settingsStore.saveWechat(appid.getText().toString(), secret.getText().toString(), true);
                        toast("公众号配置已保存");
                    } catch (Exception e) {
                        toast("公众号配置失败：" + e.getMessage());
                    }
                }))
                .setNeutralButton("断开", (d, w) -> io.execute(() -> {
                    try {
                        settingsStore.saveWechat("", "", false);
                        toast("已断开公众号");
                    } catch (Exception e) {
                        toast("断开失败：" + e.getMessage());
                    }
                }))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showUsage() {
        io.execute(() -> {
            try {
                UsageStore.Balance b = usageStore.balance();
                List<UsageStore.Entry> entries = usageStore.ledger();
                StringBuilder text = new StringBuilder();
                text.append("剩余算力：").append((int) b.suanli)
                        .append("\n约可成文：").append(UsageStore.articleCapacity(b.suanli)).append(" 篇")
                        .append("\n已用：").append((int) b.spentSuanli)
                        .append("\n\n明细：\n");
                for (UsageStore.Entry e : entries) {
                    text.append(e.kind).append(" · ").append(e.reason).append(" · ").append(e.suanli).append("\n");
                }
                main.post(() -> showTextDialog("算力", text.toString()));
            } catch (Exception e) {
                toast("算力加载失败：" + e.getMessage());
            }
        });
    }

    private void toggleAutoShare() {
        io.execute(() -> {
            try {
                JSONObject cfg = settingsStore.loadConfig();
                boolean next = !cfg.optBoolean("autoShareCommunity", false);
                settingsStore.saveConfig(next);
                toast(next ? "已开启自动分享到社区" : "已关闭自动分享到社区");
            } catch (Exception e) {
                toast("配置保存失败：" + e.getMessage());
            }
        });
    }

    private void showCommunityTerms() {
        showTextDialog("社区公约", "请分享你有权发布的内容。\n\n可举报不当内容；举报后内容会立即下架待审核。\n\n投诉与反馈：support@jianshuo.dev");
    }

    private void showTokenImport() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("anon_...");
        new AlertDialog.Builder(this)
                .setTitle("导入账号 token")
                .setView(input)
                .setPositiveButton("导入", (d, w) -> {
                    if (auth.adoptToken(input.getText().toString().trim())) {
                        toast("已导入账号");
                        refreshAndDrain();
                    } else {
                        toast("token 格式不对");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeviceLogin() {
        String[] items = {"自动登录已有账号", "导入 anon token", "生成本机接收公钥", "解密 sealed token", "给另一台设备加密当前账号"};
        new AlertDialog.Builder(this)
                .setTitle("设备登录")
                .setItems(items, (d, which) -> {
                    if (which == 0) showAutomaticDeviceLink();
                    if (which == 1) showTokenImport();
                    if (which == 2) showDeviceReceiverKey();
                    if (which == 3) showDecryptDeviceToken();
                    if (which == 4) showEncryptDeviceToken();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAutomaticDeviceLink() {
        final android.widget.EditText prefix = field("输入旧设备显示的 6 位账号前缀");
        prefix.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("登录已有账号")
                .setMessage("在旧设备上保持 VoiceDrop 打开；这里输入旧设备账号标识的前 6 位，旧设备会弹出 4 位验证码。")
                .setView(prefix)
                .setPositiveButton("下一步", (d, w) -> startAutomaticDeviceLink(prefix.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void startAutomaticDeviceLink(String prefix) {
        if (prefix.length() < 4) {
            toast("请输入至少 4 位账号前缀");
            return;
        }
        toast("正在发起设备配对…");
        io.execute(() -> {
            deviceLinkSession = new DeviceLinkSession(auth, deviceLinkStore, new DeviceLinkSession.Listener() {
                @Override public void onCodeNeeded(String pairingId) {
                    main.post(() -> askDeviceLinkCode(pairingId));
                }

                @Override public void onState(String state) {
                    toast(state);
                }

                @Override public void onDone() {
                    main.post(() -> {
                        toast("已登录旧账号");
                        refreshAndDrain();
                    });
                }

                @Override public void onError(String message) {
                    toast("设备登录失败：" + message);
                }
            });
            deviceLinkSession.start(prefix);
        });
    }

    private void askDeviceLinkCode(String pairingId) {
        final android.widget.EditText code = field("旧设备上显示的 4 位验证码");
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("输入验证码")
                .setMessage("配对已发起：" + pairingId)
                .setView(code)
                .setPositiveButton("验证", (d, w) -> io.execute(() -> {
                    if (deviceLinkSession != null) deviceLinkSession.verify(code.getText().toString().trim());
                }))
                .setNegativeButton("取消", (d, w) -> {
                    if (deviceLinkSession != null) deviceLinkSession.cancel();
                })
                .show();
    }

    private void showDeviceLinkApproval(String pairingId, String code, String pubkey) {
        pendingLinkPairingId = pairingId;
        pendingLinkPubkey = pubkey;
        new AlertDialog.Builder(this)
                .setTitle("设备登录请求")
                .setMessage("有新设备想登录当前账号。\n\n验证码：" + code + "\n\n如果不是你本人操作，请点“不是我”。")
                .setPositiveButton("是我", (d, w) -> toast("请在新设备输入验证码 " + code))
                .setNegativeButton("不是我", (d, w) -> io.execute(() -> {
                    try {
                        deviceLinkStore.cancel(pairingId);
                    } catch (Exception ignored) {
                    }
                    if (pairingId.equals(pendingLinkPairingId)) {
                        pendingLinkPairingId = null;
                        pendingLinkPubkey = null;
                    }
                }))
                .show();
    }

    private void releaseDeviceLink(String pairingId) {
        if (pendingLinkPairingId == null || !pendingLinkPairingId.equals(pairingId) || pendingLinkPubkey == null) return;
        String pubkey = pendingLinkPubkey;
        pendingLinkPairingId = null;
        pendingLinkPubkey = null;
        io.execute(() -> {
            try {
                DeviceLinkCrypto.Blob blob = DeviceLinkCrypto.encrypt(auth.bearer(), pubkey);
                JSONObject json = new JSONObject()
                        .put("epk", blob.epkB64)
                        .put("sealed", blob.sealedB64);
                deviceLinkStore.complete(pairingId, json);
                toast("已安全发送账号");
            } catch (Exception e) {
                toast("发送账号失败：" + e.getMessage());
            }
        });
    }

    private void showDeviceReceiverKey() {
        DeviceLinkCrypto.Keypair keypair = DeviceLinkCrypto.newKeypair();
        showTextDialog("本机接收公钥",
                "publicKey:\n" + keypair.publicKeyB64
                        + "\n\nprivateKey:\n" + keypair.privateKeyB64
                        + "\n\n请保存 privateKey，用它解密另一台设备返回的 epk/sealed。");
    }

    private void showDecryptDeviceToken() {
        LinearLayout form = form();
        android.widget.EditText priv = field("privateKey");
        android.widget.EditText epk = field("epk");
        android.widget.EditText sealed = field("sealed");
        form.addView(priv);
        form.addView(epk);
        form.addView(sealed);
        new AlertDialog.Builder(this)
                .setTitle("解密 sealed token")
                .setView(form)
                .setPositiveButton("解密并登录", (d, w) -> {
                    try {
                        String token = DeviceLinkCrypto.decrypt(
                                epk.getText().toString().trim(),
                                sealed.getText().toString().trim(),
                                priv.getText().toString().trim());
                        if (auth.adoptToken(token)) {
                            toast("已切换到迁移账号");
                            refreshAndDrain();
                        } else {
                            toast("解密成功，但 token 格式不对");
                        }
                    } catch (Exception e) {
                        toast("解密失败：" + e.getMessage());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEncryptDeviceToken() {
        final android.widget.EditText pub = field("新设备 publicKey");
        new AlertDialog.Builder(this)
                .setTitle("加密当前账号")
                .setView(pub)
                .setPositiveButton("生成", (d, w) -> {
                    try {
                        DeviceLinkCrypto.Blob blob = DeviceLinkCrypto.encrypt(auth.bearer(), pub.getText().toString().trim());
                        showTextDialog("迁移密文", "epk:\n" + blob.epkB64 + "\n\nsealed:\n" + blob.sealedB64);
                    } catch (Exception e) {
                        toast("加密失败：" + e.getMessage());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTextDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("好", null)
                .show();
    }

    // MARK: - About

    private void showAbout() {
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(14) + getStatusBarHeight(), dp(16), dp(10));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView back = text("‹", 34, Theme.SECONDARY, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> showHome());
        top.addView(text("关于", 24, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addSettingRow(content, "隐私说明", "", () -> showTextDialog("隐私说明",
                "录音只上传到你自己的云端空间；麦克风仅在录音和语音修改时使用；身份是本机生成的匿名 ID。"));
        addSettingRow(content, "社区公约", "", () -> showTextDialog("社区公约", CommunityTerms.BODY));
        addSettingRow(content, "已屏蔽用户", blockStore.blockedList().size() + " 人", this::showBlockedUsers);
        addSettingRow(content, "联系我们 / 内容投诉", CommunityTerms.SUPPORT_EMAIL, () -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:" + CommunityTerms.SUPPORT_EMAIL + "?subject=VoiceDrop%20反馈与投诉"));
            try { startActivity(intent); } catch (Exception e) { toast("无法打开邮件客户端"); }
        });
    }

    // MARK: - Blocked Users

    private void showBlockedUsers() {
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(14) + getStatusBarHeight(), dp(16), dp(10));
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView back = text("‹", 34, Theme.SECONDARY, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> showHome());
        top.addView(text("已屏蔽用户", 24, Theme.INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(10), dp(16), dp(28));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        java.util.List<String> blocked = blockStore.blockedList();
        if (blocked.isEmpty()) {
            TextView empty = text("还没有屏蔽任何人", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
        } else {
            for (String name : blocked) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(13), dp(16), dp(13));
                row.setBackground(round(Theme.CARD, 12));
                TextView nameView = text(name, 16, Theme.INK, Typeface.BOLD);
                row.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1));
                TextView unblock = text("取消屏蔽", 14, Theme.RED, Typeface.BOLD);
                row.addView(unblock);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, dp(8));
                list.addView(row, lp);
                unblock.setOnClickListener(v -> {
                    blockStore.unblock(name);
                    showBlockedUsers();
                });
            }
        }
    }

    // MARK: - Community Terms Gate

    private void showCommunityTermsGate(Runnable onAgree) {
        new AlertDialog.Builder(this)
                .setTitle("社区公约")
                .setMessage(CommunityTerms.BODY)
                .setPositiveButton("同意并发布", (d, w) -> {
                    communityTerms.setAgreed(true);
                    onAgree.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        form.setPadding(pad, dp(8), pad, 0);
        return form;
    }

    private android.widget.EditText field(String hint) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(hint);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setPadding(0, dp(6), 0, dp(6));
        return input;
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;
        io.execute(() -> {
            try {
                if (Intent.ACTION_SEND.equals(action)) {
                    Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                    if (uri != null) uploadSharedUri(uri, "mine", 0);
                    else if (text != null) uploadSharedText(text.toString(), "mine", 0);
                } else {
                    ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (uris != null) {
                        for (int i = 0; i < uris.size(); i++) uploadSharedUri(uris.get(i), "mine", i);
                    }
                }
                toast("分享内容已送入 VoiceDrop");
            } catch (Exception e) {
                toast("分享导入失败：" + e.getMessage());
            }
        });
    }

    private void uploadSharedText(String text, String intent, int index) throws Exception {
        String suffix = index == 0 ? "" : "-" + index;
        String name = "VoiceDrop-" + intent + "-" + (System.currentTimeMillis() / 1000) + suffix + ".txt";
        http.putBytes(com.baixingai.voicedrop.net.Api.filesBase() + "/upload/" + name, auth.bearer(), "text/plain; charset=utf-8", text.getBytes("UTF-8"));
    }

    private void uploadSharedUri(Uri uri, String intent, int index) throws Exception {
        String suffix = index == 0 ? "" : "-" + index;
        String ext = "bin";
        String path = uri.getLastPathSegment();
        if (path != null && path.contains(".")) ext = path.substring(path.lastIndexOf('.') + 1);
        File tmp = new File(getCacheDir(), "share-" + System.nanoTime() + "." + ext);
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
        String name = "VoiceDrop-" + intent + "-" + (System.currentTimeMillis() / 1000) + suffix + "." + ext;
        http.putFile(com.baixingai.voicedrop.net.Api.filesBase() + "/upload/" + name, auth.bearer(), "application/octet-stream", tmp);
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private void addWaveBar(LinearLayout parent, int width, int height) {
        View bar = new View(this);
        bar.setBackground(round(Theme.RED, 2));
        parent.addView(bar, new LinearLayout.LayoutParams(width, height));
    }

    private TextView circleText(String value, int dp, int bg, int sp, int fg) {
        TextView view = text(value, sp, fg, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(bg, dp / 2));
        view.setMinWidth(dp(dp));
        view.setMinHeight(dp(dp));
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : (int) (24 * metrics.density);
    }

    private void toast(String message) {
        main.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private static final class CapturedPhoto {
        final String key;
        final byte[] bytes;
        final Bitmap bitmap;

        CapturedPhoto(String key, byte[] bytes, Bitmap bitmap) {
            this.key = key;
            this.bytes = bytes;
            this.bitmap = bitmap;
        }
    }
}
