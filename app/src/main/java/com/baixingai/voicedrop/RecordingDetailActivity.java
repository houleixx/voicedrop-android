package com.baixingai.voicedrop;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import com.baixingai.voicedrop.ui.SimpleToast;
import androidx.core.content.FileProvider;
import com.baixingai.voicedrop.audio.AudioRecorder;
import com.baixingai.voicedrop.audio.AsrDictationSession;
import com.baixingai.voicedrop.audio.RecordingQuality;
import com.baixingai.voicedrop.audio.Uploader;
import com.baixingai.voicedrop.core.ArticlePhotoInsert;
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
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.HoldToTalkGesture;
import com.baixingai.voicedrop.ui.HoldToTalkTranscript;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.PopupMenuPosition;
import com.baixingai.voicedrop.ui.Theme;
import com.kongzue.dialogx.dialogs.MessageDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baixingai.voicedrop.audio.AsrDictationSession;
import com.baixingai.voicedrop.core.ArticleBody;
import com.baixingai.voicedrop.net.ArticleEditSession;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.HoldToTalkGesture;
import com.baixingai.voicedrop.ui.HoldToTalkTranscript;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.PopupMenuPosition;
import com.baixingai.voicedrop.ui.Theme;
import com.kongzue.dialogx.dialogs.MessageDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.data.ArticleDoc;
import com.baixingai.voicedrop.data.CommunityTerms;
import com.baixingai.voicedrop.data.MinedArticle;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.net.HttpClient;

