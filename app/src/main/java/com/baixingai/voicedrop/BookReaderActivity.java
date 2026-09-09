package com.baixingai.voicedrop;

import android.app.Activity;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.core.BookShareTarget;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.BookShelfCache;
import com.baixingai.voicedrop.data.WechatMiniProgramShare;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.I18n;
import com.baixingai.voicedrop.ui.LoadingStateView;
import com.baixingai.voicedrop.ui.PageTitleBar;
import com.baixingai.voicedrop.ui.PopupMenuPosition;
import com.baixingai.voicedrop.ui.RemixIconGlyph;
import com.baixingai.voicedrop.ui.RemixIconView;
import com.baixingai.voicedrop.ui.ShareBottomSheet;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** In-app web reader for a published VoiceDrop book. */
public final class BookReaderActivity extends VoiceDropActivity {
    private static final int REQUEST_WRITE_DOWNLOADS = 301;
    private static final String MATCH_NATIVE_BACKGROUND_SCRIPT =
            "(function(){var id='voicedrop-native-background';"
                    + "var style=document.getElementById(id);"
                    + "if(!style){style=document.createElement('style');style.id=id;"
                    + "style.textContent='html,body{background:#FAF6EF!important}';"
                    + "document.head.appendChild(style);}})();";
    private WebView web;
    private LoadingStateView loadingState;
    private BookReviseBottomSheet reviseSheet;
    private final ExecutorService shareIo = Executors.newSingleThreadExecutor();
    private final ExecutorService bookIo = Executors.newSingleThreadExecutor();
    private String currentPageUrl;
    private String currentPageTitle;
    private boolean isMine;
    private boolean isHidden;
    private boolean bookPdfDownloading;
    private String pendingPdfDownloadSlug;

    /** Opens with the same leftward page transition used by the rest of the app. */
    public static void open(Activity source, BookShelfIndex.Book book) {
        Intent intent = new Intent(source, BookReaderActivity.class);
        intent.putExtra("slug", book.slug);
        intent.putExtra("displayTitle", book.main);
        intent.putExtra("shareTitle", book.title);
        intent.putExtra("author", book.author);
        intent.putExtra("cover", book.cover);
        intent.putExtra("coverAt", book.coverAt);
        intent.putExtra("coverUrl", book.coverUrl(Api.publicWebBase()));
        source.startActivity(intent);
        source.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Theme.BG);
        PageTitleBar titleBar = new PageTitleBar(this, getIntent().getStringExtra("displayTitle"),
                this::finishWithPageTransition);
        FrameLayout moreAction = titleBar.addIconAction(
                AliIconFont.MORE, Theme.SECONDARY, I18n.text(this, "更多"), () -> {});
        // The shared title bar has a 16dp end inset; shift its 38dp visible card 5dp
        // within the 48dp touch target so its visual edge matches other detail toolbars.
        FrameLayout.LayoutParams moreParams = (FrameLayout.LayoutParams) moreAction.getLayoutParams();
        moreParams.rightMargin = -dp(5);
        moreAction.setLayoutParams(moreParams);
        moreAction.setOnClickListener(this::showBookMenu);
        page.addView(titleBar, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(Theme.BG);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        // WebView does not save attachment responses itself. The in-page PDF link is
        // therefore routed to the native PDF downloader.
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url != null && url.startsWith("https://jianshuo.dev/agent/books/pdf/")) {
                downloadBookPdf();
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String destination = request == null || request.getUrl() == null ? "" : request.getUrl().toString();
                if (destination.startsWith("https://jianshuo.dev/agent/books/pdf/")) {
                    downloadBookPdf();
                    return true;
                }
                return false;
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                showLoading();
            }

