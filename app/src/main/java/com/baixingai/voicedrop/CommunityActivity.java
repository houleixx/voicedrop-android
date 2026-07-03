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
import android.widget.ScrollView;
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
import com.baixingai.voicedrop.ui.PullRefreshLayout;
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

public final class CommunityActivity extends Activity {
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
    private final HoldToTalkTranscript holdEditTranscript = new HoldToTalkTranscript();
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
                main.post(CommunityActivity.this::refreshAndDrain);
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
    protected boolean isRecordingDetailPage() {
        return false;
    }
    protected boolean isCommunityDetailPage() {
        return false;
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
    protected TextView tab(String label, boolean selected) {
        TextView view = text(label, 15, selected ? 0xffffffff : Theme.SECONDARY, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(22), dp(10), dp(22), dp(10));
        view.setBackground(round(selected ? Theme.RED : 0x00ffffff, 20));
        return view;
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
    protected void renderArticleBody(LinearLayout content, String bodyText, ArticleDoc doc) {
    }
    protected TextView articleLocator(String label) {
        return text(label, 11, Theme.RED, Typeface.BOLD);
    }
    protected void setArticleLocatorsVisible(boolean visible) {
    }
    protected String articleBodyWithoutDuplicateTitle(MinedArticle article) {
        return article == null ? "" : ArticleBody.stripOriginComment(article.body);
    }
    protected void loadPhotoInto(FrameLayout frame, String relKey) {
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
    protected void uploadCapturedPhotos(List<CapturedPhoto> photos) {
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
    protected LinearLayout menuRow(String label, int iconResId, int iconColor) {
        int textColor = label.equals("删除") ? Theme.RED : Theme.INK;
        return menuRow(label, iconResId, iconColor, textColor);
    }
    protected LinearLayout menuRow(String label, int iconResId, int iconColor, int textColor) {
        return new LinearLayout(this);
    }
    protected PopupWindow showDetailMorePopup(LinearLayout menu, View anchor) {
        return new PopupWindow(menu, dp(260), -2, true);
    }
    protected View divider() {
        return new View(this);
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

    protected boolean isCommunityPage() {
        return true;
    }
    protected void onPageCreate(Intent intent) {
        handleShareIntent(intent);
        communityTab = true;
        showHome();
        refreshAndDrain();
        statusSession.connect();
    }
    protected void refreshAndDrain() {
        loading = true;
        showHome();
        io.execute(() -> {
            uploader.drainPending();
            try {
                posts = loadRankedCommunityPosts();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            loading = false;
            main.post(this::showHome);
        });
    }
    protected void refreshDataInBackground() {
        io.execute(() -> {
            uploader.drainPending();
            try {
                posts = loadRankedCommunityPosts();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            main.post(this::showHome);
        });
    }
    protected void refreshDataSilently() {
        io.execute(() -> {
            uploader.drainPending();
            try {
                posts = loadRankedCommunityPosts();
                main.post(this::showHome);
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
        });
    }

    protected void refreshCommunityFromPull(PullRefreshLayout refresher) {
        io.execute(() -> {
            uploader.drainPending();
            try {
                posts = loadRankedCommunityPosts();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            loading = false;
            main.post(() -> {
                refresher.setRefreshing(false);
                showHome();
            });
        });
    }

    protected void showHome() {
        topLevelUiRendered = true;
        root.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(18), dp(8) + getStatusBarHeight(), dp(12), dp(8));
        page.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout logo = new LinearLayout(this);
        logo.setOrientation(LinearLayout.HORIZONTAL);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        addWaveBar(logo, dp(3), dp(10));
        View gap1 = new View(this);
        gap1.setLayoutParams(new LinearLayout.LayoutParams(dp(2), 1));
        logo.addView(gap1);
        addWaveBar(logo, dp(3), dp(16));
        View gap2 = new View(this);
        gap2.setLayoutParams(new LinearLayout.LayoutParams(dp(2), 1));
        logo.addView(gap2);
        addWaveBar(logo, dp(3), dp(10));
        topBar.addView(logo);

        TextView logoText = text(" VoiceDrop 口述", 14, Theme.SECONDARY, Typeface.BOLD);
        logoText.setPadding(dp(6), 0, 0, 0);
        topBar.addView(logoText, new LinearLayout.LayoutParams(0, -2, 1));

        toolbarIconButton(topBar, Theme.CARD, 11, AliIconFont.SETTINGS, Theme.SECONDARY,
                dp(20), dp(40), 0, true, () -> {
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                });

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);
        titleRow.setPadding(dp(18), 0, dp(18), 0);
        page.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        TextView recordingsTitle = text("我的录音", 20, Theme.FAINT, Typeface.BOLD);
        recordingsTitle.setGravity(Gravity.CENTER);
        titleRow.addView(recordingsTitle, new LinearLayout.LayoutParams(0, -2, 1));
        recordingsTitle.setOnClickListener(v -> {
            Intent intent = new Intent(this, RecordingsActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        TextView communityTitle = text("VD社区", 20, Theme.RED, Typeface.BOLD);
        communityTitle.setGravity(Gravity.CENTER);
        communityTitle.setPadding(dp(8), dp(6), dp(8), dp(6));
        titleRow.addView(communityTitle, new LinearLayout.LayoutParams(0, -2, 1));

        View underline = new View(this);
        underline.setBackground(round(Theme.RED, 1));
        page.addView(underline, new LinearLayout.LayoutParams(0, dp(3)));
        titleRow.post(() -> updateUnderline(underline, recordingsTitle, communityTitle, true));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(10)));
        page.addView(spacer);

        FrameLayout contentArea = new FrameLayout(this);
        page.addView(contentArea, new LinearLayout.LayoutParams(-1, 0, 1));

        PullRefreshLayout refresher = pullRefreshContainer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(6), dp(14), dp(16));
        scroll.addView(list);
        refresher.addView(scroll, match());
        refresher.setOnRefreshListener(() -> refreshCommunityFromPull(refresher));
        contentArea.addView(refresher, match());

        renderCommunityList(list);
    }

    protected PullRefreshLayout pullRefreshContainer() {
        PullRefreshLayout refresher = new PullRefreshLayout(this);
        refresher.setColorSchemeColors(Theme.RED);
        refresher.setProgressBackgroundColorSchemeColor(Theme.CARD);
        return refresher;
    }
    protected void renderCommunityList(LinearLayout list) {
        if (loading && posts.isEmpty()) {
            LinearLayout loadingLayout = new LinearLayout(this);
            loadingLayout.setOrientation(LinearLayout.VERTICAL);
            loadingLayout.setGravity(Gravity.CENTER);
            ProgressBar spinner = new ProgressBar(this);
            spinner.setIndeterminate(true);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                spinner.setIndeterminateTintList(
                        android.content.res.ColorStateList.valueOf(Theme.RED));
            } else {
                spinner.getIndeterminateDrawable().setColorFilter(Theme.RED,
                        android.graphics.PorterDuff.Mode.SRC_IN);
            }
            loadingLayout.addView(spinner, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView empty = text("正在加载 VD社区…", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(14), 0, 0);
            loadingLayout.addView(empty);
            list.addView(loadingLayout, new LinearLayout.LayoutParams(-1, dp(180)));
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
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        row.setBackground(round(Theme.CARD, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(lp);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(round(0xfff6e4dc, 14));
        iconBox.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        ImageView docIcon = new ImageView(this);
        AliIconFont.apply(docIcon, AliIconFont.DOC, Theme.RED);
        docIcon.setScaleType(ImageView.ScaleType.CENTER);
        iconBox.addView(docIcon, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        row.addView(iconBox);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(dp(14), 0, dp(8), 0);
        row.addView(meta, new LinearLayout.LayoutParams(0, -2, 1));
        meta.addView(text(post.title == null || post.title.isEmpty() ? "社区文章" : post.title,
                16, Theme.INK, Typeface.BOLD));
        String dateStr = formatCommunityDate(post.firstSharedAt);
        TextView sub = text((post.author == null || post.author.isEmpty() ? "匿名作者" : post.author)
                + (dateStr.isEmpty() ? "" : " · " + dateStr), 13, Theme.FAINT, Typeface.NORMAL);
        sub.setPadding(0, dp(7), 0, 0);
        meta.addView(sub);

        TextView chevron = text("›", 30, 0xffcfc6b6, Typeface.NORMAL);
        row.addView(chevron);
        row.setOnClickListener(v -> openCommunityPost(post));
        return row;
    }
}