public final class RecordingDetailActivity extends Activity {
    public static final String EXTRA_AUDIO_NAME = "audioName";
    public static final String EXTRA_SHARE_ID = "shareId";
    protected final Handler main = new Handler(Looper.getMainLooper());
    protected final ExecutorService io = Executors.newSingleThreadExecutor();
    protected final ExecutorService dictationIo = Executors.newSingleThreadExecutor();
    protected AuthStore auth;
    protected Prefs prefs;
    protected HttpClient http;
    protected LibraryStore library;
    protected CommunityStore community;
    protected BlockStore blockStore;
    protected CommunityTerms communityTerms;
    protected SettingsStore settingsStore;
    protected UsageStore usageStore;
    protected DeviceLinkStore deviceLinkStore;
    protected ExportManager exportManager;
    protected DeviceLinkSession deviceLinkSession;
    protected String pendingLinkPairingId;
    protected String pendingLinkPubkey;
    protected Uploader uploader;
    protected AudioRecorder recorder;
    protected StatusSession statusSession;
    protected ArticleEditSession editSession;
    protected AsrDictationSession dictationSession;
    protected MediaPlayer mediaPlayer;
    protected ArticleDoc currentArticleDoc;
    protected String currentArticleStem;
    protected Recording deferredArticleRenderRecording;
    protected ArticleDoc deferredArticleRenderDoc;
    protected List<ArticleEditSession.EditRequest> editQueue = new ArrayList<>();
    protected String editReply;
    protected boolean editReplyOk = true;
    protected boolean holdEditCanceled;
    protected boolean holdEditFinishing;
    protected View holdEditButton;
    protected ImageView holdEditMicIcon;
    protected View holdEditTranscriptBubble;
    protected TextView holdEditTranscriptText;
    protected boolean articleLocatorsVisible;
    protected final List<View> articleLocatorViews = new ArrayList<>();
    protected ZonedDateTime recordingStart;
    protected Recording insertPhotoTarget;
    protected FrameLayout root;
    protected List<Recording> recordings = new ArrayList<>();
    protected List<CommunityStore.Post> posts = new ArrayList<>();
    protected final List<CapturedPhoto> capturedPhotos = new ArrayList<>();
    // Currently open swipe-to-delete rows
    protected final List<LinearLayout> openSwipeRows = new ArrayList<>();
    protected boolean communityTab;
    protected boolean loading;
    protected boolean topLevelUiRendered;
    protected int articleIndex;                // current article section index in multi-article docs
    protected String replyToShareId;           // when recording a reply to a community post
    protected boolean communityRecording;      // true while recording a community reply
    protected final Runnable communityTimerTick = new Runnable() {
        @Override public void run() {
            if (recorder != null && recorder.isRecording() && communityRecording) {
                updateCommunityRecordingTimer();
                main.postDelayed(this, 500);
            }
        }
    };
    protected final Runnable timerTick = new Runnable() {
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
                main.post(RecordingDetailActivity.this::refreshAndDrain);
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
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        // Edge-to-edge: content extends behind status bar and navigation bar
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(0x00000000);
            getWindow().setNavigationBarColor(0x00000000);
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            getWindow().setStatusBarColor(0x00000000);
            getWindow().setNavigationBarColor(0x00000000);
        }
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        onPageCreate(getIntent());
    }
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // When user taps anywhere and a swipe row is open (but not on that row itself), close it
        if (ev.getAction() == MotionEvent.ACTION_DOWN && !openSwipeRows.isEmpty()) {
            closeOpenSwipes();
        }
        return super.dispatchTouchEvent(ev);
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!isDetailActivity()) handleShareIntent(intent);
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (!isDetailActivity()) {
            if (ResumeRefreshPolicy.shouldRedrawOnResume(false, topLevelUiRendered)) {
                refreshAndDrain();
            } else {
                refreshDataSilently();
            }
            if (statusSession != null) statusSession.connect();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(timerTick);
        main.removeCallbacks(communityTimerTick);
        if (isDetailActivity()) cleanupDetailResources();
        if (statusSession != null) statusSession.close();
        if (editSession != null) editSession.close();
        if (dictationSession != null) dictationSession.stop();
        if (deviceLinkSession != null) deviceLinkSession.cancel();
        stopPlayback();
        io.shutdownNow();
        dictationIo.shutdownNow();
    }
    @Override
    public void onBackPressed() {
        if (isDetailActivity()) {
            finishDetailActivity();
        } else {
            super.onBackPressed();
        }
    }
    protected void markPhase(String stem, String status) {
        for (Recording rec : recordings) {
            if (rec.stem().equals(stem)) rec.phase = status;
        }
        if (!communityTab) showHome();
    }
    protected boolean isDetailActivity() {
        return isRecordingDetailPage() || isCommunityDetailPage();
    }
    protected boolean isRecordingsPage() {
        return false;
    }
    protected boolean isCommunityPage() {
        return false;
    }
    protected boolean isCommunityDetailPage() {
        return false;
    }
    protected void refreshDataInBackground() {
    }
    protected void refreshDataSilently() {
    }
    protected List<CommunityStore.Post> loadRankedCommunityPosts() throws Exception {
        List<CommunityStore.Post> all = community.list();
        List<CommunityStore.Post> visible = new ArrayList<>();
        for (CommunityStore.Post p : all) {
            if (!blockStore.isBlocked(p.author)) visible.add(p);
        }
        applyCommunityRanking(visible);
        return visible;
    }
    protected void applyCommunityRanking(List<CommunityStore.Post> visible) {
        try {
            CommunityStore.Ranking ranking = community.rank(visible);
            Set<String> liked = new HashSet<>(ranking.liked);
            prefs.setLikedCommunityPosts(liked);

            if (ranking.order.size() != visible.size()) return;
            Map<String, CommunityStore.Post> byId = new HashMap<>();
            for (CommunityStore.Post post : visible) byId.put(post.shareId, post);
            List<CommunityStore.Post> reordered = new ArrayList<>();
            for (String shareId : ranking.order) {
                CommunityStore.Post post = byId.get(shareId);
                if (post != null) reordered.add(post);
            }
            if (reordered.size() == visible.size()) {
                visible.clear();
                visible.addAll(reordered);
            }
        } catch (Exception ignored) {
            // Ranking is best-effort; keep the community list usable if reco is unavailable.
        }
    }
    protected void showHome() {
    }
    protected TextView tab(String label, boolean selected) {
        TextView view = text(label, 15, selected ? 0xffffffff : Theme.SECONDARY, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(22), dp(10), dp(22), dp(10));
        view.setBackground(round(selected ? Theme.RED : 0x00ffffff, 20));
        return view;
    }
    protected void renderCommunityList(LinearLayout list) {
    }
    protected void updateCommunityRecordingTimer() {
    }
    protected View recordingRow(Recording rec) {
        return new View(this);
    }
    protected void closeOpenSwipes() {
        for (LinearLayout r : openSwipeRows) {
            if (r.getTranslationX() != 0) {
                r.animate().translationX(0).setDuration(200).start();
            }
        }
        openSwipeRows.clear();
    }
    protected void showRecording(boolean first) {
    }
    protected View buildWaveform(double level) {
        return new View(this);
    }
    protected View recordingFilmstrip() {
        return new View(this);
    }
    protected void startRecordingFlow() {
    }
    protected void stopRecordingFlow() {
    }
    protected void buildHomeShell(AudioRecorder.Take take, List<CapturedPhoto> photos) {
    }
    protected void confirmSilentRecording(AudioRecorder.Take take, List<CapturedPhoto> photos) {
    }
    protected void uploadTake(AudioRecorder.Take take, List<CapturedPhoto> photos) {
    }
    protected void openRecording(Recording rec) {
    }
    protected void attachPage(View page, boolean animateOpen) {
        if (!animateOpen || root.getChildCount() == 0) {
            root.removeAllViews();
            root.addView(page, match());
            return;
        }
        View previous = root.getChildAt(0);
        int width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        page.setTranslationX(width);
        root.addView(page, match());
        previous.animate()
                .translationX(-width * 0.22f)
                .alpha(0.96f)
                .setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        page.animate()
                .translationX(0)
                .setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    root.removeView(previous);
                    page.setTranslationX(0);
                })
                .start();
    }
    protected void showHomeFromArticle() {
        if (root.getChildCount() == 0) {
            showHome();
            return;
        }
        View current = root.getChildAt(root.getChildCount() - 1);
        int width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        current.animate()
                .translationX(width)
                .alpha(0.98f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    current.setTranslationX(0);
                    current.setAlpha(1f);
                    showHome();
                })
                .start();
    }
    protected void leaveDetailPage() {
        if (isRecordingDetailPage() || isCommunityDetailPage()) {
            finishDetailActivity();
        } else {
            showHomeFromArticle();
        }
    }
    protected void finishDetailActivity() {
        cleanupDetailResources();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
    protected void cleanupDetailResources() {
        main.removeCallbacks(timerTick);
        main.removeCallbacks(communityTimerTick);
        if (dictationSession != null) {
            dictationSession.stop();
            dictationSession = null;
        }
        if (editSession != null) {
            editSession.close();
            editSession = null;
        }
        stopPlayback();
        if (communityRecording && recorder != null && recorder.isRecording()) {
            recorder.cancel();
        }
        communityRecording = false;
        replyToShareId = null;
        capturedPhotos.clear();
        recordingStart = null;
        insertPhotoTarget = null;
        holdEditButton = null;
        holdEditMicIcon = null;
        holdEditTranscriptBubble = null;
        holdEditTranscriptText = null;
        holdEditCanceled = false;
        holdEditFinishing = false;
        setArticleLocatorsVisible(false);
        articleLocatorViews.clear();
        currentArticleDoc = null;
        currentArticleStem = null;
        deferredArticleRenderRecording = null;
        deferredArticleRenderDoc = null;
    }
    protected GradientDrawable cardBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.CARD);
        bg.setCornerRadius(dp(11));
        bg.setStroke(dp(1), 0xffe0d8cc);
        return bg;
    }
    protected void addNavBackButton(LinearLayout parent, Runnable action) {
        toolbarIconButton(parent, Theme.CARD, 11, AliIconFont.BACK, Theme.INK,
                dp(18), dp(40), 0, true, action);
    }
    protected ImageView iconImageView(int resId, int size) {
        ImageView iv = new ImageView(this);
        AliIconFont.apply(iv, resId, Theme.INK);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return iv;
    }
    protected void iconButton(LinearLayout parent, int bgColor, int cornerRadiusDp,
                            int iconResId, int iconColor, int iconSize, Runnable action) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(cornerRadiusDp));
        if (bgColor != Theme.RED) {
            bg.setStroke(dp(1), 0xffe0d8cc);
        }

        FrameLayout btn = new FrameLayout(this);
        btn.setBackground(bg);
        btn.setElevation(dp(3));
        btn.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        btn.setClickable(true);

        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, iconResId, iconColor);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        btn.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(10), 0, 0, 0);
        parent.addView(btn, lp);
        btn.setOnClickListener(v -> action.run());
    }
    protected void toolbarIconButton(LinearLayout parent, int bgColor, int cornerRadiusDp,
                                   int iconResId, int iconColor, int iconSize, int boxSize,
                                   int leftMargin, boolean elevated, Runnable action) {
        toolbarIconButton(parent, bgColor, cornerRadiusDp, iconResId, iconColor, iconSize,
                boxSize, leftMargin, elevated, v -> action.run());
    }
    protected void toolbarIconButton(LinearLayout parent, int bgColor, int cornerRadiusDp,
                                   int iconResId, int iconColor, int iconSize, int boxSize,
                                   int leftMargin, boolean elevated, View.OnClickListener action) {
        FrameLayout touch = new FrameLayout(this);
        touch.setClickable(true);
        FrameLayout btn = new FrameLayout(this);
        if (bgColor != 0x00ffffff) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(bgColor);
            bg.setCornerRadius(dp(cornerRadiusDp));
            if (bgColor != Theme.RED) bg.setStroke(dp(1), 0xffe0d8cc);
            btn.setBackground(bg);
        }
        if (elevated) btn.setElevation(dp(2));

        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, iconResId, iconColor);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        btn.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        touch.addView(btn, new FrameLayout.LayoutParams(boxSize, boxSize, Gravity.CENTER));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.setMargins(leftMargin, 0, 0, 0);
        parent.addView(touch, lp);
        touch.setOnClickListener(action);
    }
    protected void addAction(LinearLayout actions, String label, Runnable action) {
        TextView button = text(label, 14, Theme.INK, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(round(Theme.CARD, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(0, 0, dp(8), 0);
        actions.addView(button, lp);
        button.setOnClickListener(v -> action.run());
    }
    protected void shareCommunityWithTermsGate(Recording rec, String replyTo) {
    }
    protected void openCommunityPost(CommunityStore.Post post) {
        Intent intent = new Intent(this, CommunityDetailActivity.class);
        intent.putExtra(EXTRA_SHARE_ID, post.shareId);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
    protected void showCommunityDetailFromIntent() {
    }
    protected void showCommunityPost(CommunityStore.Post post, ArticleDoc doc) {
    }
    protected void showCommunityPost(CommunityStore.Post post, ArticleDoc doc, boolean animateOpen) {
    }
    protected String formatCommunityDate(double ms) {
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
    protected void openCamera() {
    }
    protected void pickRecordingPhotos() {
    }
    protected void addRecordingPhoto(Uri uri) {
    }
    protected void uploadCapturedPhotos(List<CapturedPhoto> photos) {
    }
    protected void showDeviceLinkApproval(String pairingId, String code, String pubkey) {
        pendingLinkPairingId = pairingId;
        pendingLinkPubkey = pubkey;
        IosDialog.show(this, "设备登录请求", "有新设备想登录当前账号。\n\n验证码：" + code + "\n\n如果不是你本人操作，请点\"不是我\"。",
                "是我", () -> toast("请在新设备输入验证码 " + code),
                "不是我", () -> io.execute(() -> {
                    try {
                        deviceLinkStore.cancel(pairingId);
                    } catch (Exception ignored) {
                    }
                    if (pairingId.equals(pendingLinkPairingId)) {
                        pendingLinkPairingId = null;
                        pendingLinkPubkey = null;
                    }
                }));
    }
    protected void releaseDeviceLink(String pairingId) {
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
    protected void handleShareIntent(Intent intent) {
    }
    protected void uploadSharedText(String text, String intent, int index) throws Exception {
    }
    protected void uploadSharedUri(Uri uri, String intent, int index) throws Exception {
    }
    protected TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }
    // MARK: - Article Date Formatting

    protected String formatArticleTitle(Recording rec) {
        RecordingName.Parsed parsed = RecordingName.parse(rec.stem());
        if (parsed == null) return rec.rowTitle();
        String month = (parsed.month != null ? parsed.month : 0) + "月";
        String day = (parsed.day != null ? parsed.day : 0) + "日";
        String period = parsedPeriod(rec);
        return month + day + period;
    }
    protected String formatArticleSubtitle(Recording rec) {
        RecordingName.Parsed parsed = RecordingName.parse(rec.stem());
        if (parsed == null) return "";
        String month = (parsed.month != null ? parsed.month : 0) + "月";
        String day = (parsed.day != null ? parsed.day : 0) + "日";
        String time = parsed.hhmm != null ? parsed.hhmm : "";
        return month + day + (time.isEmpty() ? "" : " " + time);
    }
    protected String parsedPeriod(Recording rec) {
        // Extract period from the filename (e.g. "Morning", "Afternoon")
        String[] p = rec.stem().split("-");
        for (String part : p) {
            switch (part) {
                case "EarlyMorning": return "凌晨";
                case "Morning": return "上午";
                case "Noon": return "中午";
                case "Afternoon": return "下午";
                case "Evening": return "晚上";
                case "Night": return "夜间";
                case "LateNight": return "深夜";
            }
        }
        return "";
    }
    protected void addWaveBar(LinearLayout parent, int width, int height) {
        View bar = new View(this);
        bar.setBackground(round(Theme.RED, 2));
        parent.addView(bar, new LinearLayout.LayoutParams(width, height));
    }
    protected TextView circleText(String value, int dp, int bg, int sp, int fg) {
        TextView view = text(value, sp, fg, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(bg, dp / 2));
        view.setMinWidth(dp(dp));
        view.setMinHeight(dp(dp));
        return view;
    }
    protected GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }
    protected FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }
    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    /** Position underline under the active tab (mainTitle or communityTabView) */
    protected void updateUnderline(View underline, TextView mainTitle, TextView communityTabView, boolean isCommunityActive) {
        TextView activeTab = isCommunityActive ? communityTabView : mainTitle;
        int tabW = activeTab.getWidth();
        int lineW = (int) (tabW * 0.8);
        int offset = (tabW - lineW) / 2;
        underline.setLayoutParams(new LinearLayout.LayoutParams(lineW, dp(3)));
        underline.setTranslationX(activeTab.getLeft() + offset);
        underline.setVisibility(View.VISIBLE);
    }
    protected int getStatusBarHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : (int) (24 * metrics.density);
    }
    protected void toast(String message) {
        main.post(() -> SimpleToast.show(this, message));
    }
    protected static final class CapturedPhoto {
        final String key;
        final byte[] bytes;
        final Bitmap bitmap;

        CapturedPhoto(String key, byte[] bytes, Bitmap bitmap) {
            this.key = key;
            this.bytes = bytes;
            this.bitmap = bitmap;
        }
    }

    private final HoldToTalkTranscript holdEditTranscript = new HoldToTalkTranscript();
    protected boolean isRecordingDetailPage() {
        return true;
    }
    protected void onPageCreate(Intent intent) {
        showRecordingDetailFromIntent();
    }
    protected void showRecordingDetailFromIntent() {
        String audioName = getIntent().getStringExtra(EXTRA_AUDIO_NAME);
        if (audioName == null || audioName.isEmpty()) {
            toast("无法识别录音");
            finish();
            return;
        }
        Recording rec = new Recording(audioName, "", true, false);
        io.execute(() -> {
            ArticleDoc doc = library.fetchDoc(rec);
            main.post(() -> showArticle(rec, doc, false));
        });
    }
    protected void refreshAndDrain() {
        showRecordingDetailFromIntent();
    }

    protected void showArticle(Recording rec, ArticleDoc doc) {
        showArticle(rec, doc, false);
    }

    protected void showArticle(Recording rec, ArticleDoc doc, boolean animateOpen) {
        if (doc == null || doc.articles.isEmpty()) {
            toast("文章暂不可读");
            return;
        }
        if (!rec.stem().equals(currentArticleStem)) {
            articleIndex = 0;
            editQueue = new ArrayList<>();
            editReply = null;
            deferredArticleRenderRecording = null;
            deferredArticleRenderDoc = null;
        } else {
            articleIndex = Math.min(articleIndex, doc.articles.size() - 1);
        }
        currentArticleStem = rec.stem();
        currentArticleDoc = doc;
        ensureArticleEditSession(rec);

        FrameLayout articleFrame = new FrameLayout(this);
        articleFrame.setBackgroundColor(Theme.BG);
        attachPage(articleFrame, animateOpen);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        articleFrame.addView(page, match());

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12) + getStatusBarHeight(), dp(8), dp(8));
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        addNavBackButton(bar, this::leaveDetailPage);

        TextView title = text("录音详情", 20, Theme.INK, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout iconRow = new LinearLayout(this);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(iconRow, new LinearLayout.LayoutParams(-2, dp(48)));

        toolbarIconButton(iconRow, Theme.RED, 17, AliIconFont.PLAY, 0xffffffff, dp(15), dp(34), dp(2), true, () -> playRecordingAudio(rec));
        toolbarIconButton(iconRow, Theme.CARD, 11, AliIconFont.IMAGE, Theme.INK, dp(22), dp(40), dp(2), true, () -> pickArticlePhoto(rec));

        FrameLayout undoTouch = new FrameLayout(this);
        undoTouch.setClickable(true);
        FrameLayout undoRedoBtn = new FrameLayout(this);
        undoRedoBtn.setBackground(cardBg());
        undoRedoBtn.setElevation(dp(2));

        LinearLayout undoRedoInner = new LinearLayout(this);
        undoRedoInner.setOrientation(LinearLayout.HORIZONTAL);
        undoRedoInner.setGravity(Gravity.CENTER);
        undoRedoInner.setPadding(dp(10), 0, dp(10), 0);
        undoRedoBtn.addView(undoRedoInner, new FrameLayout.LayoutParams(-2, dp(40), Gravity.CENTER));

        ImageView undoIcon = iconImageView(AliIconFont.UNDO, dp(17));
        undoRedoInner.addView(undoIcon);

        View vDivider = new View(this);
        vDivider.setBackgroundColor(0xffe0d8cc);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(dp(1), dp(20));
        dividerLp.setMargins(dp(10), 0, dp(10), 0);
        undoRedoInner.addView(vDivider, dividerLp);

        ImageView redoIcon = iconImageView(AliIconFont.REDO, dp(17));
        redoIcon.setAlpha(0.32f);
        undoRedoInner.addView(redoIcon);

        undoTouch.addView(undoRedoBtn, new FrameLayout.LayoutParams(-2, dp(40), Gravity.CENTER));
        LinearLayout.LayoutParams undoLp = new LinearLayout.LayoutParams(-2, dp(48));
        undoLp.setMargins(dp(2), 0, 0, 0);
        iconRow.addView(undoTouch, undoLp);
        undoTouch.setOnClickListener(v -> {
            if (editQueue.isEmpty()) shiftArticleHead(rec, -1);
            else shiftArticleHead(rec, 1);
        });

        toolbarIconButton(iconRow, Theme.CARD, 11, AliIconFont.MORE, Theme.SECONDARY, dp(18), dp(38), dp(2), true, v -> showMoreMenu(rec, v));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(22), dp(12), dp(22), dp(148));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderCurrentArticle(content, rec, doc);
        renderArticleEditBar(articleFrame, rec);
    }

    protected void publishWechat(Recording rec) {
        toast("正在发布到公众号…");
        io.execute(() -> {
            try {
                JSONObject cfg = settingsStore.loadWechat();
                if (cfg.optString("appid", "").trim().isEmpty()
                        || cfg.optString("secret", "").trim().isEmpty()) {
                    main.post(() -> openWechatSettings("请先配置公众号"));
                    return;
                }
                LibraryStore.PublishResult result = library.publishWechat(rec);
                if (result.notConfigured) {
                    main.post(() -> openWechatSettings("请先配置公众号"));
                    return;
                }
                if (result.ok) {
                    ArticleDoc updated = library.fetchDoc(rec);
                    main.post(() -> {
                        if (updated != null && !updated.articles.isEmpty()) {
                            showArticle(rec, updated, false);
                        }
                        toast(result.created == 0 && result.updated > 0 ? "已更新草稿" : "已到草稿箱");
                    });
                    return;
                }
                if (result.isConfigError()) {
                    main.post(() -> openWechatSettings(result.message == null ? "公众号配置有误" : result.message));
                } else {
                    toast(result.message == null ? "推送失败，请稍后再试" : result.message);
                }
            } catch (Exception e) {
                toast("公众号发布失败：" + e.getMessage());
            }
        });
    }

    private void openWechatSettings(String message) {
        toast(message);
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_SHOW_WECHAT, true);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    protected boolean hasWechatDraft() {
        if (currentArticleDoc == null || currentArticleDoc.articles == null) return false;
        for (MinedArticle article : currentArticleDoc.articles) {
            if (article.wechatMediaId != null && !article.wechatMediaId.trim().isEmpty()) return true;
        }
        return false;
    }

    protected void shareCommunity(Recording rec, String replyTo) {
        if (!communityTerms.agreed()) {
            showCommunityTermsGate(() -> doShareCommunity(rec, replyTo));
        } else {
            doShareCommunity(rec, replyTo);
        }
    }

    protected void doShareCommunity(Recording rec, String replyTo) {
        io.execute(() -> {
            try {
                String shareId = community.share(rec, replyTo);
                toast(shareId == null || shareId.isEmpty() ? "社区分享失败，可能需要 Apple 会话" : "已分享到社区");
            } catch (Exception e) {
                toast("社区分享失败：" + e.getMessage());
            }
        });
    }

    protected void showCommunityTermsGate(Runnable onAgree) {
        TextView body = text(CommunityTerms.BODY, 15, Theme.INK, Typeface.NORMAL);
        body.setLineSpacing(dp(6), 1.0f);
        body.setPadding(dp(22), dp(12), dp(22), dp(18));
        IosDialog.show(this, "社区公约", body, 360, "同意并发布", () -> {
            communityTerms.setAgreed(true);
            onAgree.run();
        }, true);
    }

    protected void playRecordingAudio(Recording rec) {
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

    protected void startPlayback(File file) {
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

    protected void stopPlayback() {
        if (mediaPlayer == null) return;
        try {
            mediaPlayer.stop();
        } catch (Exception ignored) {
        }
        mediaPlayer.release();
        mediaPlayer = null;
    }

    protected void showVoiceEdit(Recording rec) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setMinLines(3);
        input.setHint("输入你想怎么改这篇文章。会通过 /agent/edit WebSocket 串行提交。");
        final TextView[] dictationBtn = {null};
        IosDialog.show(this, "语音修改", input,
                "提交修改", () -> startArticleEdit(rec, input.getText().toString()),
                "语音听写", () -> toggleDictation(input, dictationBtn));
    }

    protected void toggleDictation(android.widget.EditText input, TextView[] dictationBtn) {
        if (dictationSession != null && dictationSession.isRunning()) {
            dictationSession.stop();
            if (dictationBtn[0] != null) dictationBtn[0].setText("语音听写");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
            toast("请授权麦克风后再开始听写");
            return;
        }
        if (dictationBtn[0] != null) dictationBtn[0].setText("停止听写");
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
                    if (dictationBtn[0] != null) dictationBtn[0].setText("语音听写");
                    toast("听写失败：" + message);
                });
            }
        });
        dictationSession.start();
    }

    protected void startArticleEdit(Recording rec, String instruction) {
        if (dictationSession != null) dictationSession.stop();
        ensureArticleEditSession(rec);
        editSession.enqueue(instruction, articleIndex);
    }

    protected void showStyleVersions(Recording rec) {
        io.execute(() -> {
            try {
                JSONObject history = library.versionHistory(rec);
                main.post(() -> showStyleVersionDialog(rec, history));
            } catch (Exception e) {
                toast("版本历史加载失败：" + e.getMessage());
            }
        });
    }

    protected void showStyleVersionDialog(Recording rec, JSONObject history) {
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
        IosDialog.show(this, "文风 / 版本", form,
                "重挖文风", () -> io.execute(() -> {
                    try {
                        int version = Integer.parseInt(input.getText().toString());
                        boolean ok = library.restyle(rec, version);
                        toast(ok ? "已请求生成该文风版本" : "文风版本请求失败");
                    } catch (Exception e) {
                        toast("文风版本请求失败：" + e.getMessage());
                    }
                }),
                "切换 head", () -> io.execute(() -> {
                    try {
                        int head = Integer.parseInt(headInput.getText().toString());
                        boolean ok = library.patchHead(rec, head);
                        toast(ok ? "已切换文章版本" : "切换失败");
                        if (ok) refreshAndDrain();
                    } catch (Exception e) {
                        toast("切换失败：" + e.getMessage());
                    }
                }));
    }

    protected String versionPreview(JSONObject item, int index) {
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

    protected void switchArticleHead(Recording rec, int head) {
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

    protected void shiftArticleHead(Recording rec, int delta) {
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

    protected void exportArticle(Recording rec, ArticleDoc doc) {
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

    protected void shareRecording(Recording rec) {
        shareRecording(rec, articleIndex);
    }

    protected void shareRecording(Recording rec, int section) {
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

    protected void deleteRecording(Recording rec) {
        deleteRecording(rec, null);
    }

    protected void deleteRecording(Recording rec, Runnable afterSuccess) {
        io.execute(() -> {
            boolean ok = library.delete(rec);
            if (ok) {
                main.post(() -> {
                    if (afterSuccess != null) afterSuccess.run();
                    else refreshAndDrain();
                });
            } else {
                toast("删除失败");
            }
        });
    }

    protected void confirmDeleteRecording(Recording rec, Runnable afterSuccess) {
        new MessageDialog("删除这条录音？", "音频和已挖出的文章都会从云端删除，不可恢复。", "删除", "取消")
                .setOkButton("删除", (dialog, v) -> {
                    deleteRecording(rec, afterSuccess);
                    return false;
                })
                .setCancelable(true)
                .show();
    }

    protected void pickArticlePhoto(Recording rec) {
        insertPhotoTarget = rec;
        Intent intent = new Intent(this, InsertPhotoActivity.class);
        startActivityForResult(intent, 21);
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 21 && resultCode == RESULT_OK && data != null && insertPhotoTarget != null) {
            ArrayList<String> paths = data.getStringArrayListExtra(InsertPhotoActivity.EXTRA_PHOTO_PATHS);
            long[] captureTimes = data.getLongArrayExtra(InsertPhotoActivity.EXTRA_CAPTURE_TIMES);
            Recording target = insertPhotoTarget;
            insertPhotoTarget = null;
            if (paths != null && !paths.isEmpty()) uploadArticlePhotos(target, paths, captureTimes);
        }
    }

    protected void uploadArticlePhotos(Recording rec, List<String> paths, long[] captureTimes) {
        toast("正在上传图片…");
        io.execute(() -> {
            try {
                RecordingName.Parsed parsed = RecordingName.parse(rec.stem());
                if (parsed == null) throw new IllegalStateException("无法识别录音时间戳");
                List<ArticleEditSession.AgentImage> images = new ArrayList<>();
                List<String> keys = new ArrayList<>();
                for (int i = 0; i < paths.size(); i++) {
                    File file = new File(paths.get(i));
                    byte[] bytes;
                    try (InputStream in = new FileInputStream(file)) {
                        bytes = HttpClient.readAll(in);
                    }
                    long capturedAt = captureTimes != null && i < captureTimes.length && captureTimes[i] > 0
                            ? captureTimes[i]
                            : System.currentTimeMillis();
                    int offset = ArticlePhotoInsert.offsetSeconds(parsed.sessionTs,
                            ZonedDateTime.ofInstant(Instant.ofEpochMilli(capturedAt), ZoneId.systemDefault()));
                    String key = RecordingName.photoKey(parsed.sessionTs, offset);
                    http.putBytes(com.baixingai.voicedrop.net.Api.filesBase() + "/upload/" + com.baixingai.voicedrop.net.Api.path(key),
                            auth.bearer(), "image/jpeg", bytes);
                    keys.add(key);
                    String thumb = ArticlePhotoInsert.thumbnailBase64(bytes, 320);
                    if (thumb != null) images.add(new ArticleEditSession.AgentImage(key, thumb));
                    // Best-effort cleanup; cache misses are harmless.
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
                main.post(() -> {
                    ensureArticleEditSession(rec);
                    editSession.enqueue(ArticlePhotoInsert.instructionForKeys(keys), articleIndex, images);
                    toast("图片已上传，AI正在插入…");
                });
            } catch (Exception e) {
                toast("照片上传失败：" + e.getMessage());
            }
        });
    }
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            toast("权限被拒绝");
            return;
        }
        if (requestCode == 12) toast("已授权，请重新按住说话修改");
    }

    protected void showMoreMenu(Recording rec, View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(0, dp(3), 0, dp(3));
        menu.setBackground(round(0xf9ffffff, 16));
        menu.setElevation(dp(8));
        final PopupWindow[] popupRef = {null};

        LinearLayout pubRow = menuRow(hasWechatDraft() ? "更新公众号草稿" : "发布公众号草稿", AliIconFont.PAPERPLANE, Theme.RED);
        pubRow.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            publishWechat(rec);
        });
        menu.addView(pubRow);
        menu.addView(divider());

        LinearLayout commRow = menuRow("VD 社区可见", AliIconFont.PEOPLE, Theme.RED);
        commRow.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            shareCommunity(rec, null);
        });
        menu.addView(commRow);
        menu.addView(divider());

        LinearLayout shareRow = menuRow("分享", AliIconFont.SHARE_UP, Theme.RED);
        shareRow.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            shareRecording(rec);
        });
        menu.addView(shareRow);
        View thickDivider = new View(this);
        thickDivider.setBackgroundColor(0xffe9e5dc);
        menu.addView(thickDivider, new LinearLayout.LayoutParams(-1, dp(8)));

        LinearLayout delRow = menuRow("删除", AliIconFont.TRASH, Theme.RED);
        delRow.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            confirmDeleteRecording(rec, this::leaveDetailPage);
        });
        menu.addView(delRow);

        popupRef[0] = showDetailMorePopup(menu, anchor);
    }

    protected LinearLayout menuRow(String label, int iconResId, int iconColor) {
        int textColor = label.equals("删除") ? Theme.RED : Theme.INK;
        return menuRow(label, iconResId, iconColor, textColor);
    }

    protected LinearLayout menuRow(String label, int iconResId, int iconColor, int textColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(16), 0);
        row.setMinimumHeight(dp(48));
        TextView text = text(label, 17, textColor, Typeface.NORMAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text);
        ImageView iconView = new ImageView(this);
        AliIconFont.apply(iconView, iconResId, iconColor);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(24), dp(24)));
        return row;
    }

    protected PopupWindow showDetailMorePopup(LinearLayout menu, View anchor) {
        int popupWidth = dp(260);
        PopupWindow popup = new PopupWindow(menu, popupWidth, -2, true);
        popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));
        popup.showAsDropDown(anchor,
                PopupMenuPosition.rightAlignedXOffset(anchor.getWidth(), popupWidth) - dp(5),
                dp(10));
        return popup;
    }

    protected View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xffe0d8cc);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return v;
    }

    protected void renderArticleChips(LinearLayout chipRow, ArticleDoc doc, final Recording rec) {
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
                showArticle(rec, doc);
            });
        }
    }

    protected void renderCurrentArticle(LinearLayout content, Recording rec, ArticleDoc doc) {
        content.removeAllViews();
        content.setTag(rec);
        MinedArticle article = doc.articles.get(articleIndex);
        TextView title = text(article.title, 23, Theme.INK, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        title.setLineSpacing(dp(5), 1.0f);
        content.addView(title);

        TextView subtitle = text(formatArticleSubtitle(rec), 13, Theme.FAINT, Typeface.NORMAL);
        subtitle.setPadding(0, dp(8), 0, dp(20));
        content.addView(subtitle);

        if (doc.articles.size() > 1) {
            HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
            chipsScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chipsScroll.addView(chips);
            renderArticleChips(chips, doc, rec);
            LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(-1, -2);
            chipsLp.setMargins(0, 0, 0, dp(20));
            content.addView(chipsScroll, chipsLp);
        }

        renderArticleBody(content, articleBodyWithoutDuplicateTitle(article), doc);
    }

    protected void ensureArticleEditSession(Recording rec) {
        if (editSession != null && rec.stem().equals(editSession.stem())) {
            editSession.connect();
            return;
        }
        if (editSession != null) editSession.close();
        editSession = new ArticleEditSession(this, auth, rec.stem(), new ArticleEditSession.Listener() {
            @Override public void onUpdated(ArticleDoc doc) {
                main.post(() -> {
                    currentArticleDoc = doc;
                    renderArticleOrDefer(rec, doc);
                });
            }

            @Override public void onQueueChanged(List<ArticleEditSession.EditRequest> queue) {
                main.post(() -> {
                    editQueue = new ArrayList<>(queue);
                    if (currentArticleDoc != null && rec.stem().equals(currentArticleStem)) {
                        renderArticleOrDefer(rec, currentArticleDoc);
                    }
                });
            }

            @Override public void onReply(String text, boolean ok) {
                main.post(() -> {
                    editReply = text;
                    editReplyOk = ok;
                    if (currentArticleDoc != null && rec.stem().equals(currentArticleStem)) {
                        renderArticleOrDefer(rec, currentArticleDoc);
                    }
                });
            }

            @Override public void onState(String state) {
                // Queue rows provide the visible working state; keep transient toasts quiet.
            }

            @Override public void onError(String message) {
                toast("修改失败：" + message);
            }
        });
        editSession.connect();
    }

    protected void renderArticleOrDefer(Recording rec, ArticleDoc doc) {
        if (isHoldArticleEditActiveFor(rec)) {
            deferredArticleRenderRecording = rec;
            deferredArticleRenderDoc = doc;
            return;
        }
        showArticle(rec, doc);
    }

    protected boolean isHoldArticleEditActiveFor(Recording rec) {
        return rec != null
                && rec.stem().equals(currentArticleStem)
                && (holdEditButton != null || holdEditFinishing);
    }

    protected void applyDeferredArticleRenderIfIdle() {
        if (holdEditButton != null || holdEditFinishing) return;
        if (deferredArticleRenderRecording == null || deferredArticleRenderDoc == null) return;
        if (!deferredArticleRenderRecording.stem().equals(currentArticleStem)) {
            deferredArticleRenderRecording = null;
            deferredArticleRenderDoc = null;
            return;
        }
        Recording rec = deferredArticleRenderRecording;
        ArticleDoc doc = deferredArticleRenderDoc;
        deferredArticleRenderRecording = null;
        deferredArticleRenderDoc = null;
        showArticle(rec, doc);
    }

    protected void renderArticleEditBar(FrameLayout page, Recording rec) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(18));

        // Reply / server response area
        if (editReply != null && !editReply.isEmpty()) {
            TextView reply = text((editReplyOk ? "✨ " : "⚠ ") + editReply, 14,
                    editReplyOk ? Theme.INK : Theme.RED, Typeface.NORMAL);
            reply.setPadding(dp(12), dp(8), dp(12), dp(8));
            reply.setBackground(round(Theme.CARD, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(8));
            panel.addView(reply, lp);
        }

        // Edit queue
        for (int i = editQueue.size() - 1; i >= 0; i--) {
            ArticleEditSession.EditRequest req = editQueue.get(i);
            boolean inFlight = i == 0;
            TextView row = text((inFlight ? "✎ " : "⏱ ") + req.text, 14,
                    inFlight ? Theme.INK : Theme.SECONDARY, Typeface.NORMAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setBackground(round(inFlight ? Theme.AMBER_BG : Theme.CARD, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(6));
            panel.addView(row, lp);
        }

        LinearLayout transcriptBubble = new LinearLayout(this);
        transcriptBubble.setOrientation(LinearLayout.VERTICAL);
        transcriptBubble.setVisibility(View.INVISIBLE);
        transcriptBubble.setAlpha(0f);

        TextView transcriptText = text("在听…", 16, 0xfffbf6ee, Typeface.NORMAL);
        transcriptText.setPadding(dp(14), dp(12), dp(14), dp(12));
        transcriptText.setGravity(Gravity.LEFT);
        transcriptText.setMinHeight(dp(48));
        transcriptText.setBackground(round(0xff2e2823, 16));
        transcriptText.setMaxLines(3);
        transcriptText.setEllipsize(TextUtils.TruncateAt.END);
        transcriptBubble.addView(transcriptText, new LinearLayout.LayoutParams(-1, -2));

        View tail = new View(this);
        tail.setBackgroundColor(0xff2e2823);
        tail.setRotation(45f);
        LinearLayout.LayoutParams tailLp = new LinearLayout.LayoutParams(dp(12), dp(12));
        tailLp.setMargins(dp(28), dp(-6), 0, dp(3));
        transcriptBubble.addView(tail, tailLp);

        panel.addView(transcriptBubble, new LinearLayout.LayoutParams(-1, -2));
        holdEditTranscriptBubble = transcriptBubble;
        holdEditTranscriptText = transcriptText;

        // Hold-to-talk button (iOS style: mic icon + text)
        LinearLayout editContainer = new LinearLayout(this);
        editContainer.setOrientation(LinearLayout.HORIZONTAL);
        editContainer.setGravity(Gravity.CENTER);
        editContainer.setPadding(dp(16), 0, dp(16), 0);
        editContainer.setBackground(round(Theme.CARD, 12));
        editContainer.setElevation(0);
        editContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52)));

        ImageView micIcon = new ImageView(this);
        AliIconFont.apply(micIcon, AliIconFont.MIC, Theme.INK);
        micIcon.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        micLp.setMargins(0, 0, dp(8), 0);
        editContainer.addView(micIcon, micLp);

        TextView speak = text(editQueue.isEmpty() ? "按住 说话 修改" : "正在改…按住继续说", 17, Theme.INK, Typeface.BOLD);
        speak.setGravity(Gravity.CENTER);
        speak.setMaxLines(2);
        speak.setEllipsize(TextUtils.TruncateAt.END);
        editContainer.addView(speak);

        panel.addView(editContainer);
        attachArticleEditHoldGesture(editContainer, rec);
        holdEditMicIcon = micIcon;
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        page.addView(panel, panelLp);
    }

    protected void attachArticleEditHoldGesture(View speak, Recording rec) {
        final float[] startY = {0};
        speak.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY[0] = event.getRawY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    startHoldArticleEdit(rec, speak);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateHoldArticleEditCancelState(speak,
                            HoldToTalkGesture.shouldCancel(startY[0], event.getRawY(), dp(64)));
                    return true;
                case MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    finishHoldArticleEdit(rec, false);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    finishHoldArticleEdit(rec, HoldToTalkGesture.shouldAbortOnEnd(true, holdEditCanceled));
                    return true;
                default:
                    return true;
            }
        });
    }

    protected void startHoldArticleEdit(Recording rec, View button) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 12);
            toast("请授权麦克风后再按住修改文章");
            return;
        }
        if (dictationSession != null && dictationSession.isRunning()) dictationSession.stop();
        holdEditTranscript.clear();
        holdEditCanceled = false;
        holdEditFinishing = false;
        holdEditButton = button;
        if (holdEditMicIcon != null) holdEditMicIcon.setColorFilter(Theme.AMBER);
        setArticleLocatorsVisible(true);
        updateHoldArticleEditTranscriptBubble();
        updateHoldArticleEditButton(button, "松开 发送 · 上滑取消", Theme.CARD, Theme.AMBER);
        // start() opens the WebSocket and starts the mic, blocking until recording is active.
        dictationSession = new AsrDictationSession(auth, new AsrDictationSession.Listener() {
            @Override public void onText(String text, boolean isFinal) {
                holdEditTranscript.accept(text, isFinal);
                main.post(() -> updateHoldArticleEditLiveText());
            }
            @Override public void onState(String state) {}
            @Override public void onError(String message) {
                main.post(() -> {
                    if (!holdEditFinishing) {
                        resetHoldArticleEditButton();
                        toast("听写失败：" + message);
                    }
                });
            }
        });
        final AsrDictationSession session = dictationSession;
        dictationIo.execute(() -> {
            session.start();
            if (!session.isRunning() && dictationSession == session && !holdEditFinishing) {
                main.post(() -> {
                    if (dictationSession == session && !holdEditFinishing) {
                        resetHoldArticleEditButton();
                        toast("听写连接超时，请重试");
                    }
                });
            }
        });
    }

    protected void updateHoldArticleEditCancelState(View button, boolean canceled) {
        if (dictationSession == null || !dictationSession.isRunning()) return;
        if (holdEditCanceled == canceled) return;
        holdEditCanceled = canceled;
        if (holdEditMicIcon != null) {
            holdEditMicIcon.setColorFilter(canceled ? Theme.RED : Theme.AMBER);
        }
        if (canceled) {
            updateHoldArticleEditButton(button, "上滑取消 · 松开放弃", 0xffffe5e5, Theme.RED);
        } else {
            updateHoldArticleEditButton(button, "松开 发送 · 上滑取消", Theme.CARD, Theme.AMBER);
        }
    }

    protected void updateHoldArticleEditLiveText() {
        if (holdEditButton == null || holdEditCanceled || holdEditFinishing) return;
        updateHoldArticleEditTranscriptBubble();
    }

    protected void updateHoldArticleEditTranscriptBubble() {
        if (holdEditTranscriptBubble == null || holdEditTranscriptText == null) return;
        holdEditTranscriptBubble.setVisibility(View.VISIBLE);
        holdEditTranscriptBubble.setAlpha(1f);
        holdEditTranscriptText.setText(highlightHoldArticleEditTranscript(holdEditTranscript.bubbleText()));
    }

    protected SpannableString highlightHoldArticleEditTranscript(String text) {
        SpannableString span = new SpannableString(text);
        Matcher matcher = Pattern.compile("第[0-9]+行|图[0-9]+").matcher(text);
        while (matcher.find()) {
            span.setSpan(new ForegroundColorSpan(0xfff0b59b), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    protected void finishHoldArticleEdit(Recording rec, boolean forceCancel) {
        if (dictationSession == null) {
            resetHoldArticleEditButton();
            return;
        }
        holdEditFinishing = true;
        boolean canceled = forceCancel || holdEditCanceled;
        final AsrDictationSession session = dictationSession;
        if (canceled) {
            // User swiped up to cancel — abort immediately, no need to wait for ASR.
            dictationIo.execute(session::stop);
            resetHoldArticleEditButton();
            toast("已取消语音修改");
            return;
        }
        // Normal release: gracefully finish — the session will wait for the ASR
        // server's final result and close the WebSocket asynchronously.
        updateHoldArticleEditButton(holdEditButton, "正在整理…", Theme.AMBER_BG, Theme.INK);
        dictationIo.execute(() -> session.finish(() -> main.post(() -> completeHoldArticleEdit(rec, session))));
    }

    protected void completeHoldArticleEdit(Recording rec, AsrDictationSession session) {
        if (dictationSession != session) return;
        String text = holdEditTranscript.bestText();
        resetHoldArticleEditButton();
        if (text.isEmpty()) {
            toast("没有识别到语音");
            return;
        }
        startArticleEdit(rec, text);
        toast("已发送修改：" + text);
    }

    protected void resetHoldArticleEditButton() {
        if (holdEditButton != null) {
            updateHoldArticleEditButton(holdEditButton,
                    editQueue.isEmpty() ? "按住 说话 修改" : "正在改…按住继续说",
                    Theme.CARD, Theme.INK);
        }
        setArticleLocatorsVisible(false);
        if (holdEditMicIcon != null) {
            holdEditMicIcon.setColorFilter(Theme.INK);
        }
        if (holdEditTranscriptBubble != null) {
            holdEditTranscriptBubble.setAlpha(0f);
            holdEditTranscriptBubble.setVisibility(View.INVISIBLE);
        }
        holdEditButton = null;
        holdEditMicIcon = null;
        holdEditTranscriptBubble = null;
        holdEditTranscriptText = null;
        holdEditCanceled = false;
        holdEditFinishing = false;
        applyDeferredArticleRenderIfIdle();
    }

    protected void updateHoldArticleEditButton(View container, String label, int bgColor, int textColor) {
        if (container == null) return;
        container.setBackground(round(bgColor, 12));
        // Find the TextView child to update text/color
        if (container instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) container;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    tv.setText(label);
                    tv.setTextColor(textColor);
                    break;
                }
            }
        }
    }

    protected void renderArticleBody(LinearLayout content, String bodyText, ArticleDoc doc) {
        articleLocatorViews.clear();
        int[] lineNo = {0};
        int[] imageNo = {0};
        for (ArticleBody.Segment segment : ArticleBody.segments(bodyText)) {
            if (segment.type == ArticleBody.Segment.Type.PHOTO) {
                String key = ArticleBody.resolvePhotoKey(segment.value, doc.photos);
                lineNo[0]++;
                imageNo[0]++;
                FrameLayout photo = new FrameLayout(this);
                photo.setBackground(round(0xfff1e7db, 10));
                TextView loading = text("图片 · " + (key == null ? segment.value : key), 13, Theme.SECONDARY, Typeface.NORMAL);
                loading.setGravity(Gravity.CENTER);
                loading.setTag("photo_loading");
                photo.addView(loading, match());
                TextView line = articleLocator(String.valueOf(lineNo[0]));
                FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(dp(20), -2, Gravity.LEFT | Gravity.TOP);
                lineLp.setMargins(-dp(24), dp(11), 0, 0);
                photo.addView(line, lineLp);
                TextView badge = text("图" + imageNo[0], 12, 0xffffffff, Typeface.BOLD);
                badge.setPadding(dp(8), dp(3), dp(8), dp(3));
                badge.setBackground(round(0x66000000, 6));
                FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT | Gravity.TOP);
                badgeLp.setMargins(dp(8), dp(34), 0, 0);
                photo.addView(badge, badgeLp);
                articleLocatorViews.add(badge);
                badge.setAlpha(articleLocatorsVisible ? 1f : 0f);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(90));
                p.setMargins(0, dp(12), 0, dp(12));
                content.addView(photo, p);
                if (key != null) loadPhotoInto(photo, key);
            } else {
                String[] lines = segment.value.split("\\n");
                for (String raw : lines) {
                    String paragraph = raw.trim();
                    if (paragraph.isEmpty()) continue;
                    lineNo[0]++;
                    FrameLayout row = new FrameLayout(this);
                    row.setClipChildren(false);
                    row.setClipToPadding(false);
                    TextView locator = articleLocator(String.valueOf(lineNo[0]));
                    FrameLayout.LayoutParams locatorLp = new FrameLayout.LayoutParams(dp(20), -2, Gravity.LEFT | Gravity.TOP);
                    locatorLp.setMargins(-dp(24), dp(4), 0, 0);
                    row.addView(locator, locatorLp);
                    TextView body = text(paragraph, 16, 0xff5d574f, Typeface.NORMAL);
                    body.setLineSpacing(dp(9), 1.0f);
                    body.setPadding(0, 0, 0, dp(22));
                    row.addView(body, new FrameLayout.LayoutParams(-1, -2));
                    content.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
            }
        }
    }

    protected TextView articleLocator(String label) {
        TextView locator = text(label, 11, Theme.RED, Typeface.BOLD);
        locator.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        locator.setPadding(0, 0, 0, 0);
        locator.setAlpha(articleLocatorsVisible ? 1f : 0f);
        articleLocatorViews.add(locator);
        return locator;
    }

    protected void setArticleLocatorsVisible(boolean visible) {
        articleLocatorsVisible = visible;
        for (View view : articleLocatorViews) {
            view.animate().alpha(visible ? 1f : 0f).setDuration(180).start();
        }
    }

    protected String articleBodyWithoutDuplicateTitle(MinedArticle article) {
        String body = ArticleBody.stripOriginComment(article.body);
        String title = article.title == null ? "" : article.title.trim();
        if (title.isEmpty() || body.isEmpty()) return body;
        String[] lines = body.split("\\n", -1);
        int firstContent = -1;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                firstContent = i;
                break;
            }
        }
        if (firstContent < 0) return body;
        String first = lines[firstContent].trim();
        String normalizedFirst = first.replaceFirst("^#{1,6}\\s*", "").trim();
        if (!normalizedFirst.equals(title)) return body;
        StringBuilder out = new StringBuilder();
        for (int i = firstContent + 1; i < lines.length; i++) {
            if (out.length() > 0) out.append('\n');
            out.append(lines[i]);
        }
        return out.toString().trim();
    }

    protected void loadPhotoInto(FrameLayout frame, String relKey) {
        io.execute(() -> {
            try {
                String scope = library.ownerScope();
                if (scope == null) return;
                byte[] data = library.photoData(scope + relKey);
                if (data == null) return;
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bitmap == null) return;
                main.post(() -> {
                    for (int i = frame.getChildCount() - 1; i >= 0; i--) {
                        View child = frame.getChildAt(i);
                        Object tag = child.getTag();
                        if ("photo_loading".equals(tag)) {
                            frame.removeViewAt(i);
                        }
                    }
                    ImageView image = new ImageView(this);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setImageBitmap(bitmap);
                    frame.addView(image, 0, match());
                });
            } catch (Exception ignored) {
            }
        });
    }

}
