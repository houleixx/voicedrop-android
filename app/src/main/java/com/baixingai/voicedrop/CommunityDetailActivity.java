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
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import com.baixingai.voicedrop.ui.SimpleToast;
import androidx.core.content.FileProvider;
import com.baixingai.voicedrop.audio.AudioRecorder;
import com.baixingai.voicedrop.audio.AsrDictationSession;
import com.baixingai.voicedrop.audio.RecordingQuality;
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
import com.baixingai.voicedrop.data.PendingReplyStore;
import com.baixingai.voicedrop.data.Prefs;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.data.UsageStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.net.ArticleEditSession;
import com.baixingai.voicedrop.net.StatusSession;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.BouncyScrollView;
import com.baixingai.voicedrop.ui.HoldToTalkGesture;
import com.baixingai.voicedrop.ui.HoldToTalkTranscript;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.PopupMenuPosition;
import com.baixingai.voicedrop.ui.RoundedImageView;
import com.baixingai.voicedrop.ui.Theme;
import com.kongzue.dialogx.dialogs.MessageDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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

import com.baixingai.voicedrop.audio.AudioRecorder;
import com.baixingai.voicedrop.core.ArticleBody;

import com.baixingai.voicedrop.data.ArticleDoc;
import com.baixingai.voicedrop.data.CommunityStore;
import com.baixingai.voicedrop.data.MinedArticle;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.PopupMenuPosition;
import com.baixingai.voicedrop.ui.Theme;
import com.kongzue.dialogx.dialogs.MessageDialog;

import java.util.ArrayList;
import java.util.List;
import java.time.ZonedDateTime;

public final class CommunityDetailActivity extends Activity {
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
    protected PendingReplyStore pendingReplies;
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
    private final HoldToTalkTranscript holdEditTranscript = new HoldToTalkTranscript();
    protected boolean holdEditCanceled;
    protected boolean holdEditFinishing;
    protected View holdEditButton;
    protected ImageView holdEditMicIcon;
    protected View holdEditTranscriptBubble;
    protected TextView holdEditTranscriptText;
    protected boolean articleLocatorsVisible;
    protected final List<View> articleLocatorViews = new ArrayList<>();
    protected final Map<String, Bitmap> articlePhotoCache = new HashMap<>();
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
    protected static final class CommunityRecordingUi {
        final TextView timer;
        final List<View> waveBars;
        int phase;

        CommunityRecordingUi(TextView timer, List<View> waveBars) {
            this.timer = timer;
            this.waveBars = waveBars;
        }
    }