            @Override public void onPageFinished(WebView view, String url) {
                updateShareLocation(view, url);
                view.evaluateJavascript(MATCH_NATIVE_BACKGROUND_SCRIPT, null);
                hideLoading();
            }

            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                updateShareLocation(view, url);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    hideLoading();
                    SimpleToast.show(BookReaderActivity.this, I18n.text(BookReaderActivity.this, "书籍加载失败，请检查网络后重试"));
                }
            }
        });
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));
        loadingState = new LoadingStateView(this, I18n.text(this, "正在加载书籍…"));
        loadingState.setBackgroundColor(Theme.BG);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(-1, dp(180), Gravity.TOP);
        loadingParams.topMargin = dp(20);
        content.addView(loadingState, loadingParams);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);

        String slug = getIntent().getStringExtra("slug");
        if (slug != null && slug.matches("[A-Za-z0-9_-]+")) {
            web.loadUrl(Api.publicWebBase() + "/books/" + slug + "/");
            loadOwnership();
        }
    }

    private void showLoading() {
        if (loadingState == null) return;
        loadingState.setVisibility(View.VISIBLE);
        loadingState.bringToFront();
    }

    private void hideLoading() {
        if (loadingState != null) loadingState.setVisibility(View.GONE);
    }

    private void showBookMenu(View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(0, dp(3), 0, dp(3));
        menu.setBackground(roundedMenuBackground());
        menu.setElevation(dp(8));
        final PopupWindow[] popupRef = {null};

        if (isMine) {
            LinearLayout hideRow = bookMenuRow(isHidden ? "取消隐藏" : "隐藏本书",
                    RemixIconGlyph.LOCK, Theme.ACCENT);
            hideRow.setOnClickListener(ignored -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                setHidden(!isHidden);
            });
            menu.addView(hideRow);
            addMenuDivider(menu);
            LinearLayout reviseRow = bookMenuRow("修改这本书", RemixIconGlyph.EDIT, Theme.ACCENT);
            reviseRow.setOnClickListener(ignored -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                openBookRevision();
            });
            menu.addView(reviseRow);
            addMenuDivider(menu);
        }

        LinearLayout shareRow = bookMenuRow("分享", RemixIconGlyph.SHARE_FORWARD, Theme.SECONDARY);
        shareRow.setOnClickListener(ignored -> {
            if (popupRef[0] != null) popupRef[0].dismiss();
            showBookShareSheet();
        });
        menu.addView(shareRow);

        int popupWidth = dp(220);
        PopupWindow popup = new PopupWindow(menu, popupWidth, -2, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));
        popup.showAsDropDown(anchor,
                PopupMenuPosition.rightAlignedXOffset(anchor.getWidth(), popupWidth) - dp(5),
                dp(10));
        popupRef[0] = popup;
    }

    /** Downloads the public PDF endpoint instead of asking WebView to handle an attachment. */
    private void downloadBookPdf() {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[a-z0-9][a-z0-9-]{0,62}")) {
            SimpleToast.show(this, I18n.text(this, "这本书暂时不能下载"));
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingPdfDownloadSlug = slug;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_DOWNLOADS);
            return;
        }
        if (bookPdfDownloading) return;
        bookPdfDownloading = true;
        SimpleToast.show(this, I18n.text(this, "正在生成 PDF，首次下载约需一分钟"));
        String requestedSlug = slug;
        bookIo.execute(() -> {
            boolean saved = false;
            try {
                fetchBookPdf(requestedSlug);
                saved = true;
            } catch (Exception ignored) { }
            boolean result = saved;
            runOnUiThread(() -> {
                bookPdfDownloading = false;
                if (isFinishing() || isDestroyed()) return;
                // fetchBookPdf returns only after MediaStore has published the file.
                // Do not launch a viewer here: the page action promises a download.
                if (!result) {
                    SimpleToast.show(this, I18n.text(this, "下载失败，请检查网络后重试"));
                    return;
                }
                SimpleToast.show(this, I18n.text(this, "PDF 已保存到下载/VoiceDrop"));
            });
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_WRITE_DOWNLOADS) return;
        String slug = pendingPdfDownloadSlug;
        pendingPdfDownloadSlug = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && slug != null) {
            downloadBookPdf();
        } else {
            SimpleToast.show(this, I18n.text(this, "需要存储权限才能保存 PDF"));
        }
    }

    private void fetchBookPdf(String slug) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://jianshuo.dev/agent/books/pdf/" + slug).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20_000);
        // 首次请求必须等待 Worker 渲染整本书，不能沿用普通 API 的 120 秒上限。
        connection.setReadTimeout(180_000);
        connection.setRequestProperty("X-VD-Platform", "android");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("PDF download failed: " + code);
            saveBookPdf(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    /** Persists PDFs in the user-visible Downloads/VoiceDrop directory. */
    private void saveBookPdf(InputStream input) throws Exception {
        String fileName = safePdfFileName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VoiceDrop");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("cannot create download");
            try (InputStream source = input; OutputStream destination = getContentResolver().openOutputStream(uri)) {
                if (destination == null) throw new IllegalStateException("cannot open download");
                copyPdf(source, destination);
            } catch (Exception error) {
                getContentResolver().delete(uri, null, null);
                throw error;
            }
            ContentValues published = new ContentValues();
            published.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, published, null, null);
            return;
        }

        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VoiceDrop");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("download directory unavailable");
        File target = new File(directory, fileName);
        File temporary = new File(directory, fileName + ".part");
        try (InputStream source = input; OutputStream destination = new FileOutputStream(temporary)) {
            copyPdf(source, destination);
            if (temporary.length() == 0L) throw new IllegalStateException("empty PDF");
            if (target.exists() && !target.delete()) throw new IllegalStateException("old PDF cannot be replaced");
            if (!temporary.renameTo(target)) throw new IllegalStateException("PDF cannot be finalized");
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private void copyPdf(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        long total = 0L;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
            total += read;
        }
        if (total == 0L) throw new IllegalStateException("empty PDF");
    }

    private String safePdfFileName() {
        String title = getIntent().getStringExtra("shareTitle");
        if (title == null || title.trim().isEmpty()) title = getIntent().getStringExtra("displayTitle");
        if (title == null || title.trim().isEmpty()) title = getIntent().getStringExtra("slug");
        String cleaned = title == null ? "book" : title.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return (cleaned.isEmpty() ? "book" : cleaned) + ".pdf";
    }

    private void addMenuDivider(LinearLayout menu) {
        View divider = new View(this);
        divider.setBackgroundColor(0xffe0d8cc);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(dp(16), 0, dp(16), 0);
        menu.addView(divider, dividerParams);
    }

    /** The reader must use the server answer: a cached shelf can be stale after login changes. */
    private void loadOwnership() {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return;
        AuthStore auth = new AuthStore(this);
        bookIo.execute(() -> {
            try {
                HttpClient.Response response = new HttpClient().get(
                        Api.publicWebBase() + "/books/" + slug + "/hidden", auth.bearer(),
                        new HttpClient.RequestOptions().readTimeoutMs(15_000)
                                .header("Cache-Control", "no-cache"));
                if (!response.ok()) return;
                org.json.JSONObject result = new org.json.JSONObject(response.text());
                boolean mine = result.optBoolean("mine", false);
                boolean hidden = result.optBoolean("hidden", false);
                runOnUiThread(() -> { isMine = mine; isHidden = hidden; });
            } catch (Exception ignored) { }
        });
    }

    private void setHidden(boolean hidden) {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return;
        AuthStore auth = new AuthStore(this);
        bookIo.execute(() -> {
            int code = 0;
            try {
                HttpClient.Response response = new HttpClient().postJson(
                        Api.publicWebBase() + "/books/" + slug + "/hidden", auth.bearer(),
                        ("{\"hidden\":" + hidden + "}").getBytes(StandardCharsets.UTF_8),
                        new HttpClient.RequestOptions().readTimeoutMs(20_000));
                code = response.code;
            } catch (Exception ignored) { }
            int resultCode = code;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (resultCode >= 200 && resultCode < 300) {
                    isHidden = hidden;
                    new BookShelfCache(this, auth.libraryCacheIdentity()).clear();
                    SimpleToast.show(this, I18n.text(this, hidden ? "已隐藏，书架上看不到了" : "已取消隐藏"));
                } else {
                    SimpleToast.show(this, I18n.text(this, resultCode == 403 ? "这不是你的书，改不了" : "没改成，过会儿再试"));
                    if (resultCode == 403) loadOwnership();
                }
            });
        });
    }

    private LinearLayout bookMenuRow(String label, String glyph, int iconColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(16), 0);
        row.setMinimumHeight(dp(48));
        TextView text = new TextView(this);
        text.setText(I18n.text(this, label));
        text.setTextSize(17);
        text.setTextColor(Theme.INK);
        text.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        RemixIconView icon = new RemixIconView(this);
        icon.setIcon(glyph);
        icon.setTextSize(22);
        icon.setTextColor(iconColor);
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        return row;
    }

    private GradientDrawable roundedMenuBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xf9ffffff);
        background.setCornerRadius(dp(16));
        return background;
    }

    private void showBookShareSheet() {
        List<ShareBottomSheet.Item> items = new ArrayList<>();
        items.add(ShareBottomSheet.drawable(I18n.text(this, "微信好友"), R.drawable.ic_wechat,
                ShareBottomSheet.WECHAT_GREEN, Color.WHITE, () -> shareBookToWechat(false)));
        items.add(ShareBottomSheet.remix(I18n.text(this, "朋友圈"), RemixIconGlyph.CAMERA_LENS_LINE,
                ShareBottomSheet.WECHAT_GREEN, Color.WHITE, () -> shareBookToWechat(true)));
        items.add(ShareBottomSheet.drawable(I18n.text(this, "复制链接"), R.drawable.ic_link_flat,
                ShareBottomSheet.NEUTRAL_BACKGROUND, Theme.SECONDARY, 23, this::copyBookLink));
        items.add(ShareBottomSheet.drawable(I18n.text(this, "其它分享"), R.drawable.ic_share_forward,
                ShareBottomSheet.NEUTRAL_BACKGROUND, Theme.SECONDARY, 24, this::shareBookWithSystem));
        ShareBottomSheet.show(this, items);
    }

    private void openBookRevision() {
        reviseSheet = BookReviseBottomSheet.show(this, getIntent().getStringExtra("slug"),
                getIntent().getStringExtra("displayTitle"), () -> {
                    reviseSheet = null;
                    if (web != null && !isFinishing() && !isDestroyed()) web.reload();
                });
    }

    @Override protected void onStart() {
        super.onStart();
        if (reviseSheet != null) reviseSheet.onHostStart();
    }

    @Override protected void onStop() {
        if (reviseSheet != null) reviseSheet.onHostStop();
        super.onStop();
    }

    private void copyBookLink() {
        BookShareTarget.Target target = currentShareTarget();
        if (target == null) {
            SimpleToast.show(this, I18n.text(this, "复制失败"));
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            SimpleToast.show(this, I18n.text(this, "复制失败"));
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(I18n.text(this, "VoiceDrop 书籍链接"), target.url));
        SimpleToast.show(this, I18n.text(this, "链接已复制"));
    }

    private void shareBookToWechat(boolean timeline) {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return;
        BookShareTarget.Target target = currentShareTarget();
        if (target == null) return;
        if (!getIntent().getBooleanExtra("cover", false)) {
            showWechatShareResult(sendToWechat(timeline, target, null), target);
            return;
        }
        SimpleToast.show(this, I18n.text(this, "正在准备微信分享…"));
        shareIo.execute(() -> {
            Bitmap cover = loadBookCover(getIntent().getStringExtra("coverUrl"));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (cover != null) cover.recycle();
                    return;
                }
                WechatMiniProgramShare.Result result = sendToWechat(
                        timeline, target, cover);
                if (cover != null) cover.recycle();
                showWechatShareResult(result, target);
            });
        });
    }

    private WechatMiniProgramShare.Result sendToWechat(boolean timeline,
                                                        BookShareTarget.Target target, Bitmap cover) {
        String description = target.chapter ? rootBookTitle() : I18n.text(this, "VoiceDrop 图书馆 · 点开即读");
        if (timeline) return WechatMiniProgramShare.sendTimeline(
                this, target.title, target.url, cover, description);
        String title = getIntent().getStringExtra("shareTitle");
        String main = getIntent().getStringExtra("displayTitle");
        return WechatMiniProgramShare.send(
                this, target.title, target.url,
                WechatMiniProgramShare.bookReaderPath(
                        getIntent().getStringExtra("slug"), title, main,
                        getIntent().getStringExtra("author"),
                        getIntent().getBooleanExtra("cover", false),
                        getIntent().getLongExtra("coverAt", 0L),
                        target.chapter ? target.url : null),
                cover, description);
    }

    private void shareBookWithSystem() {
        BookShareTarget.Target target = currentShareTarget();
        if (target == null) return;
        String text = target.title + "\n" + target.url;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, target.title);
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, I18n.text(this, "分享这本书")));
    }

    private Bitmap loadBookCover(String coverUrl) {
        if (coverUrl == null || coverUrl.trim().isEmpty()) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(coverUrl).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            return BitmapFactory.decodeStream(connection.getInputStream());
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void updateShareLocation(WebView view, String url) {
        currentPageUrl = url;
        currentPageTitle = view == null ? null : view.getTitle();
    }

    private BookShareTarget.Target currentShareTarget() {
        String slug = getIntent().getStringExtra("slug");
        if (slug == null || !slug.matches("[A-Za-z0-9_-]+")) return null;
        String root = "https://voicedrop.cn/books/" + slug + "/";
        String bookTitle = getIntent().getStringExtra("shareTitle");
        if (bookTitle == null || bookTitle.trim().isEmpty()) {
            bookTitle = getIntent().getStringExtra("displayTitle");
        }
        return BookShareTarget.resolve(root, currentPageUrl, currentPageTitle,
                bookTitle, getIntent().getStringExtra("author"));
    }

    private String rootBookTitle() {
        String title = getIntent().getStringExtra("shareTitle");
        if (title == null || title.trim().isEmpty()) title = getIntent().getStringExtra("displayTitle");
        if (title == null || title.trim().isEmpty()) title = I18n.text(this, "未命名");
        String author = getIntent().getStringExtra("author");
        return "《" + title.trim() + "》"
                + (author == null || author.trim().isEmpty() ? "" : " — " + author.trim());
    }

    private void showWechatShareResult(WechatMiniProgramShare.Result result,
                                       BookShareTarget.Target target) {
        if (result == WechatMiniProgramShare.Result.WECHAT_NOT_INSTALLED) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(I18n.text(this, "VoiceDrop 书籍链接"),
                        target.title + "\n" + target.url));
                SimpleToast.show(this, I18n.text(this, "未安装微信，链接已复制"));
                return;
            }
        }
        SimpleToast.show(this, result.message());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else finishWithPageTransition();
    }

    private void finishWithPageTransition() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override protected void onDestroy() {
        if (reviseSheet != null) reviseSheet.dismiss();
        if (web != null) web.destroy();
        shareIo.shutdownNow();
        bookIo.shutdownNow();
        super.onDestroy();
    }
}
