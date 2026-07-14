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
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import com.baixingai.voicedrop.ui.SimpleToast;
import androidx.core.content.FileProvider;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.baixingai.voicedrop.audio.AudioRecorder;
import com.baixingai.voicedrop.audio.AsrDictationSession;
import com.baixingai.voicedrop.audio.EngineRecorder;
import com.baixingai.voicedrop.audio.RealtimeInterviewer;
import com.baixingai.voicedrop.audio.RecordingBackend;
import com.baixingai.voicedrop.audio.RecordingQuality;
import com.baixingai.voicedrop.audio.Uploader;
import com.baixingai.voicedrop.core.ArticleBody;
import com.baixingai.voicedrop.core.ArticlePhotoInsert;
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
import com.baixingai.voicedrop.data.PrivacyConsent;
import com.baixingai.voicedrop.data.Recording;
import com.baixingai.voicedrop.data.ReferralManager;
import com.baixingai.voicedrop.data.SettingsStore;
import com.baixingai.voicedrop.data.UsageStore;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.net.ArticleEditSession;
import com.baixingai.voicedrop.net.LibraryCommandSession;
import com.baixingai.voicedrop.net.StatusSession;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.HoldToTalkGesture;
import com.baixingai.voicedrop.ui.HoldToTalkTranscript;
import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.PopupMenuPosition;
import com.baixingai.voicedrop.ui.PrivacyConsentDialog;
import com.baixingai.voicedrop.ui.PullRefreshLayout;
import com.baixingai.voicedrop.ui.RoundedImageView;
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
    protected RecordingBackend recorder;
    protected RealtimeInterviewer interviewer;
    protected StatusSession statusSession;
    protected ArticleEditSession editSession;
    protected LibraryCommandSession commandSession;
    protected AsrDictationSession dictationSession;
    protected AsrDictationSession commandDictationSession;
    protected MediaPlayer mediaPlayer;
    protected ArticleDoc currentArticleDoc;
    protected String currentArticleStem;
    protected Recording deferredArticleRenderRecording;
    protected ArticleDoc deferredArticleRenderDoc;
    protected List<ArticleEditSession.EditRequest> editQueue = new ArrayList<>();
    protected List<LibraryCommandSession.CommandRequest> commandQueue = new ArrayList<>();
    protected String editReply;
    protected boolean editReplyOk = true;
    protected String commandReply;
    protected boolean commandReplyOk = true;
    protected final HoldToTalkTranscript commandTranscript = new HoldToTalkTranscript();
    protected boolean commandTalking;
    protected boolean commandCanceled;
    protected View commandFabView;
    protected TextView fabLabel;
    protected TextView fabStatus;
    protected LinearLayout recordingsList;
    protected String emptyListText;
    protected final HashMap<String, LinearLayout> recordingsListsByPage = new HashMap<>();
    protected final HashMap<String, String> emptyListTextByPage = new HashMap<>();
    protected static final String COMMAND_NUMBER_BADGE_TAG = "command-number-badge";
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
    protected boolean homeRefreshDeferredWhileRecording;
    private boolean businessInitialized;
    protected ViewPager homePager;
    protected HomePagerAdapter homePagerAdapter;
    protected TextView recordingsTabTitle;
    protected TextView communityTabTitle;
    protected View homeTabUnderline;
    protected final List<String> homeTags = new ArrayList<>();
    protected String selectedTag;
    protected String defaultRecordTag;
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
        installRoot();
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
        commandSession = new LibraryCommandSession(this, auth, new LibraryCommandSession.Listener() {
            @Override public void onQueueChanged(List<LibraryCommandSession.CommandRequest> queue) {
                main.post(() -> {
                    commandQueue = queue;
                    refreshHomePages();
                });
            }

            @Override public void onReply(String text, boolean ok) {
                main.post(() -> {
                    if (isQuietLibraryCommandMessage(text)) return;
                    commandReply = text;
                    commandReplyOk = ok;
                    refreshHomePages();
                });
            }

            @Override public void onConfirm(String id, String text) {
                main.post(() -> showLibraryCommandConfirm(id, text));
            }

            @Override public void onUpdate(List<String> stems) {
                library.invalidateArticleCaches(stems);
                main.post(RecordingsActivity.this::refreshAndDrain);
            }

            @Override public void onState(String state) {
                // Match iOS: connection lifecycle is quiet. The home feedback area is only
                // for active dictation, queued commands, confirmations, replies, and errors.
            }

            @Override public void onError(String message) {
                main.post(() -> {
                    if (isQuietLibraryCommandMessage(message)) return;
                    commandReply = message;
                    commandReplyOk = false;
                    if (message != null && !message.isEmpty()) toast(message);
                    refreshHomePages();
                });
            }
        });
        onPageCreate(getIntent());
    }

    private void installRoot() {
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
        if (!businessInitialized) return;
        if (!isDetailActivity()) {
            if (ResumeRefreshPolicy.shouldRedrawOnResume(false, topLevelUiRendered)) {
                refreshAndDrain();
            } else {
                refreshDataSilently();
            }
            if (statusSession != null) statusSession.connect();
            if (commandSession != null) {
                commandSession.setRefs(currentCommandRefs());
                commandSession.connect();
            }
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
        if (commandDictationSession != null) commandDictationSession.stop();
        if (interviewer != null) interviewer.stop();
        if (commandSession != null) commandSession.close();
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
            list.addView(new LoadingStateView(this), new LinearLayout.LayoutParams(-1, dp(180)));
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
        if (interviewer != null) {
            interviewer.stop();
            interviewer = null;
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

    protected boolean isQuietLibraryCommandMessage(String message) {
        if (message == null) return false;
        String value = message.trim();
        if (value.isEmpty()) return false;
        return value.contains("已连接图库指令")
                || value.contains("已连接到图库指令")
                || value.contains("正在恢复图库指令")
                || value.contains("正在执行图库指令")
                || value.contains("图库指令连接断开")
                || value.contains("图库指令已完成");
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

    protected String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        List<String> clean = new ArrayList<>();
        for (String tag : tags) {
            if (tag != null && !tag.trim().isEmpty()) clean.add(tag.trim());
        }
        return TextUtils.join(" · ", clean);
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
    protected GradientDrawable roundStroke(int fillColor, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
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
        if (handleDeepLink(intent)) return;
        handleShareIntent(intent);
        communityTab = false;
        showHome();
        refreshAndDrain();
        statusSession.connect();
        root.postDelayed(() -> AppUpdateManager.checkOnStartup(this), 1200);
    }
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!businessInitialized) return;
        if (handleDeepLink(intent)) return;
        handleShareIntent(intent);
    }

    protected boolean handleDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        AppRouter.DeepLink link = AppRouter.parse(intent.getData());
        if (link.kind == AppRouter.Kind.RECORDINGS) {
            communityTab = false;
            showHome();
            refreshAndDrain();
            if (statusSession != null) statusSession.connect();
            return true;
        }
        if (link.kind == AppRouter.Kind.COMMUNITY) {
            communityTab = true;
            showHome();
            refreshDataInBackground();
            if (statusSession != null) statusSession.connect();
            return true;
        }
        if (link.kind == AppRouter.Kind.SETTINGS) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (link.kind == AppRouter.Kind.RECORD) {
            communityTab = false;
            selectedTag = null;
            defaultRecordTag = link.tag;
            showHome();
            if (statusSession != null) statusSession.connect();
            startRecordingFlow();
            return true;
        }
        if (link.kind == AppRouter.Kind.ARTICLE) {
            openArticleDeepLink(link.stem);
            return true;
        }
        if (link.kind == AppRouter.Kind.SHARE_LINK) {
            new ReferralManager(this).noteShareToken(link.id);
            openShareLink(link.id, link.url);
            return true;
        }
        if (link.kind == AppRouter.Kind.WEB) {
            openWebFallback(link.url);
            return true;
        }
        return false;
    }

    protected void openShareLink(String id, String fallbackUrl) {
        if (id == null || id.trim().isEmpty()) {
            openWebFallback(fallbackUrl);
            return;
        }
        io.execute(() -> {
            try {
                LibraryStore.LinkTarget target = library.resolveShareLink(id);
                if (target == null) {
                    main.post(() -> openWebFallback(fallbackUrl));
                    return;
                }
                String scope = library.ownerScope();
                if (scope != null && scope.equals(target.owner) && !target.stem.isEmpty()) {
                    main.post(() -> openArticleDeepLink(target.stem));
                } else if (target.isCommunity()) {
                    main.post(() -> openCommunityShare(id));
                } else if (target.doc != null && !target.doc.articles.isEmpty()) {
                    main.post(() -> openSharedArticle(target, fallbackUrl));
                } else {
                    main.post(() -> openWebFallback(fallbackUrl));
                }
            } catch (Exception e) {
                main.post(() -> openWebFallback(fallbackUrl));
            }
        });
    }

    protected void openCommunityShare(String shareId) {
        Intent intent = new Intent(this, CommunityDetailActivity.class);
        intent.putExtra(EXTRA_SHARE_ID, shareId);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    protected void openSharedArticle(LibraryStore.LinkTarget target, String fallbackUrl) {
        if (target == null || target.rawJson.isEmpty()) {
            openWebFallback(fallbackUrl);
            return;
        }
        Intent intent = new Intent(this, SharedArticleActivity.class);
        intent.putExtra(SharedArticleActivity.EXTRA_SHARED_JSON, target.rawJson);
        int section = 0;
        try {
            Uri uri = Uri.parse(fallbackUrl);
            String s = uri.getQueryParameter("s");
            if (s != null) section = Math.max(0, Integer.parseInt(s));
        } catch (Exception ignored) {
        }
        intent.putExtra(SharedArticleActivity.EXTRA_ARTICLE_INDEX, section);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    protected void openWebFallback(String fallbackUrl) {
        if (fallbackUrl == null || fallbackUrl.trim().isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)));
        } catch (Exception ignored) {
        }
    }

    protected void openArticleDeepLink(String stem) {
        if (stem == null || stem.trim().isEmpty()) return;
        String decoded = Uri.decode(stem.trim());
        String audioName = decoded.endsWith(".m4a") ? decoded : decoded + ".m4a";
        Intent intent = new Intent(this, RecordingDetailActivity.class);
        intent.putExtra(EXTRA_AUDIO_NAME, audioName);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
    protected void refreshAndDrain() {
        loading = true;
        showHome();
        recordingsLoadAttempted = true;
        io.execute(() -> {
            uploader.drainPending();
            boolean tagsChanged = false;
            try {
                tagsChanged = loadRecordingsAndPublishPendingReplies();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            loading = false;
            final boolean rebuildHome = tagsChanged;
            main.post(() -> refreshHomeAfterRecordingLoad(rebuildHome));
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
            boolean tagsChanged = false;
            try {
                if (loadCommunity) posts = loadRankedCommunityPosts();
                else tagsChanged = loadRecordingsAndPublishPendingReplies();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            if (loadCommunity) communityLoading = false;
            final boolean rebuildHome = tagsChanged;
            main.post(() -> {
                if (loadCommunity) refreshHomePages();
                else refreshHomeAfterRecordingLoad(rebuildHome);
            });
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

    protected boolean loadRecordingsAndPublishPendingReplies() throws Exception {
        recordings = library.load(uploader.pendingNames(), uploader.pendingTagsByName());
        boolean tagsChanged = refreshHomeTagsFromRecordings();
        int published = pendingReplies.publishReadyReplies(recordings,
                (recording, replyToShareId) -> community.share(recording, replyToShareId) != null);
        if (published > 0) {
            posts = loadRankedCommunityPosts();
            main.post(() -> toast("回应已发布到社区"));
        }
        return tagsChanged;
    }

    protected boolean refreshHomeTagsFromRecordings() {
        List<String> previous = new ArrayList<>(homeTags);
        Set<String> seen = new HashSet<>();
        List<String> tags = new ArrayList<>();
        for (Recording rec : recordings) {
            if (rec.tags == null) continue;
            for (String tag : rec.tags) {
                if (tag != null && !tag.trim().isEmpty() && seen.add(tag.trim())) tags.add(tag.trim());
            }
        }
        homeTags.clear();
        homeTags.addAll(tags);
        if (selectedTag != null && !homeTags.contains(selectedTag)) selectedTag = null;
        return !previous.equals(homeTags);
    }

    protected void refreshHomeAfterRecordingLoad(boolean tagsChanged) {
        if (tagsChanged && homePager != null && root.getChildCount() > 0 && !isDetailActivity()) {
            clearHomePagerRefs();
            root.removeAllViews();
            showHome();
        } else {
            refreshHomePages();
        }
    }

    protected void refreshRecordingsFromPull(PullRefreshLayout refresher) {
        recordingsLoadAttempted = true;
        closeOpenSwipes();
        io.execute(() -> {
            uploader.drainPending();
            boolean tagsChanged = false;
            try {
                tagsChanged = loadRecordingsAndPublishPendingReplies();
            } catch (Exception e) {
                toast("加载失败：" + e.getMessage());
            }
            final boolean rebuildHome = tagsChanged;
            main.post(() -> {
                refresher.setRefreshing(false);
                refreshHomeAfterRecordingLoad(rebuildHome);
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
        if (recorder != null && recorder.isRecording()) {
            homeRefreshDeferredWhileRecording = true;
            return;
        }
        homeRefreshDeferredWhileRecording = false;
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

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(false);
        page.addView(tabScroll, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);
        titleRow.setPadding(dp(18), 0, dp(18), 0);
        tabScroll.addView(titleRow, new HorizontalScrollView.LayoutParams(-2, -2));

        recordingsTabTitle = text("我的录音", 20, !communityTab && selectedTag == null ? Theme.INK : Theme.FAINT, Typeface.BOLD);
        recordingsTabTitle.setGravity(Gravity.CENTER);
        recordingsTabTitle.setPadding(dp(8), dp(6), dp(14), dp(6));
        titleRow.addView(recordingsTabTitle, new LinearLayout.LayoutParams(-2, -2));

        communityTabTitle = text("VD社区", 20, communityTab ? Theme.INK : Theme.FAINT, Typeface.BOLD);
        communityTabTitle.setGravity(Gravity.CENTER);
        communityTabTitle.setPadding(dp(14), dp(6), dp(14), dp(6));
        titleRow.addView(communityTabTitle, new LinearLayout.LayoutParams(-2, -2));

        for (String tag : homeTags) {
            TextView tagTitle = text(tag, 20, tag.equals(selectedTag) ? Theme.INK : Theme.FAINT, Typeface.BOLD);
            tagTitle.setGravity(Gravity.CENTER);
            tagTitle.setSingleLine(true);
            tagTitle.setEllipsize(TextUtils.TruncateAt.END);
            tagTitle.setPadding(dp(14), dp(6), dp(14), dp(6));
            tagTitle.setOnClickListener(v -> {
                communityTab = false;
                selectedTag = tag;
                if (homePager != null) homePager.setCurrentItem(homeTags.indexOf(tag) + 2, true);
                updateHomeTabs();
            });
            titleRow.addView(tagTitle, new LinearLayout.LayoutParams(-2, -2));
        }

        homeTabUnderline = new View(this);
        homeTabUnderline.setBackground(round(Theme.RED, 1));
        page.addView(homeTabUnderline, new LinearLayout.LayoutParams(0, dp(3)));
        titleRow.post(this::updateHomeTabs);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(10)));
        page.addView(spacer);

        homePager = new LockedViewPager(this);
        homePager.setId(View.generateViewId());
        homePager.setOffscreenPageLimit(Math.max(2, homeTags.size() + 2));
        homePagerAdapter = new HomePagerAdapter();
        homePager.setAdapter(homePagerAdapter);
        page.addView(homePager, new LinearLayout.LayoutParams(-1, 0, 1));
        homePager.setCurrentItem(currentHomePageIndex(), false);
        homePager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int position) {
                communityTab = position == 1;
                selectedTag = position >= 2 && position - 2 < homeTags.size() ? homeTags.get(position - 2) : null;
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
        recordingsTabTitle.setOnClickListener(v -> {
            selectedTag = null;
            homePager.setCurrentItem(0, true);
        });
        communityTabTitle.setOnClickListener(v -> {
            selectedTag = null;
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
        recordingsTabTitle.setTextColor(!communityTab && selectedTag == null ? Theme.INK : Theme.FAINT);
        communityTabTitle.setTextColor(communityTab ? Theme.INK : Theme.FAINT);
        if (selectedTag == null) {
            homeTabUnderline.setVisibility(View.VISIBLE);
            updateUnderline(homeTabUnderline, recordingsTabTitle, communityTabTitle, communityTab);
        } else {
            homeTabUnderline.setVisibility(View.INVISIBLE);
        }
    }

    protected int currentHomePageIndex() {
        if (communityTab) return 1;
        if (selectedTag != null) {
            int index = homeTags.indexOf(selectedTag);
            if (index >= 0) return index + 2;
        }
        return 0;
    }

    protected View buildRecordingsTabPage() {
        return buildRecordingsListPage(recordingsPageKey(null), recordings, "轻点下方按钮开始第一条录音");
    }

    protected View buildTagTabPage(String tag) {
        return buildRecordingsListPage(recordingsPageKey(tag), recordingsForTag(tag), "「" + tag + "」标签下还没有文章");
    }

    protected String recordingsPageKey(String tag) {
        return tag == null ? "__all_recordings__" : "tag:" + tag;
    }

    protected List<Recording> recordingsForTag(String tag) {
        List<Recording> out = new ArrayList<>();
        for (Recording rec : recordings) {
            if (rec.tags != null && rec.tags.contains(tag)) out.add(rec);
        }
        return out;
    }

    protected View buildRecordingsListPage(String pageKey, List<Recording> listForPage, String emptyText) {
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

        recordingsList = list;
        recordingsListsByPage.put(pageKey, list);
        emptyListText = emptyText;
        emptyListTextByPage.put(pageKey, emptyText);
        populateRecordingList(list, emptyText, listForPage);

        addRecordFab(contentArea);
        return contentArea;
    }

    protected void populateRecordingList(List<Recording> listForPage) {
        populateRecordingList(recordingsList, emptyListText, listForPage);
    }

    protected void populateRecordingList(LinearLayout list, String emptyText, List<Recording> listForPage) {
        if (list == null) return;
        list.removeAllViews();
        if (loading && listForPage.isEmpty() && !communityTab) {
            list.addView(new LoadingStateView(this), new LinearLayout.LayoutParams(-1, dp(180)));
        } else if (listForPage.isEmpty()) {
            TextView empty = text(emptyText, 16, Theme.SECONDARY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
        } else {
            for (Recording rec : listForPage) list.addView(recordingRow(rec));
        }
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
        FrameLayout.LayoutParams fabColLp = new FrameLayout.LayoutParams(-1, -2,
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
        addLibraryCommandStatus(fabCol);
        TextView localFabStatus = fabStatus;
        fabCol.addView(fabRing, new LinearLayout.LayoutParams(dp(82), dp(82)));

        fabLabel = text(recordFabLabel(), 12, commandTalking ? Theme.RED : Theme.SECONDARY, Typeface.NORMAL);
        TextView localFabLabel = fabLabel;
        fabLabel.setLetterSpacing(0.08f);
        fabLabel.setPadding(0, 0, 0, 0);
        fabLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-2, -2);
        labelLp.setMargins(0, -dp(3), 0, 0);
        fabCol.addView(fabLabel, labelLp);
        View.OnTouchListener commandTouch = createLibraryCommandFabTouchListener(localFabLabel, localFabStatus);
        fab.setOnTouchListener(commandTouch);
    }

    protected String recordFabLabel() {
        if (!commandTalking) return "轻点录音 · 长按说话";
        return commandCanceled ? "上滑取消 · 松开放弃" : "松开发送 · 上滑取消";
    }

    protected View.OnTouchListener createLibraryCommandFabTouchListener(TextView localFabLabel, TextView localFabStatus) {
        final Handler pressHandler = new Handler(Looper.getMainLooper());
        final boolean[] longPressed = {false};
        final boolean[] movedToCancel = {false};
        final float[] startRawY = {0};
        final Runnable[] startRunnable = new Runnable[1];
        return (v, event) -> {
            fabLabel = localFabLabel;
            fabStatus = localFabStatus;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    commandFabView = v;
                    longPressed[0] = false;
                    movedToCancel[0] = false;
                    startRawY[0] = event.getRawY();
                    startRunnable[0] = () -> {
                        longPressed[0] = true;
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        startLibraryCommandTalk();
                    };
                    pressHandler.postDelayed(startRunnable[0], commandLongPressConfirmDelayMs());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (longPressed[0]) {
                        boolean shouldCancel = HoldToTalkGesture.shouldCancel(startRawY[0], event.getRawY(), dp(60));
                        if (shouldCancel != movedToCancel[0]) {
                            movedToCancel[0] = shouldCancel;
                            commandCanceled = shouldCancel;
                            updateFabLabel();
                            updateFabStatus();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!longPressed[0]) {
                        if (startRunnable[0] != null) pressHandler.removeCallbacks(startRunnable[0]);
                        startRecordingFlow();
                    } else {
                        finishLibraryCommandTalk(HoldToTalkGesture.shouldAbortOnEnd(false, movedToCancel[0]));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (startRunnable[0] != null) pressHandler.removeCallbacks(startRunnable[0]);
                    finishLibraryCommandTalk(true);
                    return true;
                default:
                    return false;
            }
        };
    }

    protected int commandLongPressConfirmDelayMs() {
        return 300;
    }

    protected void startLibraryCommandTalk() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 12);
            return;
        }
        if (commandTalking) return;
        commandTranscript.clear();
        commandReply = "正在连接…";
        commandReplyOk = true;
        commandCanceled = false;
        commandTalking = true;
        if (commandSession != null) {
            commandSession.setRefs(currentCommandRefs());
            commandSession.connect();
        }
        commandDictationSession = new AsrDictationSession(auth, new AsrDictationSession.Listener() {
            @Override public void onText(String text, boolean isFinal) {
                main.post(() -> {
                    commandTranscript.accept(text, isFinal);
                    if (!commandTalking) return;
                    commandReply = commandTranscript.bubbleText();
                    updateFabLabel();
                    updateFabStatus();
                });
            }

            @Override public void onState(String state) {
                main.post(() -> {
                    if (!commandTalking) return;
                    if (commandTranscript.bestText().isEmpty()) commandReply = state;
                    updateFabLabel();
                    updateFabStatus();
                });
            }

            @Override public void onError(String message) {
                main.post(() -> {
                    if (!commandTalking) return;
                    commandReply = message;
                    commandReplyOk = false;
                    toast(message == null || message.isEmpty() ? "听写失败" : message);
                    updateFabStatus();
                });
            }
        });
        commandDictationSession.start();
        // Update FAB label/status in-place without rebuilding the view tree,
        // so the ongoing long-press touch stream isn't interrupted.
        updateFabLabel();
        updateFabStatus();
        refreshRecordingList();
    }

    protected void finishLibraryCommandTalk(boolean cancel) {
        if (!commandTalking) return;
        commandCanceled = cancel;
        // Don't refreshHomePages() here -- the view tree rebuild destroys the touch
        // target. Just update the label in-place. Full rebuild happens after release.
        commandTalking = false;
        commandReply = null;
        commandReplyOk = true;
        updateFabLabel();
        updateFabStatus();
        final AsrDictationSession session = commandDictationSession;
        if (session == null) {
            completeLibraryCommandTalk(cancel);
            return;
        }
        if (cancel) {
            session.stop();
            completeLibraryCommandTalk(true);
            return;
        }
        session.finish(() -> main.post(() -> completeLibraryCommandTalk(false)));
    }

    protected void completeLibraryCommandTalk(boolean cancel) {
        commandDictationSession = null;
        String text = commandTranscript.bestText();
        if (cancel) {
            commandReply = null;
            commandReplyOk = true;
        } else if (text.isEmpty()) {
            commandReply = null;
            commandReplyOk = true;
        } else {
            commandReply = text;
            commandReplyOk = true;
            if (commandSession != null) commandSession.enqueue(text, currentCommandRefs());
        }
        commandTranscript.clear();
        commandCanceled = false;
        refreshHomePages();
    }

    protected List<LibraryCommandSession.CommandRef> currentCommandRefs() {
        List<LibraryCommandSession.CommandRef> refs = new ArrayList<>();
        for (Recording rec : currentCommandTargets()) {
            if (rec.uploading) continue;
            refs.add(new LibraryCommandSession.CommandRef(refs.size() + 1, rec.stem(), rec.rowTitle()));
        }
        return refs;
    }

    protected List<Recording> currentCommandTargets() {
        if (selectedTag == null) return recordings;
        List<Recording> out = new ArrayList<>();
        for (Recording rec : recordings) {
            if (rec.tags != null && rec.tags.contains(selectedTag)) out.add(rec);
        }
        return out;
    }

    protected int commandRefNumberFor(Recording rec) {
        if (rec.uploading) return 0;
        int n = 0;
        for (Recording item : currentCommandTargets()) {
            if (item.uploading) continue;
            n++;
            if (item == rec || item.audioName.equals(rec.audioName)) return n;
        }
        return 0;
    }

    protected void addLibraryCommandStatus(LinearLayout list) {
        fabStatus = text("", 13, Theme.SECONDARY, Typeface.BOLD);
        fabStatus.setGravity(Gravity.CENTER);
        fabStatus.setSingleLine(false);
        fabStatus.setPadding(dp(20), dp(9), dp(20), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), 0, dp(16), dp(12));
        list.addView(fabStatus, lp);
        updateFabStatus();
    }

    protected void updateFabLabel() {
        if (fabLabel == null) return;
        fabLabel.setText(recordFabLabel());
        fabLabel.setTextColor(commandTalking ? Theme.RED : Theme.SECONDARY);
    }

    protected void updateFabStatus() {
        if (fabStatus == null) return;
        String text;
        int color = commandReplyOk ? Theme.SECONDARY : Theme.RED;
        if (commandTalking) {
            String transcriptText = commandTranscript.bestText();
            if (commandCanceled) {
                text = "松手取消图库指令";
                color = Theme.RED;
            } else {
                text = transcriptText.isEmpty() ? commandReply : transcriptText;
                color = Theme.INK;
            }
        } else if (!commandQueue.isEmpty()) {
            text = commandQueue.get(commandQueue.size() - 1).text;
            color = Theme.RED;
        } else if (commandReply != null && !commandReply.isEmpty()) {
            text = commandReply;
        } else {
            fabStatus.setVisibility(View.GONE);
            return;
        }
        fabStatus.setText(text);
        fabStatus.setTextColor(color);
        fabStatus.setBackground(round(commandReplyOk ? Theme.ACCENT_SOFT : 0xffffebe8, 14));
        fabStatus.setVisibility(View.VISIBLE);
    }

    /** Rebuild just the recording list rows without touching the FAB column. */
    protected void refreshRecordingList() {
        String pageKey = recordingsPageKey(selectedTag);
        LinearLayout list = recordingsListsByPage.get(pageKey);
        if (list == null) return;
        List<Recording> listForPage = (selectedTag == null) ? recordings : recordingsForTag(selectedTag);
        String emptyText = emptyListTextByPage.get(pageKey);
        if (emptyText == null) emptyText = selectedTag == null
                ? "轻点下方按钮开始第一条录音"
                : "「" + selectedTag + "」标签下还没有文章";
        populateRecordingList(list, emptyText, listForPage);
    }

    protected void showLibraryCommandConfirm(String id, String text) {
        new MessageDialog("确认图库指令", text == null || text.isEmpty() ? "确认执行这条图库指令？" : text, "确认", "取消")
                .setOkButton("确认", (dialog, v) -> {
                    if (commandSession != null) commandSession.confirm(id);
                    return false;
                })
                .setCancelButton("取消", (dialog, v) -> {
                    if (commandSession != null) commandSession.cancel(id);
                    return false;
                })
                .setCancelable(true)
                .show();
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
            return 2 + homeTags.size();
        }

        @Override public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override public Object instantiateItem(ViewGroup container, int position) {
            View page;
            if (position == 0) {
                page = buildRecordingsTabPage();
            } else if (position == 1) {
                page = buildCommunityTabPage();
            } else {
                String tag = position - 2 < homeTags.size() ? homeTags.get(position - 2) : "";
                page = buildTagTabPage(tag);
            }
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

        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.setClipChildren(false);
        iconWrap.setClipToPadding(false);
        iconWrap.addView(waveIcon, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        int commandNumber = commandRefNumberFor(rec);
        if (commandNumber > 0) {
            View badge = commandNumberBadge(commandNumber);
            badge.setTag(COMMAND_NUMBER_BADGE_TAG);
            badge.setVisibility(commandTalking ? View.VISIBLE : View.GONE);
            iconWrap.addView(badge, new FrameLayout.LayoutParams(dp(24), dp(24),
                    Gravity.START | Gravity.TOP));
        }
        row.addView(iconWrap, new LinearLayout.LayoutParams(dp(44), dp(44)));
        maybeLoadRowCover(rec, iconWrap, waveIcon);

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
        List<String> metaParts = new ArrayList<>();
        if (!dateTime.isEmpty()) metaParts.add(dateTime);
        if (!duration.isEmpty()) metaParts.add(duration);
        if (!formatTags(rec.tags).isEmpty()) metaParts.add(formatTags(rec.tags));
        String metaText = metaParts.isEmpty() ? "录音" : TextUtils.join("  ", metaParts);
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
                                boolean ok = library.remine(rec);
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
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (isSwiping[0] && dX < 0) {
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        float tx = Math.max(-deleteWidth - dp(10), dX / 3f);
                        row.setTranslationX(tx);
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSwiping[0]) {
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
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
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
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

    protected TextView commandNumberBadge(int n) {
        TextView badge = text(String.valueOf(n), 13, Color.BLACK, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundStroke(Color.WHITE, 12, 0xffd8d8d8, 1));
        return badge;
    }

    protected void maybeLoadRowCover(Recording rec, FrameLayout iconWrap, View fallbackIcon) {
        if (rec == null || !rec.hasArticles || iconWrap == null || fallbackIcon == null) return;
        io.execute(() -> {
            try {
                ArticleDoc doc = library.fetchDoc(rec);
                if (doc == null || doc.articles.isEmpty()) return;
                String key = null;
                for (MinedArticle article : doc.articles) {
                    key = ArticleBody.firstPhotoKey(article.body, doc.photos);
                    if (key != null) break;
                }
                if (key == null) return;
                String scope = library.ownerScope();
                if (scope == null) return;
                Bitmap bitmap = library.photoImage(scope + key, false);
                if (bitmap == null) return;
                main.post(() -> {
                    int index = iconWrap.indexOfChild(fallbackIcon);
                    if (index < 0) return;
                    iconWrap.removeView(fallbackIcon);
                    RoundedImageView cover = new RoundedImageView(this);
                    cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    cover.setImageBitmap(bitmap);
                    iconWrap.addView(cover, index, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
                });
            } catch (Exception ignored) {
            }
        });
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
        statusWrap.setPadding(dp(20), dp(64) + getStatusBarHeight(), dp(20), 0);
        page.addView(statusWrap, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(round(Theme.RED, 5));
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(9), dp(9)));
        statusRow.addView(dot);
        boolean aiActive = interviewer != null && interviewer.interviewActive();
        TextView statusText = text(aiActive ? " AI 采访中" : " 正在录音", 14, Theme.SECONDARY, Typeface.NORMAL);
        statusText.setLetterSpacing(0.14f);
        statusRow.addView(statusText);
        statusWrap.addView(statusRow, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
        if (aiActive && interviewer != null) {
            TextView aiStatus = text(interviewer.stateText(), 11, Theme.FAINT, Typeface.NORMAL);
            aiStatus.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams aiLp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL);
            aiLp.topMargin = dp(24);
            statusWrap.addView(aiStatus, aiLp);
        }

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

        if (!prefs.classicRecorder()) {
            LinearLayout aiBox = new LinearLayout(this);
            aiBox.setOrientation(LinearLayout.VERTICAL);
            aiBox.setGravity(Gravity.CENTER_HORIZONTAL);
            aiBox.setClickable(true);
            FrameLayout.LayoutParams aiLp = new FrameLayout.LayoutParams(dp(76), -2, Gravity.LEFT | Gravity.BOTTOM);
            aiLp.leftMargin = dp(28);
            aiLp.bottomMargin = dp(26);
            bottom.addView(aiBox, aiLp);
            FrameLayout aiButton = new FrameLayout(this);
            aiButton.setBackground(round(aiActive ? Theme.AMBER_BG : Theme.CARD, 11));
            ImageView aiIcon = new ImageView(this);
            aiIcon.setImageResource(R.drawable.ic_waveform_mic);
            aiIcon.setColorFilter(aiActive ? Theme.RED : Theme.SECONDARY);
            aiIcon.setScaleType(ImageView.ScaleType.CENTER);
            aiButton.addView(aiIcon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            LinearLayout.LayoutParams aiBtnLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            aiBtnLp.gravity = Gravity.CENTER_HORIZONTAL;
            aiBox.addView(aiButton, aiBtnLp);
            TextView aiLabel = text("采访", 11, aiActive ? Theme.RED : Theme.FAINT, Typeface.NORMAL);
            aiLabel.setGravity(Gravity.CENTER);
            aiLabel.setPadding(0, dp(6), 0, 0);
            aiBox.addView(aiLabel, new LinearLayout.LayoutParams(-1, -2));
            aiBox.setOnClickListener(v -> toggleInterview());
        }

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
            if ((defaultRecordTag == null || defaultRecordTag.isEmpty()) && selectedTag != null) defaultRecordTag = selectedTag;
            recorder = createRecorderBackend();
            EngineRecorder engineRecorder = null;
            if (recorder instanceof EngineRecorder) engineRecorder = (EngineRecorder) recorder;
            if (engineRecorder != null) {
                interviewer = new RealtimeInterviewer(this, auth);
                interviewer.setOnStateChanged(() -> {
                    if (recorder != null && recorder.isRecording()) showRecording(false);
                });
                engineRecorder.setPcmListener((pcm16le, sampleRate) -> {
                    if (interviewer != null) interviewer.onPcm16(pcm16le, sampleRate);
                });
            } else {
                interviewer = null;
            }
            recorder.start();
            recordingStart = recorder.startDate();
            capturedPhotos.clear();
            showRecording(true);
        } catch (Exception e) {
            toast("无法开始录音：" + e.getMessage());
        }
    }

    protected RecordingBackend createRecorderBackend() {
        if (prefs.classicRecorder()) return new AudioRecorder(this);
        return new EngineRecorder(this);
    }

    protected void stopRecordingFlow() {
        if (interviewer != null) {
            interviewer.stop();
            interviewer = null;
        }
        boolean refreshDeferred = homeRefreshDeferredWhileRecording;
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
        if (refreshDeferred) refreshHomePages();
    }

    protected void toggleInterview() {
        if (recorder == null || !recorder.isRecording() || prefs.classicRecorder()) return;
        if (interviewer == null) {
            interviewer = new RealtimeInterviewer(this, auth);
            interviewer.setOnStateChanged(() -> {
                if (recorder != null && recorder.isRecording()) showRecording(false);
            });
        }
        interviewer.toggle();
        showRecording(false);
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
        list.addView(new LoadingStateView(this), new LinearLayout.LayoutParams(-1, dp(180)));

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

            addRecordFab(contentArea);
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
            if (defaultRecordTag != null && !defaultRecordTag.isEmpty()) {
                Uploader.writeTagsSidecar(take.file, java.util.Collections.singletonList(defaultRecordTag));
                defaultRecordTag = null;
            }
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
        byte[] bytes = ArticlePhotoInsert.squareJpeg(bitmap, 1080, 86);
        if (bytes == null) return;
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
            Bitmap bitmap = ArticlePhotoInsert.decodeSampledBitmap(bytes, 1200);
            if (bitmap == null) return;
            byte[] jpeg = ArticlePhotoInsert.squareJpeg(bitmap, 1080, 86);
            if (jpeg == null) return;
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
        if (requestCode == 12) startLibraryCommandTalk();
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