    protected final Runnable communityTimerTick = new Runnable() {
        @Override public void run() {
            if (recorder != null && recorder.isRecording() && communityRecording) {
                updateCommunityRecordingTimer();
                main.postDelayed(this, 120);
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
        pendingReplies = new PendingReplyStore(this);
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
                main.post(CommunityDetailActivity.this::refreshAndDrain);
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
    protected boolean isRecordingDetailPage() {
        return false;
    }
    protected void refreshAndDrain() {
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
    protected void showRecordingDetailFromIntent() {
    }
    protected void showArticle(Recording rec, ArticleDoc doc) {
    }
    protected void showArticle(Recording rec, ArticleDoc doc, boolean animateOpen) {
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
    protected void renderArticleChips(LinearLayout chipRow, ArticleDoc doc, final Recording rec) {
    }
    protected void renderCurrentArticle(LinearLayout content, Recording rec, ArticleDoc doc) {
    }
    protected void ensureArticleEditSession(Recording rec) {
    }
    protected void renderArticleOrDefer(Recording rec, ArticleDoc doc) {
    }
    protected boolean isHoldArticleEditActiveFor(Recording rec) {
        return false;
    }
    protected void applyDeferredArticleRenderIfIdle() {
    }
    protected void renderArticleEditBar(FrameLayout page, Recording rec) {
    }
    protected void attachArticleEditHoldGesture(View speak, Recording rec) {
    }
    protected void startHoldArticleEdit(Recording rec, View button) {
    }
    protected void updateHoldArticleEditCancelState(View button, boolean canceled) {
    }
    protected void updateHoldArticleEditLiveText() {
    }
    protected void updateHoldArticleEditTranscriptBubble() {
    }
    protected SpannableString highlightHoldArticleEditTranscript(String text) {
        return new SpannableString(text == null ? "" : text);
    }
    protected void finishHoldArticleEdit(Recording rec, boolean forceCancel) {
    }
    protected void completeHoldArticleEdit(Recording rec, AsrDictationSession session) {
    }
    protected void resetHoldArticleEditButton() {
    }
    protected void updateHoldArticleEditButton(View container, String label, int bgColor, int textColor) {
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
    protected void publishWechat(Recording rec) {
    }
    protected void shareCommunity(Recording rec, String replyTo) {
    }
    protected void shareCommunityWithTermsGate(Recording rec, String replyTo) {
    }
    protected void doShareCommunity(Recording rec, String replyTo) {
    }
    protected void playRecordingAudio(Recording rec) {
    }
    protected void startPlayback(File file) {
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
    }
    protected void toggleDictation(android.widget.EditText input, TextView[] dictationBtn) {
    }
    protected void startArticleEdit(Recording rec, String instruction) {
    }
    protected void showStyleVersions(Recording rec) {
    }
    protected void showStyleVersionDialog(Recording rec, JSONObject history) {
    }
    protected String versionPreview(JSONObject item, int index) {
        return "";
    }
    protected void switchArticleHead(Recording rec, int head) {
    }
    protected void shiftArticleHead(Recording rec, int delta) {
    }
    protected void exportArticle(Recording rec, ArticleDoc doc) {
    }
    protected void openCommunityPost(CommunityStore.Post post) {
        Intent intent = new Intent(this, CommunityDetailActivity.class);
        intent.putExtra(EXTRA_SHARE_ID, post.shareId);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
    protected void shareRecording(Recording rec) {
    }
    protected void shareRecording(Recording rec, int section) {
    }
    protected void deleteRecording(Recording rec) {
    }
    protected void deleteRecording(Recording rec, Runnable afterSuccess) {
    }
    protected void confirmDeleteRecording(Recording rec, Runnable afterSuccess) {
    }
    protected void openCamera() {
    }
    protected void pickRecordingPhotos() {
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
    protected void addRecordingPhoto(Uri uri) {
    }
    protected void pickArticlePhoto(Recording rec) {
    }
    protected void uploadArticlePhoto(Recording rec, Uri uri) {
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
    // MARK: - Community Terms Gate

    protected void showCommunityTermsGate(Runnable onAgree) {
        TextView body = text(CommunityTerms.BODY, 15, Theme.INK, Typeface.NORMAL);
        body.setLineSpacing(dp(6), 1.0f);
        body.setPadding(dp(22), dp(12), dp(22), dp(18));
        IosDialog.show(this, "社区公约", body, 360, "同意并发布", () -> {
            communityTerms.setAgreed(true);
            onAgree.run();
        }, true);
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
    // MARK: - Article Detail More Menu

    protected void showMoreMenu(Recording rec, View anchor) {
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

    protected GradientDrawable roundStroke(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = round(color, radiusDp);
        drawable.setStroke(dp(strokeDp), strokeColor);
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

    protected int getNavigationBarHeight() {
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : dp(24);
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

    protected boolean isCommunityDetailPage() {
        return true;
    }
    protected void onPageCreate(Intent intent) {
        showCommunityDetailFromIntent();
    }
    protected void showCommunityDetailFromIntent() {
        String shareId = getIntent().getStringExtra(EXTRA_SHARE_ID);
        if (shareId == null || shareId.isEmpty()) {
            toast("无法识别社区文章");
            finish();
            return;
        }
        showDetailLoading();
        io.execute(() -> {
            try {
                CommunityStore.Post post = community.get(shareId);
                if (post == null) {
                    toast("社区文章不存在");
                    finish();
                    return;
                }
                ArticleDoc doc = post.doc != null ? post.doc : library.fetchDocByArticleKey(post.articleKey);
                main.post(() -> showCommunityPost(post, doc, false));
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
                finish();
            }
        });
    }
    protected void showCommunityPost(CommunityStore.Post post, ArticleDoc doc) {
        showCommunityPost(post, doc, false);
    }

    protected void showCommunityPost(CommunityStore.Post post, ArticleDoc doc, boolean animateOpen) {
        FrameLayout communityFrame = new FrameLayout(this);
        communityFrame.setBackgroundColor(Theme.BG);
        attachPage(communityFrame, animateOpen);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        communityFrame.addView(page, match());

        // Nav bar with like button and more menu
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12) + getStatusBarHeight(), dp(8), dp(8));
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        addNavBackButton(bar, this::leaveDetailPage);
        Space toolbarSpace = new Space(this);
        bar.addView(toolbarSpace, new LinearLayout.LayoutParams(0, dp(48), 1));

        final String shareId = post.shareId;
        final String authorName = communityAuthorName(post);
        final boolean[] liked = {prefs.likedCommunityPost(shareId)};
        final boolean[] fed = {false};
        final boolean[] feeding = {false};

        FrameLayout feedBtn = new FrameLayout(this);
        feedBtn.setClickable(true);
        final ImageView feedIcon = new ImageView(this);
        feedIcon.setImageResource(R.drawable.ic_settings_bolt);
        feedIcon.setColorFilter(Theme.INK);
        feedIcon.setScaleType(ImageView.ScaleType.CENTER);
        feedBtn.addView(feedIcon, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        bar.addView(feedBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        io.execute(() -> {
            try {
                List<String> ids = new ArrayList<>();
                ids.add(shareId);
                Map<String, CommunityStore.FeedState> states = community.feedStates(ids);
                CommunityStore.FeedState state = states.get(shareId);
                if (state != null) {
                    main.post(() -> {
                        fed[0] = state.fed;
                        feedIcon.setColorFilter(fed[0] ? 0xffd99a1a : Theme.INK);
                    });
                }
            } catch (Exception ignored) {}
        });
        feedBtn.setOnClickListener(v -> {
            if (fed[0] || feeding[0]) return;
            feeding[0] = true;
            feedBtn.setAlpha(0.45f);
            io.execute(() -> {
                try {
                    CommunityStore.FeedResult result = community.feed(shareId);
                    main.post(() -> {
                        feeding[0] = false;
                        feedBtn.setAlpha(1f);
                        if (result.ok || result.already) {
                            fed[0] = true;
                            feedIcon.setColorFilter(0xffd99a1a);
                            if (result.already) toast("已经投过这篇了");
                            else toast("已投币：你 +" + suanliText(result.feederSuanli) + "，作者 +" + suanliText(result.authorSuanli) + " 算力");
                        } else if ("cannot_feed_own".equals(result.error)) {
                            toast("不能给自己的文章投币");
                        } else if ("pool_exhausted".equals(result.error)) {
                            toast("今日算力池已发完，明天再来");
                        } else if (result.needsWechatSignin()) {
                            toast("投币需要先用微信登录");
                        } else {
                            toast("投币失败，稍后再试");
                        }
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        feeding[0] = false;
                        feedBtn.setAlpha(1f);
                        toast("投币失败：" + e.getMessage());
                    });
                }
            });
        });

        // Like button
        FrameLayout likeBtn = new FrameLayout(this);
        likeBtn.setClickable(true);
        final ImageView likeIcon = new ImageView(this);
        AliIconFont.apply(likeIcon, liked[0] ? AliIconFont.HEART_FILLED : AliIconFont.HEART,
                liked[0] ? Theme.RED : Theme.INK);
        likeIcon.setScaleType(ImageView.ScaleType.CENTER);
        likeBtn.addView(likeIcon, new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
        LinearLayout.LayoutParams likeLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        likeLp.setMargins(0, 0, 0, 0);
        bar.addView(likeBtn, likeLp);
        likeBtn.setOnClickListener(v -> {
            liked[0] = !liked[0];
            prefs.setLikedCommunityPost(shareId, liked[0]);
            AliIconFont.apply(likeIcon, liked[0] ? AliIconFont.HEART_FILLED : AliIconFont.HEART,
                    liked[0] ? Theme.RED : Theme.INK);
            io.execute(() -> {
                try {
                    community.engage(shareId, "like", liked[0]);
                } catch (Exception ignored) {}
            });
        });

        // ... menu
        toolbarIconButton(bar, Theme.CARD, 11, AliIconFont.MORE, Theme.SECONDARY,
                dp(18), dp(38), dp(2), true, v -> showCommunityPostMenu(post, authorName, v));

        BouncyScrollView scroll = new BouncyScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(12), dp(22), dp(24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView title = text(post.title == null || post.title.isEmpty() ? "社区文章" : post.title,
                26, Theme.INK, Typeface.BOLD);
        title.setLineSpacing(dp(5), 1.0f);
        content.addView(title);

        TextView meta = text("", 14, Theme.FAINT, Typeface.NORMAL);
        meta.setText(communityMetaText(post));
        meta.setPadding(0, dp(9), 0, dp(14));
        content.addView(meta);

        final LinearLayout replyToSection = new LinearLayout(this);
        replyToSection.setOrientation(LinearLayout.VERTICAL);
        content.addView(replyToSection);

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
                List<CommunityStore.Post> fullReplies = new ArrayList<>();
                for (CommunityStore.Post reply : replies) {
                    CommunityStore.Post full = community.get(reply.shareId);
                    fullReplies.add(full == null ? reply : full);
                }
                main.post(() -> renderReplies(repliesSection, fullReplies, post));
            } catch (Exception ignored) {}

            if (post.replyTo != null && !post.replyTo.isEmpty()) {
                try {
                    CommunityStore.Post original = community.get(post.replyTo);
                    if (original != null) main.post(() -> renderReplyToChip(replyToSection, original));
                } catch (Exception ignored) {}
            }
        });

        // Recording bar at the bottom (visible while recording a reply)
        final FrameLayout recordingBarContainer = new FrameLayout(this);
        recordingBarContainer.setVisibility(View.GONE);
        page.addView(recordingBarContainer, new LinearLayout.LayoutParams(-1, -2));

        // Store references for the recording bar
        final CommunityStore.Post finalPost = post;
        root.setTag(new Object[]{post, recordingBarContainer, scroll, content});
    }

    protected void startCommunityReplyRecording(CommunityStore.Post post) {
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
            main.postDelayed(communityTimerTick, 120);
        } catch (Exception e) {
            toast("无法开始录音：" + e.getMessage());
            replyToShareId = null;
            communityRecording = false;
        }
    }

    protected void showCommunityRecordingBar(CommunityStore.Post post) {
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
        addNavBackButton(bar, () -> {
            cancelCommunityReplyRecording();
            showCommunityPost(post, null);
        });
        bar.addView(text("回复: " + (post.title == null ? "社区文章" : post.title), 16, Theme.INK, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1));

        // Article content (scrollable)
        BouncyScrollView scroll = new BouncyScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(14), dp(20), dp(24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content.addView(text(post.title == null || post.title.isEmpty() ? "社区文章" : post.title, 24, Theme.INK, Typeface.BOLD));
        String authorName = post.author == null || post.author.isEmpty() ? "匿名作者" : post.author;
        content.addView(text(authorName, 13, Theme.SECONDARY, Typeface.NORMAL));

        View topBorder = new View(this);
        topBorder.setBackgroundColor(0xffe8decf);
        page.addView(topBorder, new LinearLayout.LayoutParams(-1, dp(1)));

        // Recording bar at bottom, matching the iOS community response dock.
        LinearLayout recBar = new LinearLayout(this);
        recBar.setOrientation(LinearLayout.HORIZONTAL);
        recBar.setGravity(Gravity.CENTER_VERTICAL);
        recBar.setMinimumHeight(dp(112));
        recBar.setPadding(dp(20), dp(16), dp(20), dp(20) + getNavigationBarHeight());
        recBar.setBackgroundColor(Theme.CARD);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, -2);
        page.addView(recBar, barLp);

        // Red dot + timer
        View dot = new View(this);
        dot.setBackground(round(0xffffb8b8, 4));
        recBar.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        final TextView timerText = text("00:00", 18, Theme.INK, Typeface.BOLD);
        timerText.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        timerText.setPadding(dp(10), 0, 0, 0);
        recBar.addView(timerText);

        // Waveform
        LinearLayout wave = new LinearLayout(this);
        wave.setGravity(Gravity.CENTER);
        wave.setOrientation(LinearLayout.HORIZONTAL);
        int[] bars = new int[]{3, 4, 3, 5, 4, 3, 4, 3};
        List<View> waveBars = new ArrayList<>();
        for (int i = 0; i < bars.length; i++) {
            int h = bars[i];
            View b = new View(this);
            b.setBackground(round(0xffefaaa7, 2));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(3), dp(h));
            blp.setMargins(dp(2), 0, dp(2), 0);
            wave.addView(b, blp);
            waveBars.add(b);
        }
        wave.setGravity(Gravity.CENTER);
        recBar.addView(wave, new LinearLayout.LayoutParams(0, dp(34), 1));

        // Stop button
        FrameLayout stopHalo = new FrameLayout(this);
        stopHalo.setBackground(round(0x18e5392e, 34));
        FrameLayout stopBtn = new FrameLayout(this);
        stopBtn.setClickable(true);
        stopBtn.setBackground(roundStroke(Theme.CARD, 28, 0xffe8decf, 1));
        View stopSquare = new View(this);
        stopSquare.setBackground(round(Theme.RED, 7));
        stopBtn.addView(stopSquare, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        stopHalo.addView(stopBtn, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(dp(68), dp(68));
        stopLp.setMargins(dp(14), 0, 0, 0);
        recBar.addView(stopHalo, stopLp);
        stopBtn.setOnClickListener(v -> stopCommunityReplyRecording());

        // Store timer reference for updates
        recBar.setTag(new CommunityRecordingUi(timerText, waveBars));
    }

    protected void updateCommunityRecordingTimer() {
        // Find the timer TextView from the current view
        ViewGroup rootPage = root.getChildCount() == 0 || !(root.getChildAt(0) instanceof ViewGroup)
                ? null
                : (ViewGroup) root.getChildAt(0);
        if (rootPage == null) return;
        // Find the recording bar (last child of page)
        for (int i = rootPage.getChildCount() - 1; i >= 0; i--) {
            View child = rootPage.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) child;
                // Check if this looks like the recording bar (has a tagged timer)
                Object tag = ll.getTag();
                if (tag instanceof CommunityRecordingUi) {
                    CommunityRecordingUi ui = (CommunityRecordingUi) tag;
                    TextView timerText = ui.timer;
                    long elapsed = recorder.elapsedSeconds();
                    timerText.setText(String.format("%02d:%02d", elapsed / 60, elapsed % 60));
                    updateCommunityWaveform(ui);
                    return;
                }
            }
        }
    }

    protected void updateCommunityWaveform(CommunityRecordingUi ui) {
        int amp = recorder == null ? 0 : recorder.sampleCurrentAmplitude();
        double level = amp < 500 ? 0.0 : Math.min(1.0, amp / 9000.0);
        int[] quiet = new int[]{3, 4, 3, 5, 4, 3, 4, 3};
        double[] pattern = new double[]{0.32, 0.76, 0.48, 1.0, 0.58, 0.86, 0.42, 0.68};
        if (level > 0.0) ui.phase++;
        for (int i = 0; i < ui.waveBars.size(); i++) {
            View bar = ui.waveBars.get(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
            int height;
            if (level == 0.0) {
                height = dp(quiet[i % quiet.length]);
            } else {
                double wave = pattern[(i + ui.phase) % pattern.length];
                height = dp(5) + (int) (dp(28) * Math.min(1.0, level * wave));
            }
            if (lp.height != height) {
                lp.height = height;
                bar.setLayoutParams(lp);
            }
        }
    }

    protected void cancelCommunityReplyRecording() {
        communityRecording = false;
        main.removeCallbacks(communityTimerTick);
        if (recorder != null && recorder.isRecording()) recorder.cancel();
        capturedPhotos.clear();
        replyToShareId = null;
        recordingStart = null;
    }

    protected void stopCommunityReplyRecording() {
        communityRecording = false;
        main.removeCallbacks(communityTimerTick);
        AudioRecorder.Take take = recorder.stop(null);
        List<CapturedPhoto> photos = new ArrayList<>(capturedPhotos);
        capturedPhotos.clear();
        String replyId = replyToShareId;
        replyToShareId = null;
        recordingStart = null;

        if (take != null) {
            if (replyId != null) pendingReplies.put(take.file.getName(), replyId);
            io.execute(() -> {
                uploadCapturedPhotos(photos);
                boolean uploaded = uploader.upload(take.file);
                main.post(() -> {
                    SimpleToast.show(this, uploaded
                            ? "回应已保存，成文后自动发布"
                            : "回应已保存，网络恢复后继续上传");
                    finishDetailActivity();
                });
            });
        }
    }

    protected void renderReplies(LinearLayout section, List<CommunityStore.Post> replies, CommunityStore.Post parentPost) {
        if (replies == null || replies.isEmpty()) return;
        section.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(0, dp(30), 0, 0);
        section.addView(header, headerLp);

        View left = new View(this);
        left.setBackgroundColor(0xffddd5c7);
        header.addView(left, new LinearLayout.LayoutParams(0, 1, 1));
        TextView count = text(replies.size() + " 篇回应", 12, 0xffa79f93, Typeface.BOLD);
        count.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
        countLp.setMargins(dp(12), 0, dp(12), 0);
        header.addView(count, countLp);
        View right = new View(this);
        right.setBackgroundColor(0xffddd5c7);
        header.addView(right, new LinearLayout.LayoutParams(0, 1, 1));

        for (CommunityStore.Post reply : replies) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, dp(26), 0, 0);

            View accent = new View(this);
            accent.setBackground(round(0xffe8c7b8, 2));
            row.addView(accent, new LinearLayout.LayoutParams(dp(3), -1));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(16), 0, 0, 0);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout byline = new LinearLayout(this);
            byline.setOrientation(LinearLayout.HORIZONTAL);
            byline.setGravity(Gravity.CENTER_VERTICAL);
            texts.addView(byline);

            String replyAuthor = reply.author == null || reply.author.isEmpty() ? "匿名" : reply.author;
            TextView author = text(replyAuthor, 13, Theme.RED, Typeface.BOLD);
            author.setSingleLine(true);
            byline.addView(author);
            TextView timeText = text("  续文 · " + formatCommunityDate(reply.firstSharedAt), 12, 0xff9a9387, Typeface.NORMAL);
            timeText.setSingleLine(true);
            byline.addView(timeText);
            if (reply.title != null && !reply.title.isEmpty()) {
                TextView title = text(reply.title, 19, 0xff2b2823, Typeface.BOLD);
                title.setPadding(0, dp(8), 0, 0);
                title.setLineSpacing(dp(5), 1.0f);
                texts.addView(title);
            }

            String preview = replyPreviewText(reply);
            if (!preview.isEmpty()) {
                TextView body = text(preview, 16, 0xff4f4942, Typeface.NORMAL);
                body.setPadding(0, dp(10), 0, 0);
                body.setLineSpacing(dp(8), 1.0f);
                body.setMaxLines(8);
                body.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(body);
                if (preview.length() > 160) {
                    TextView more = text("继续阅读 ↓", 13, Theme.RED, Typeface.BOLD);
                    more.setPadding(0, dp(10), 0, 0);
                    texts.addView(more);
                }
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            section.addView(row, lp);
            row.setOnClickListener(v -> openCommunityPost(reply));
        }
    }

    protected String replyPreviewText(CommunityStore.Post reply) {
        if (reply == null || reply.doc == null || reply.doc.articles.isEmpty()) return "";
        String preview = reply.doc.articles.get(0).body;
        preview = preview.replaceAll("\\[\\[photo:[^\\]]+\\]\\]", " ");
        preview = preview.replaceAll("[#>*`\\-]+", " ");
        preview = preview.replaceAll("\\s+", " ").trim();
        return preview.length() > 600 ? preview.substring(0, 600) : preview;
    }

    protected void renderReplyToChip(LinearLayout section, CommunityStore.Post original) {
        section.removeAllViews();
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(10), dp(6), dp(8), dp(6));
        chip.setBackground(round(0xfffff1ed, 18));
        chip.setClickable(true);

        ImageView replyIcon = new ImageView(this);
        replyIcon.setImageResource(R.drawable.ic_reply_turn_flat);
        replyIcon.setColorFilter(Theme.RED);
        chip.addView(replyIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView prefix = text("回应", 13, Theme.RED, Typeface.BOLD);
        prefix.setPadding(dp(5), 0, dp(6), 0);
        chip.addView(prefix);

        String title = original.title == null || original.title.isEmpty()
                ? "原文"
                : original.title;
        TextView titleText = text(title, 13, Theme.RED, Typeface.NORMAL);
        titleText.setSingleLine(true);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setMaxWidth(getResources().getDisplayMetrics().widthPixels - dp(134));
        chip.addView(titleText, new LinearLayout.LayoutParams(-2, -2));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right_flat);
        chevron.setColorFilter(Theme.RED);
        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        chevronLp.setMargins(dp(6), 0, 0, 0);
        chip.addView(chevron, chevronLp);

        chip.setOnClickListener(v -> openCommunityPost(original));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, 0, dp(14));
        section.addView(chip, lp);
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

    protected String communityAuthorName(CommunityStore.Post post) {
        String author = post.author == null ? "" : post.author.trim();
        return author.isEmpty() ? "匿名作者" : author;
    }

    protected String communityMetaLine(CommunityStore.Post post) {
        String date = formatCommunityDate(post.firstSharedAt);
        String author = communityAuthorName(post);
        return date.isEmpty() ? author : author + "  " + date;
    }

    protected SpannableString communityMetaText(CommunityStore.Post post) {
        String author = communityAuthorName(post);
        SpannableString span = new SpannableString(communityMetaLine(post));
        span.setSpan(new ForegroundColorSpan(Theme.RED), 0, author.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, author.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    protected void showCommunityPostMenu(CommunityStore.Post post, String authorName, View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(0, dp(3), 0, dp(3));
        menu.setBackground(round(0xf9ffffff, 16));
        menu.setElevation(dp(8));
        final PopupWindow[] popupRef = {null};

        LinearLayout replyRow = menuRow("写回应", AliIconFont.MIC, Theme.RED, Theme.INK);
        replyRow.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            startCommunityReplyRecording(post);
        });
        menu.addView(replyRow);
        menu.addView(divider());

        LinearLayout shareRow = menuRow("分享", AliIconFont.SHARE_UP, Theme.RED, Theme.INK);
        shareRow.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            shareCommunityUrl(post);
        });
        menu.addView(shareRow);
        menu.addView(divider());

        LinearLayout reportRow = menuRow("举报", AliIconFont.FLAG, Theme.RED, Theme.RED);
        reportRow.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            showReportConfirm(post);
        });
        menu.addView(reportRow);
        menu.addView(divider());

        LinearLayout blockRow = menuRow("屏蔽此用户", AliIconFont.HAND, Theme.RED, Theme.RED);
        blockRow.setOnClickListener(v -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            showBlockConfirm(post, authorName);
        });
        menu.addView(blockRow);

        popupRef[0] = showDetailMorePopup(menu, anchor);
    }

    protected void showReportConfirm(CommunityStore.Post post) {
        IosDialog.show(this, "举报这篇分享？", "举报后这篇会立即从社区下架，并在 24 小时内由人工审核处理。", "举报并下架", () -> {
            io.execute(() -> {
                try {
                    community.report(post.shareId);
                    main.post(() -> {
                        toast("已举报，内容已下架待审核");
                        posts.removeIf(p -> p.shareId.equals(post.shareId));
                        leaveDetailPage();
                    });
                } catch (Exception e) {
                    main.post(() -> toast("举报失败：" + e.getMessage()));
                }
            });
        });
    }

    protected void showBlockConfirm(CommunityStore.Post post, String authorName) {
        IosDialog.show(this, "屏蔽此用户？", "屏蔽后，你将不再看到 " + authorName + " 的任何社区内容。可在「设置」>「关于」>「已屏蔽用户」中取消屏蔽。",
                "屏蔽", () -> {
                    blockStore.block(post.author);
                    posts.removeIf(p -> (p.author == null ? "" : p.author).equals(post.author));
                    toast("已屏蔽，TA 的内容将不再显示");
                    leaveDetailPage();
                });
    }

    protected void shareCommunityUrl(CommunityStore.Post post) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, com.baixingai.voicedrop.net.Api.sharePage(post.shareId));
        startActivity(Intent.createChooser(intent, "分享社区文章"));
    }

    protected String suanliText(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(java.util.Locale.US, "%.1f", value);
    }

    protected void uploadCapturedPhotos(List<CapturedPhoto> photos) {
        for (CapturedPhoto photo : photos) {
            try {
                http.putBytes(com.baixingai.voicedrop.net.Api.filesBase() + "/upload/" + com.baixingai.voicedrop.net.Api.path(photo.key),
                        auth.bearer(), "image/jpeg", photo.bytes);
            } catch (Exception e) {
                toast("照片上传失败：" + e.getMessage());
            }
        }
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
                if (key != null) {
                    ProgressBar spinner = new ProgressBar(this);
                    spinner.setIndeterminate(true);
                    tintLoadingSpinner(spinner);
                    spinner.setTag("photo_loading");
                    photo.addView(spinner, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
                }
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
                if (key != null) loadPhotoInto(photo, key, doc.ownerScope);
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
        loadPhotoInto(frame, relKey, null);
    }

    protected void loadPhotoInto(FrameLayout frame, String relKey, String preferredScope) {
        io.execute(() -> {
            try {
                String scope = normalizePhotoScope(
                        preferredScope == null || preferredScope.isEmpty() ? library.ownerScope() : preferredScope);
                if (scope == null) return;
                String cacheKey = scope + relKey;
                Bitmap cached = articlePhotoCache.get(cacheKey);
                if (cached != null) {
                    main.post(() -> showLoadedPhoto(frame, cached));
                    return;
                }
                Bitmap bitmap = library.photoImage(scope + relKey, false);
                if (bitmap == null) return;
                articlePhotoCache.put(cacheKey, bitmap);
                main.post(() -> showLoadedPhoto(frame, bitmap));
            } catch (Exception ignored) {
            }
        });
    }

    protected String normalizePhotoScope(String scope) {
        if (scope == null || scope.isEmpty()) return null;
        return scope.endsWith("/") ? scope : scope + "/";
    }

    protected void showLoadedPhoto(FrameLayout frame, Bitmap bitmap) {
        for (int i = frame.getChildCount() - 1; i >= 0; i--) {
            View child = frame.getChildAt(i);
            Object tag = child.getTag();
            if ("photo_loading".equals(tag)) {
                frame.removeViewAt(i);
            }
        }
        ImageView image = new RoundedImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap);
        frame.addView(image, 0, match());
        frame.post(() -> {
            int width = frame.getWidth();
            if (width <= 0 || bitmap.getWidth() <= 0) return;
            int height = Math.max(dp(72), Math.round(width * (bitmap.getHeight() / (float) bitmap.getWidth())));
            ViewGroup.LayoutParams lp = frame.getLayoutParams();
            if (lp != null && lp.height != height) {
                lp.height = height;
                frame.setLayoutParams(lp);
            }
        });
    }

    protected void showDetailLoading() {
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12) + getStatusBarHeight(), dp(8), dp(8));
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        addNavBackButton(bar, this::finishDetailActivity);

        Space toolbarSpace = new Space(this);
        bar.addView(toolbarSpace, new LinearLayout.LayoutParams(0, dp(48), 1));

        FrameLayout content = new FrameLayout(this);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams loadingLp = new FrameLayout.LayoutParams(-1, dp(180), Gravity.TOP);
        loadingLp.topMargin = dp(50);
        content.addView(new LoadingStateView(this), loadingLp);
    }

    protected void tintLoadingSpinner(ProgressBar spinner) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Theme.RED));
        } else {
            spinner.getIndeterminateDrawable().setColorFilter(Theme.RED,
                    android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

}
