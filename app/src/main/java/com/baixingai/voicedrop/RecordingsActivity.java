package com.baixingai.voicedrop;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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
import android.util.AttributeSet;
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
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
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
import com.baixingai.voicedrop.ui.HoldToTalkGesture;
import com.baixingai.voicedrop.ui.HoldToTalkTranscript;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.PopupMenuPosition;
import com.baixingai.voicedrop.ui.PullRefreshLayout;
import com.baixingai.voicedrop.ui.SoftCircleShadowFrameLayout;
import com.baixingai.voicedrop.ui.Theme;
import com.baixingai.voicedrop.update.AppUpdateManager;
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

public final class RecordingsActivity extends Activity {
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
    protected boolean communityLoading;
    protected boolean topLevelUiRendered;
    protected ViewPager homePager;
    protected HomePagerAdapter homePagerAdapter;
    protected TextView recordingsTabTitle;
    protected TextView communityTabTitle;
    protected View homeTabUnderline;
    protected boolean recordingsLoadAttempted;
    protected boolean communityLoadAttempted;
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
                main.post(RecordingsActivity.this::refreshAndDrain);
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
    protected boolean isCommunityPage() {
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
    protected void renderCommunityList(LinearLayout list) {
        if (communityLoading && posts.isEmpty()) {
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
            TextView empty = text("正在加载VD社区…", 16, Theme.SECONDARY, Typeface.NORMAL);
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
    protected void updateCommunityRecordingTimer() {
    }
    protected void closeOpenSwipes() {
        for (LinearLayout r : openSwipeRows) {
            if (r.getTranslationX() != 0) {
                r.animate().translationX(0).setDuration(200).start();
            }
        }
        openSwipeRows.clear();
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
    protected void pickArticlePhoto(Recording rec) {
    }
    protected void uploadArticlePhoto(Recording rec, Uri uri) {
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
        addWaveBar(parent, width, height, Theme.RED);
    }

    protected void addWaveBar(LinearLayout parent, int width, int height, int color) {
        View bar = new View(this);
        bar.setBackground(round(color, 2));
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

    protected boolean isRecordingsPage() {
        return true;
    }
    protected void onPageCreate(Intent intent) {
        handleShareIntent(intent);
        communityTab = false;
        showHome();
        refreshAndDrain();
        statusSession.connect();
        root.postDelayed(() -> AppUpdateManager.checkOnStartup(this), 1200);
    }
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }
    protected void refreshAndDrain() {
        loading = true;
        showHome();
        recordingsLoadAttempted = true;
        io.execute(() -> {
            uploader.drainPending();
            try {
                loadRecordingsAndPublishPendingReplies();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            loading = false;
            main.post(this::refreshHomePages);
        });
    }
    protected void refreshDataInBackground() {
        final boolean loadCommunity = communityTab;
        if (loadCommunity) {
            communityLoadAttempted = true;
            communityLoading = posts.isEmpty();
        } else {
            recordingsLoadAttempted = true;
        }
        io.execute(() -> {
            uploader.drainPending();
            try {
                if (loadCommunity) posts = loadRankedCommunityPosts();
                else loadRecordingsAndPublishPendingReplies();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            if (loadCommunity) communityLoading = false;
            main.post(this::refreshHomePages);
        });
    }
    protected void refreshDataSilently() {
        io.execute(() -> {
            uploader.drainPending();
            try {
                loadRecordingsAndPublishPendingReplies();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
        });
    }

    protected void loadRecordingsAndPublishPendingReplies() throws Exception {
        recordings = library.load(uploader.pendingNames());
        int published = pendingReplies.publishReadyReplies(recordings,
                (recording, replyToShareId) -> community.share(recording, replyToShareId) != null);
        if (published > 0) {
            posts = loadRankedCommunityPosts();
            main.post(() -> toast("回应已发布到社区"));
        }
    }

    protected void refreshRecordingsFromPull(PullRefreshLayout refresher) {
        recordingsLoadAttempted = true;
        closeOpenSwipes();
        io.execute(() -> {
            uploader.drainPending();
            try {
                loadRecordingsAndPublishPendingReplies();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            main.post(() -> {
                refresher.setRefreshing(false);
                refreshHomePages();
            });
        });
    }

    protected void refreshCommunityFromPull(PullRefreshLayout refresher) {
        communityLoadAttempted = true;
        io.execute(() -> {
            uploader.drainPending();
            try {
                posts = loadRankedCommunityPosts();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            communityLoading = false;
            main.post(() -> {
                refresher.setRefreshing(false);
                refreshHomePages();
            });
        });
    }
    protected void showHome() {
        topLevelUiRendered = true;
        closeOpenSwipes();
        if (homePager != null && root.getChildCount() > 0 && !isDetailActivity()) {
            refreshHomePages();
            return;
        }

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

        recordingsTabTitle = text("我的录音", 20, communityTab ? Theme.FAINT : Theme.INK, Typeface.BOLD);
        recordingsTabTitle.setGravity(Gravity.CENTER);
        recordingsTabTitle.setPadding(dp(8), dp(6), dp(8), dp(6));
        titleRow.addView(recordingsTabTitle, new LinearLayout.LayoutParams(0, -2, 1));

        communityTabTitle = text("VD社区", 20, communityTab ? Theme.INK : Theme.FAINT, Typeface.BOLD);
        communityTabTitle.setGravity(Gravity.CENTER);
        communityTabTitle.setPadding(dp(8), dp(6), dp(8), dp(6));
        titleRow.addView(communityTabTitle, new LinearLayout.LayoutParams(0, -2, 1));

        homeTabUnderline = new View(this);
        homeTabUnderline.setBackground(round(Theme.RED, 1));
        page.addView(homeTabUnderline, new LinearLayout.LayoutParams(0, dp(3)));
        titleRow.post(this::updateHomeTabs);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(10)));
        page.addView(spacer);

        homePager = new LockedViewPager(this);
        homePager.setId(View.generateViewId());
        homePager.setOffscreenPageLimit(2);
        homePagerAdapter = new HomePagerAdapter();
        homePager.setAdapter(homePagerAdapter);
        page.addView(homePager, new LinearLayout.LayoutParams(-1, 0, 1));
        homePager.setCurrentItem(communityTab ? 1 : 0, false);
        homePager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int position) {
                communityTab = position == 1;
                updateHomeTabs();
                if (communityTab && !communityLoadAttempted) {
                    communityLoading = posts.isEmpty();
                    refreshHomePages();
                    refreshDataInBackground();
                } else if (!communityTab && !recordingsLoadAttempted) {
                    loading = recordings.isEmpty();
                    refreshDataInBackground();
                }
            }
        });
        recordingsTabTitle.setOnClickListener(v -> homePager.setCurrentItem(0, true));
        communityTabTitle.setOnClickListener(v -> {
            if (!communityLoadAttempted && posts.isEmpty()) {
                communityLoading = true;
                refreshHomePages();
            }
            homePager.setCurrentItem(1, true);
        });
    }

    protected void refreshHomePages() {
        if (homePagerAdapter != null) homePagerAdapter.notifyDataSetChanged();
        updateHomeTabs();
    }

    protected void updateHomeTabs() {
        if (recordingsTabTitle == null || communityTabTitle == null || homeTabUnderline == null) return;
        recordingsTabTitle.setTextColor(communityTab ? Theme.FAINT : Theme.INK);
        communityTabTitle.setTextColor(communityTab ? Theme.INK : Theme.FAINT);
        updateUnderline(homeTabUnderline, recordingsTabTitle, communityTabTitle, communityTab);
    }

    protected View buildRecordingsTabPage() {
        FrameLayout contentArea = new FrameLayout(this);

        PullRefreshLayout refresher = pullRefreshContainer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(6), dp(14), dp(16));
        scroll.addView(list);
        scroll.setPadding(0, 0, 0, dp(130));
        scroll.setClipToPadding(false);
        refresher.addView(scroll, match());
        refresher.setOnRefreshListener(() -> refreshRecordingsFromPull(refresher));
        contentArea.addView(refresher, match());

        if (loading && recordings.isEmpty() && !communityTab) {
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
            TextView empty = text("正在加载…", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(14), 0, 0);
            loadingLayout.addView(empty);
            list.addView(loadingLayout, new LinearLayout.LayoutParams(-1, dp(180)));
        } else if (recordings.isEmpty()) {
            TextView empty = text("轻点下方按钮开始第一条录音", 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
        } else {
            for (Recording rec : recordings) list.addView(recordingRow(rec));
        }

        addRecordFab(contentArea);
        return contentArea;
    }

    protected View buildCommunityTabPage() {
        PullRefreshLayout refresher = pullRefreshContainer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(6), dp(14), dp(16));
        scroll.addView(list);
        refresher.addView(scroll, match());
        refresher.setOnRefreshListener(() -> refreshCommunityFromPull(refresher));
        renderCommunityList(list);
        return refresher;
    }

    protected PullRefreshLayout pullRefreshContainer() {
        PullRefreshLayout refresher = new PullRefreshLayout(this);
        refresher.setColorSchemeColors(Theme.RED);
        refresher.setProgressBackgroundColorSchemeColor(Theme.CARD);
        return refresher;
    }

    protected void addRecordFab(FrameLayout contentArea) {
        LinearLayout fabCol = new LinearLayout(this);
        fabCol.setOrientation(LinearLayout.VERTICAL);
        fabCol.setGravity(Gravity.CENTER_HORIZONTAL);
        fabCol.setClickable(true);
        FrameLayout.LayoutParams fabColLp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        fabColLp.bottomMargin = dp(22);
        contentArea.addView(fabCol, fabColLp);

        FrameLayout fabRing = new SoftCircleShadowFrameLayout(this);

        FrameLayout fab = new FrameLayout(this);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setColor(Theme.RED);
        fabBg.setCornerRadius(dp(30));
        fab.setBackground(fabBg);
        ImageView micIcon = new ImageView(this);
        AliIconFont.apply(micIcon, AliIconFont.MIC, 0xffffffff);
        micIcon.setScaleType(ImageView.ScaleType.CENTER);
        fab.addView(micIcon, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        fabRing.addView(fab, new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER));
        fabCol.addView(fabRing, new LinearLayout.LayoutParams(dp(82), dp(82)));

        TextView label = text("轻点录音", 12, Theme.SECONDARY, Typeface.NORMAL);
        label.setLetterSpacing(0.08f);
        label.setPadding(0, 0, 0, 0);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-2, -2);
        labelLp.setMargins(0, -dp(3), 0, 0);
        fabCol.addView(label, labelLp);
        fabCol.setOnClickListener(v -> startRecordingFlow());
        fabRing.setOnClickListener(v -> startRecordingFlow());
        label.setOnClickListener(v -> startRecordingFlow());
    }

    protected View communityRow(CommunityStore.Post post) {
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
        TextView title = text(post.title == null || post.title.isEmpty() ? "社区文章" : post.title,
                16, Theme.INK, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(title);
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

    protected final class HomePagerAdapter extends PagerAdapter {
        @Override public int getCount() {
            return 2;
        }

        @Override public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override public Object instantiateItem(ViewGroup container, int position) {
            View page = position == 0 ? buildRecordingsTabPage() : buildCommunityTabPage();
            container.addView(page, new ViewGroup.LayoutParams(-1, -1));
            return page;
        }

        @Override public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override public int getItemPosition(Object object) {
            return POSITION_NONE;
        }
    }

    protected static final class LockedViewPager extends ViewPager {
        public LockedViewPager(Context context) {
            super(context);
        }

        public LockedViewPager(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            return false;
        }
    }

    protected View recordingRow(Recording rec) {
        final int deleteWidth = dp(90);

        FrameLayout container = new FrameLayout(this);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(-1, -2);
        containerLp.setMargins(0, 0, 0, dp(12));
        container.setLayoutParams(containerLp);
        container.setClipChildren(false);
        container.setBackground(null);

        TextView deleteBtn = new TextView(this);
        deleteBtn.setText("删除");
        deleteBtn.setTextSize(17);
        deleteBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        deleteBtn.setTextColor(Color.WHITE);
        deleteBtn.setGravity(Gravity.CENTER);
        deleteBtn.setBackground(round(Theme.RED, 18));
        FrameLayout.LayoutParams deleteLp = new FrameLayout.LayoutParams(dp(80), -1,
                Gravity.END | Gravity.CENTER_VERTICAL);
        deleteLp.setMargins(dp(10), dp(4), dp(4), dp(4));
        deleteBtn.setLayoutParams(deleteLp);
        container.addView(deleteBtn);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        row.setBackground(round(Theme.CARD, 18));
        container.addView(row, new FrameLayout.LayoutParams(-1, -2));

        boolean silent = rec.isEmpty;
        int mutedIconBg = 0xfff4f0eb;
        int mutedIconFg = 0xffd1c8bd;
        int mutedText = 0xff6f6a63;
        int mutedMeta = 0xffb9b2a8;

        LinearLayout waveIcon = new LinearLayout(this);
        waveIcon.setOrientation(LinearLayout.HORIZONTAL);
        waveIcon.setGravity(Gravity.CENTER);
        waveIcon.setBackground(round(silent ? mutedIconBg : 0xfffbeae7, 14));
        waveIcon.setPadding(dp(12), dp(12), dp(12), dp(12));
        addWaveBar(waveIcon, dp(3), dp(11), silent ? mutedIconFg : Theme.RED);
        View gap1 = new View(this);
        gap1.setLayoutParams(new LinearLayout.LayoutParams(dp(4), 1));
        waveIcon.addView(gap1);
        addWaveBar(waveIcon, dp(3), dp(19), silent ? mutedIconFg : Theme.RED);
        View gap2 = new View(this);
        gap2.setLayoutParams(new LinearLayout.LayoutParams(dp(4), 1));
        waveIcon.addView(gap2);
        addWaveBar(waveIcon, dp(3), dp(14), silent ? mutedIconFg : Theme.RED);
        row.addView(waveIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(dp(14), 0, dp(8), 0);
        row.addView(meta, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = text(rec.rowTitle(), 16, silent ? mutedText : Theme.INK, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(title);

        LinearLayout sub = new LinearLayout(this);
        sub.setOrientation(LinearLayout.HORIZONTAL);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        sub.setPadding(0, dp(7), 0, 0);
        meta.addView(sub);
        String dateTime = formatArticleSubtitle(rec);
        String duration = rec.durationLabel();
        String metaText = dateTime.isEmpty()
                ? (duration.isEmpty() ? "录音" : duration)
                : dateTime + (duration.isEmpty() ? "" : "  " + duration);
        sub.addView(text(metaText, 13, silent ? mutedMeta : Theme.FAINT, Typeface.NORMAL));

        int statusColor = silent ? mutedMeta : (rec.hasArticles ? Theme.GREEN : Theme.AMBER);
        View statusDot = new View(this);
        statusDot.setBackground(round(statusColor, 3));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(5), dp(5));
        dotLp.setMargins(dp(9), 0, dp(6), 0);
        sub.addView(statusDot, dotLp);

        TextView chip;
        if (silent) {
            chip = text("无语音", 13, mutedMeta, Typeface.NORMAL);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            sub.addView(chip, chipLp);
        } else {
            chip = text(rec.statusLabel(), 13, statusColor, Typeface.NORMAL);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            sub.addView(chip, chipLp);
        }

        if (rec.hasArticles && !silent) {
            chip.setOnLongClickListener(v -> {
                IosDialog.show(this, "重新生成文章？", "会用相同的写作风格重新挖这篇文章，原文不变。",
                        "重新生成", () -> io.execute(() -> {
                            try {
                                boolean ok = library.restyle(rec, -1);
                                toast(ok ? "已请求重新生成" : "重新生成请求失败");
                                if (ok) refreshAndDrain();
                            } catch (Exception e) {
                                toast("重新生成请求失败：" + e.getMessage());
                            }
                        }));
                return true;
            });
        }

        TextView chevron = text("›", 30, 0xffcfc6b6, Typeface.NORMAL);
        row.addView(chevron);

        final float[] downX = {0};
        final float[] downY = {0};
        final boolean[] isSwiping = {false};

        row.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    isSwiping[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dX = event.getRawX() - downX[0];
                    float dY = event.getRawY() - downY[0];
                    if (!isSwiping[0] && Math.abs(dX) > dp(10) && Math.abs(dX) > Math.abs(dY)) {
                        isSwiping[0] = true;
                    }
                    if (isSwiping[0] && dX < 0) {
                        float tx = Math.max(-deleteWidth - dp(10), dX / 3f);
                        row.setTranslationX(tx);
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSwiping[0]) {
                        float currentTx = row.getTranslationX();
                        float fullOpen = -deleteWidth - dp(10);
                        if (currentTx < fullOpen / 2f) {
                            row.animate().translationX(fullOpen)
                                    .withEndAction(() -> openSwipeRows.add(row)).setDuration(200).start();
                        } else {
                            row.animate().translationX(0)
                                    .withEndAction(() -> openSwipeRows.remove(row)).setDuration(200).start();
                        }
                        isSwiping[0] = false;
                        return true;
                    }
                    return false;
            }
            return false;
        });

        deleteBtn.setOnClickListener(v -> {
            row.animate().translationX(0).withEndAction(() -> openSwipeRows.remove(row)).setDuration(200).start();
            confirmDeleteRecording(rec, null);
        });

        row.setOnClickListener(v -> {
            if (row.getTranslationX() != 0) {
                row.animate().translationX(0).withEndAction(() -> openSwipeRows.remove(row)).setDuration(200).start();
            } else {
                openRecording(rec);
            }
        });

        return container;
    }
    protected void showRecording(boolean first) {
        closeOpenSwipes();
        clearHomePagerRefs();
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        // Status: centered red dot + "正在录音"
        FrameLayout statusWrap = new FrameLayout(this);
        statusWrap.setPadding(0, dp(64) + getStatusBarHeight(), 0, 0);
        page.addView(statusWrap, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(round(Theme.RED, 5));
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(9), dp(9)));
        statusRow.addView(dot);
        TextView statusText = text(" 正在录音", 14, Theme.SECONDARY, Typeface.NORMAL);
        statusText.setLetterSpacing(0.14f);
        statusRow.addView(statusText);
        statusWrap.addView(statusRow, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));

        // Center: timer + waveform
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        page.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));
        long elapsed = recorder.elapsedSeconds();
        int amp = recorder.sampleCurrentAmplitude();
        // Normalize amplitude: MediaRecorder max is 32767
        double level = amp / 32767.0;
        // Timer: ultra-light, large, centered
        TextView timer = text(String.format("%02d:%02d", elapsed / 60, elapsed % 60), 78, Theme.INK, Typeface.NORMAL);
        timer.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        timer.setGravity(Gravity.CENTER);
        center.addView(timer, new LinearLayout.LayoutParams(-2, -2));
        // Waveform: 13 bars
        center.addView(buildWaveform(level), new LinearLayout.LayoutParams(-2, dp(46)));
        if (!capturedPhotos.isEmpty()) center.addView(recordingFilmstrip());

        // Bottom: stop button + camera
        FrameLayout bottom = new FrameLayout(this);
        page.addView(bottom, new LinearLayout.LayoutParams(-1, dp(160)));

        // Stop button column (centered at bottom)
        LinearLayout stopCol = new LinearLayout(this);
        stopCol.setOrientation(LinearLayout.VERTICAL);
        stopCol.setGravity(Gravity.CENTER_HORIZONTAL);
        stopCol.setClickable(true);
        FrameLayout.LayoutParams stopColLp = new FrameLayout.LayoutParams(dp(104), -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        stopColLp.bottomMargin = dp(26);
        bottom.addView(stopCol, stopColLp);

        FrameLayout stopBtn = new SoftCircleShadowFrameLayout(this);
        FrameLayout stopFace = new FrameLayout(this);
        stopFace.setBackground(round(Theme.RED, 30));
        View stopIcon = new View(this);
        stopIcon.setBackground(round(0xffffffff, 6));
        stopFace.addView(stopIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        stopBtn.addView(stopFace, new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER));
        LinearLayout.LayoutParams stopBtnLp = new LinearLayout.LayoutParams(dp(82), dp(82));
        stopBtnLp.gravity = Gravity.CENTER_HORIZONTAL;
        stopCol.addView(stopBtn, stopBtnLp);

        TextView stopLabel = text("点击停止", 12, Theme.SECONDARY, Typeface.NORMAL);
        stopLabel.setGravity(Gravity.CENTER);
        stopLabel.setLetterSpacing(0.08f);
        stopLabel.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams stopLabelLp = new LinearLayout.LayoutParams(-1, -2);
        stopLabelLp.setMargins(0, -dp(3), 0, 0);
        stopCol.addView(stopLabel, stopLabelLp);
        stopBtn.setOnClickListener(v -> stopRecordingFlow());
        stopCol.setOnClickListener(v -> stopRecordingFlow());
        stopLabel.setOnClickListener(v -> stopRecordingFlow());

        // Camera button on the right
        LinearLayout camBox = new LinearLayout(this);
        camBox.setOrientation(LinearLayout.VERTICAL);
        camBox.setGravity(Gravity.CENTER_HORIZONTAL);
        camBox.setClickable(true);
        FrameLayout.LayoutParams camLp = new FrameLayout.LayoutParams(dp(76), -2, Gravity.RIGHT | Gravity.BOTTOM);
        camLp.rightMargin = dp(28);
        camLp.bottomMargin = dp(26);
        bottom.addView(camBox, camLp);
        FrameLayout cameraButton = new FrameLayout(this);
        cameraButton.setBackground(round(Theme.CARD, 11));
        ImageView cameraIcon = new ImageView(this);
        AliIconFont.apply(cameraIcon, AliIconFont.CAMERA, Theme.SECONDARY);
        cameraIcon.setScaleType(ImageView.ScaleType.CENTER);
        cameraButton.addView(cameraIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams cameraBtnLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        cameraBtnLp.gravity = Gravity.CENTER_HORIZONTAL;
        camBox.addView(cameraButton, cameraBtnLp);
        TextView camLabel = text("拍照", 11, Theme.FAINT, Typeface.NORMAL);
        camLabel.setGravity(Gravity.CENTER);
        camLabel.setPadding(0, dp(6), 0, 0);
        camBox.addView(camLabel, new LinearLayout.LayoutParams(-1, -2));
        camBox.setOnClickListener(v -> openCamera());

        if (first) main.postDelayed(timerTick, 500);
    }

    /** Build 13-bar waveform matching iOS design */
    /** Build 13-bar waveform matching iOS design */
    protected View buildWaveform(double level) {
        // level is 0..1 (normalized from MediaRecorder amplitude)
        double[] pattern = {0.30, 0.56, 0.82, 0.48, 0.95, 0.65, 0.38, 0.74, 0.52, 0.86, 0.34, 0.62, 0.44};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < pattern.length; i++) {
            if (i > 0) {
                View gap = new View(this);
                gap.setLayoutParams(new LinearLayout.LayoutParams(dp(3), 1));
                row.addView(gap);
            }
            double frac = pattern[i] * (0.22 + level * 0.95);
            // Clamp frac to 0..1
            frac = Math.max(0.0, Math.min(1.0, frac));
            int height = Math.max(dp(6), (int) (dp(46) * frac));
            int color;
            if (frac > 0.6) color = Theme.RED;
            else if (frac > 0.3) color = 0xffeba89f;
            else color = 0xffe5c8c3;
            View bar = new View(this);
            bar.setBackground(round(color, 2));
            bar.setLayoutParams(new LinearLayout.LayoutParams(dp(3), height));
            row.addView(bar);
        }
        return row;
    }



    protected View recordingFilmstrip() {
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

    protected void startRecordingFlow() {
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

    protected void stopRecordingFlow() {
        AudioRecorder.Take take = recorder.stop(null);
        List<CapturedPhoto> photos = new ArrayList<>(capturedPhotos);
        capturedPhotos.clear();
        recordingStart = null;
        main.removeCallbacks(timerTick);

        // Immediately return to the normal tab container so tab switching keeps using ViewPager.
        closeOpenSwipes();
        clearHomePagerRefs();
        showHome();
        if (take != null) {
            if (RecordingQuality.looksSilent(take.peakAmplitude, take.duration)) {
                confirmSilentRecording(take, photos);
            } else {
                uploadTake(take, photos);
            }
        }
    }

    protected void clearHomePagerRefs() {
        homePager = null;
        homePagerAdapter = null;
        recordingsTabTitle = null;
        communityTabTitle = null;
        homeTabUnderline = null;
    }

    protected void buildHomeShell(AudioRecorder.Take take, List<CapturedPhoto> photos) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        root.addView(page, match());

        // Top bar: logo + settings
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(18), dp(8) + getStatusBarHeight(), dp(12), dp(8));
        page.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout logo = new LinearLayout(this);
        logo.setOrientation(LinearLayout.HORIZONTAL);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        addWaveBar(logo, dp(3), dp(10));
        View gap1 = new View(this); gap1.setLayoutParams(new LinearLayout.LayoutParams(dp(2), 1));
        logo.addView(gap1);
        addWaveBar(logo, dp(3), dp(16));
        View gap2 = new View(this); gap2.setLayoutParams(new LinearLayout.LayoutParams(dp(2), 1));
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

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);
        titleRow.setPadding(dp(18), dp(0), dp(18), dp(0));
        page.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        final TextView mainTitle = text("我的录音", 20, Theme.INK, Typeface.BOLD);
        mainTitle.setGravity(Gravity.CENTER);
        titleRow.addView(mainTitle, new LinearLayout.LayoutParams(0, -2, 1));

        final TextView communityTabView = text("VD社区", 20, communityTab ? Theme.RED : Theme.FAINT, Typeface.BOLD);
        communityTabView.setGravity(Gravity.CENTER);
        communityTabView.setPadding(dp(8), dp(6), dp(8), dp(6));
        titleRow.addView(communityTabView, new LinearLayout.LayoutParams(0, -2, 1));

        final View underline = new View(this);
        underline.setBackground(round(Theme.RED, 1));
        underline.setVisibility(View.INVISIBLE);
        page.addView(underline, new LinearLayout.LayoutParams(0, dp(3)));

        titleRow.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    titleRow.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    updateUnderline(underline, mainTitle, communityTabView, communityTab);
                }
            });

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(10)));
        page.addView(spacer);

        final View[] underlineRef = {underline};
        final TextView[] mainTitleRef = {mainTitle};
        final TextView[] communityTabRef = {communityTabView};

        mainTitle.setOnClickListener(v -> {
            if (communityTab) {
                communityTab = false;
                loading = recordings.isEmpty();
                showHome();
                refreshDataInBackground();
            }
        });
        communityTabView.setOnClickListener(v -> {
            if (!communityTab) {
                communityTab = true;
                loading = posts.isEmpty();
                showHome();
                refreshDataInBackground();
            }
        });

        // Content area with loading placeholder
        FrameLayout contentArea = new FrameLayout(this);
        page.addView(contentArea, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(6), dp(14), dp(16));
        scroll.addView(list);
        scroll.setPadding(0, 0, 0, dp(130));
        scroll.setClipToPadding(false);
        contentArea.addView(scroll, match());

        // Show loading placeholder while we build the list off-thread
        TextView loadingView = text("正在加载…", 16, Theme.SECONDARY, Typeface.NORMAL);
        loadingView.setGravity(Gravity.CENTER);
        list.addView(loadingView, new LinearLayout.LayoutParams(-1, dp(180)));

        // Defer heavy list building to the next frame
        main.post(() -> {
            list.removeAllViews();
            if (communityTab) {
                renderCommunityList(list);
            } else if (recordings.isEmpty()) {
                TextView empty = text("轻点下方按钮开始第一条录音", 16, Theme.SECONDARY, Typeface.NORMAL);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
            } else {
                for (Recording rec : recordings) list.addView(recordingRow(rec));
            }

            // FAB
            LinearLayout fabCol = new LinearLayout(this);
            fabCol.setOrientation(LinearLayout.VERTICAL);
            fabCol.setGravity(Gravity.CENTER_HORIZONTAL);
            fabCol.setClickable(true);
            FrameLayout.LayoutParams fabColLp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            fabColLp.bottomMargin = dp(22);
            contentArea.addView(fabCol, fabColLp);

        FrameLayout fabRing = new SoftCircleShadowFrameLayout(RecordingsActivity.this);

        FrameLayout fab = new FrameLayout(RecordingsActivity.this);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setColor(Theme.RED);
        fabBg.setCornerRadius(dp(30));
        fab.setBackground(fabBg);
        ImageView micIcon = new ImageView(RecordingsActivity.this);
        AliIconFont.apply(micIcon, AliIconFont.MIC, 0xffffffff);
        micIcon.setScaleType(ImageView.ScaleType.CENTER);
        fab.addView(micIcon, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        fabRing.addView(fab, new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER));
        fabRing.setLayoutParams(new LinearLayout.LayoutParams(dp(82), dp(82)));
        fabCol.addView(fabRing);

            TextView label = text("按住说话", 13, Theme.SECONDARY, Typeface.NORMAL);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-2, -2);
            labelLp.setMargins(0, -dp(3), 0, 0);
            fabCol.addView(label, labelLp);
            fabCol.setOnClickListener(v -> startRecordingFlow());
            fabRing.setOnClickListener(v -> startRecordingFlow());
            label.setOnClickListener(v -> startRecordingFlow());
        });

        // Handle the stopped recording — do NOT defer, upload immediately
        if (take != null) {
            if (RecordingQuality.looksSilent(take.peakAmplitude, take.duration)) {
                confirmSilentRecording(take, photos);
            } else {
                uploadTake(take, photos);
            }
        }
    }

    protected void confirmSilentRecording(AudioRecorder.Take take, List<CapturedPhoto> photos) {
        new MessageDialog("没有检测到明显声音", "这段录音峰值很低，模拟器常见原因是宿主机麦克风没有授权给 Android Emulator。继续上传大概率会被标记为\"无语音\"。", "仍然上传", "取消")
                .setOkButton("仍然上传", (dialog, v) -> {
                    uploadTake(take, photos);
                    return false;
                })
                .setCancelButton("取消", (dialog, v) -> {
                    //noinspection ResultOfMethodCallIgnored
                    take.file.delete();
                    toast("已丢弃静音录音");
                    return false;
                })
                .setCancelable(true)
                .show();
    }

    protected void uploadTake(AudioRecorder.Take take, List<CapturedPhoto> photos) {
        io.execute(() -> {
            uploadCapturedPhotos(photos);
            uploader.upload(take.file);
            try {
                loadRecordingsAndPublishPendingReplies();
            } catch (Exception e) {
                toast("刷新失败：" + e.getMessage());
            }
            main.post(this::showHome);
        });
    }

    protected void openRecording(Recording rec) {
        if (!rec.hasArticles) {
            toast(rec.statusLabel());
            return;
        }
        Intent intent = new Intent(this, RecordingDetailActivity.class);
        intent.putExtra(EXTRA_AUDIO_NAME, rec.audioName);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    protected void openCamera() {
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

    protected void pickRecordingPhotos() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "选择录音场景照片"), 22);
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
        long offset = Math.max(0, java.time.Duration.between(recordingStart, java.time.ZonedDateTime.now()).getSeconds());
        String key = RecordingName.photoKey(RecordingName.timestamp(recordingStart), (int) offset);
        capturedPhotos.add(new CapturedPhoto(key, bytes, bitmap));
        toast("已加入照片 " + capturedPhotos.size());
        showRecording(false);
    }

    protected void addRecordingPhoto(android.net.Uri uri) {
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
            long offset = Math.max(0, java.time.Duration.between(recordingStart, java.time.ZonedDateTime.now()).getSeconds());
            String key = RecordingName.photoKey(RecordingName.timestamp(recordingStart), (int) offset);
            capturedPhotos.add(new CapturedPhoto(key, jpeg, bitmap));
        } catch (Exception e) {
            toast("照片读取失败：" + e.getMessage());
        }
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            toast("权限被拒绝");
            return;
        }
        if (requestCode == 10) startRecordingFlow();
        if (requestCode == 11) openCamera();
    }

    protected void handleShareIntent(Intent intent) {
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

    protected void uploadSharedText(String text, String intent, int index) throws Exception {
        String suffix = index == 0 ? "" : "-" + index;
        String name = "VoiceDrop-" + intent + "-" + (System.currentTimeMillis() / 1000) + suffix + ".txt";
        http.putBytes(com.baixingai.voicedrop.net.Api.filesBase() + "/upload/" + name, auth.bearer(), "text/plain; charset=utf-8", text.getBytes("UTF-8"));
    }

    protected void uploadSharedUri(Uri uri, String intent, int index) throws Exception {
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
}
