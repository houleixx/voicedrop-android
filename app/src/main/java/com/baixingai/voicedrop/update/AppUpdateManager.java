package com.baixingai.voicedrop.update;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.baixingai.voicedrop.ui.IosDialog;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppUpdateManager {
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/houleixx/voicedrop-android/releases/latest";
    private static final int DOWNLOAD_PROGRESS_HEIGHT_DP = 5;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static boolean autoCheckStarted;
    private static boolean manualCheckRunning;

    private AppUpdateManager() {}

    public static void checkOnStartup(Activity activity) {
        UpdatePrefs prefs = new UpdatePrefs(activity);
        long now = System.currentTimeMillis();
        String currentVersion = currentVersionName(activity);
        if (autoCheckStarted || !prefs.shouldAutoCheck(now, currentVersion)) return;
        autoCheckStarted = true;
        prefs.markAutoChecked(now, currentVersion);
        check(activity, false);
    }

    public static void checkManually(Activity activity) {
        if (manualCheckRunning) {
            SimpleToast.show(activity, "正在检查更新…");
            return;
        }
        manualCheckRunning = true;
        SimpleToast.show(activity, "正在检查更新…");
        check(activity, true);
    }

    private static void check(Activity activity, boolean manual) {
        String currentVersion = currentVersionName(activity);
        EXECUTOR.execute(() -> {
            CheckResult result;
            try {
                result = fetchLatest(currentVersion);
            } catch (Exception e) {
                result = CheckResult.failed();
            }
            CheckResult finalResult = result;
            activity.runOnUiThread(() -> handleCheckResult(activity, finalResult, manual));
        });
    }

    private static void handleCheckResult(Activity activity, CheckResult result, boolean manual) {
        if (manual) manualCheckRunning = false;
        if (activity.isFinishing()) return;
        if (result.status == Status.NO_UPDATE) {
            if (manual) SimpleToast.show(activity, "已是最新版本");
            return;
        }
        if (result.status == Status.RATE_LIMITED) {
            if (manual) SimpleToast.show(activity, "检查太频繁，稍后再试");
            return;
        }
        if (result.status != Status.UPDATE_AVAILABLE || result.release == null) {
            if (manual) SimpleToast.show(activity, "检查更新失败");
            return;
        }

        GitHubRelease release = result.release;
        UpdatePrefs prefs = new UpdatePrefs(activity);
        if (prefs.isIgnored(release.tagName)) {
            if (manual) SimpleToast.show(activity, "已忽略此版本：" + release.tagName);
            return;
        }

        String message = "发现新版本 " + release.tagName + "\n当前版本 " + currentVersionName(activity);
        if (release.apkSize > 0) message += "\n安装包约 " + formatBytes(release.apkSize);
        IosDialog.show(activity, "发现新版本", message,
                "立即更新", () -> downloadAndInstall(activity, release),
                "忽略此版本", () -> {
                    prefs.ignore(release.tagName);
                    SimpleToast.show(activity, "已忽略 " + release.tagName);
                });
    }

    private static CheckResult fetchLatest(String currentVersion) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(20_000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "VoiceDrop-Android");

        int code = conn.getResponseCode();
        if (code == 403 || code == 429) {
            conn.disconnect();
            return CheckResult.rateLimited();
        }
        if (code == 404) {
            conn.disconnect();
            return CheckResult.noUpdate();
        }
        if (code < 200 || code >= 300) {
            conn.disconnect();
            return CheckResult.failed();
        }

        String body;
        try (InputStream in = conn.getInputStream()) {
            body = new String(readAll(in), "UTF-8");
        } finally {
            conn.disconnect();
        }

        GitHubRelease release = GitHubRelease.parse(body);
        if (release.draft || release.prerelease || !release.hasApk()) return CheckResult.noUpdate();
        return AppVersion.isNewer(release.tagName, currentVersion)
                ? CheckResult.update(release)
                : CheckResult.noUpdate();
    }

    private static void downloadAndInstall(Activity activity, GitHubRelease release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            IosDialog.show(activity, "需要安装权限",
                    "请允许 VoiceDrop 安装更新包，开启后重新点击更新。",
                    "去开启", () -> openInstallPermission(activity));
            return;
        }

        TextView progressText = new TextView(activity);
        ProgressBar progressBar = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        DownloadSession session = new DownloadSession();
        IosDialog progressDialog = showDownloadProgress(activity, progressText, progressBar, session);
        EXECUTOR.execute(() -> {
            try {
                File apk = downloadApk(activity, release, session, (downloaded, total) -> {
                    activity.runOnUiThread(() -> updateDownloadProgress(progressText, progressBar,
                            downloaded, total));
                });
                activity.runOnUiThread(() -> {
                    if (session.isCanceled()) return;
                    dismissDialog(progressDialog);
                    installApk(activity, apk);
                });
            } catch (DownloadCanceledException e) {
                deleteQuietly(session.apk);
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (session.isCanceled()) return;
                    dismissDialog(progressDialog);
                    SimpleToast.show(activity, "下载更新失败");
                });
            }
        });
    }

    private static File downloadApk(Activity activity, GitHubRelease release,
            DownloadSession session, DownloadProgressListener listener) throws Exception {
        File dir = new File(activity.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create update dir");
        File apk = new File(dir, safeFileName(release));
        session.apk = apk;
        deleteQuietly(apk);

        HttpURLConnection conn = (HttpURLConnection) new URL(release.apkDownloadUrl).openConnection();
        session.connection = conn;
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("User-Agent", "VoiceDrop-Android");
        try {
            if (session.isCanceled()) throw new DownloadCanceledException();
            int code = conn.getResponseCode();
            if (session.isCanceled()) throw new DownloadCanceledException();
            if (code < 200 || code >= 300) throw new IllegalStateException("Download failed: " + code);
            long total = conn.getContentLengthLong();
            if (listener != null) listener.onProgress(0L, total);
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[8192];
                int n;
                long downloaded = 0L;
                long lastNotified = 0L;
                while ((n = in.read(buffer)) >= 0) {
                    if (session.isCanceled()) throw new DownloadCanceledException();
                    out.write(buffer, 0, n);
                    downloaded += n;
                    if (listener != null && (downloaded - lastNotified >= 256L * 1024L
                            || downloaded == total)) {
                        lastNotified = downloaded;
                        listener.onProgress(downloaded, total);
                    }
                }
            }
        } finally {
            session.connection = null;
            conn.disconnect();
            if (session.isCanceled()) deleteQuietly(apk);
        }
        if (!apk.exists() || apk.length() <= 0L) throw new IllegalStateException("Empty APK");
        return apk;
    }

    private static void installApk(Activity activity, File apk) {
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            SimpleToast.show(activity, "无法打开安装器");
        }
    }

    private static void openInstallPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            SimpleToast.show(activity, "无法打开安装权限设置");
        }
    }

    private static String safeFileName(GitHubRelease release) {
        String name = release.apkName == null || release.apkName.isEmpty()
                ? "voicedrop-" + release.tagName + ".apk"
                : release.apkName;
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L * 1024L) return Math.max(1L, bytes / 1024L) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private static IosDialog showDownloadProgress(Activity activity, TextView text, ProgressBar bar,
            DownloadSession session) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 24));
        box.setLayoutParams(new ScrollView.LayoutParams(-1, -1));

        text.setText("正在连接下载…");
        text.setTextSize(15);
        text.setTextColor(Theme.INK);
        text.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        text.setGravity(Gravity.CENTER);
        box.addView(text, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1,
                dp(activity, DOWNLOAD_PROGRESS_HEIGHT_DP));
        barLp.setMargins(0, dp(activity, 18), 0, 0);
        configureDownloadProgressBar(activity, bar);
        box.addView(bar, barLp);

        return IosDialog.showProgressActions(activity, "正在下载更新", box, 104,
                "取消", () -> {
                    session.cancel();
                    deleteQuietly(session.apk);
                    SimpleToast.show(activity, "已取消更新");
                },
                "后台下载", () -> {
                    SimpleToast.show(activity, "正在后台下载更新");
                });
    }

    private static void updateDownloadProgress(TextView text, ProgressBar bar,
            long downloaded, long total) {
        if (total > 0L) {
            int percent = (int) Math.min(100L, downloaded * 100L / total);
            bar.setIndeterminate(false);
            bar.setProgress(percent);
            text.setText("已下载 " + formatBytes(downloaded) + " / " + formatBytes(total)
                    + "（" + percent + "%）");
        } else {
            bar.setIndeterminate(true);
            text.setText("已下载 " + formatBytes(downloaded));
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static void configureDownloadProgressBar(Activity activity, ProgressBar bar) {
        bar.setMax(100);
        bar.setProgress(0);
        bar.setPadding(0, 0, 0, 0);
        bar.setMinimumHeight(dp(activity, DOWNLOAD_PROGRESS_HEIGHT_DP));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bar.setMinHeight(dp(activity, DOWNLOAD_PROGRESS_HEIGHT_DP));
            bar.setMaxHeight(dp(activity, DOWNLOAD_PROGRESS_HEIGHT_DP));
        }
        bar.setProgressDrawable(roundedProgressDrawable(activity));
        bar.setIndeterminateDrawable(new IndeterminateRoundedBarDrawable(activity,
                0x1fe5392e, Theme.RED));
        bar.setIndeterminate(true);
    }

    private static Drawable roundedProgressDrawable(Activity activity) {
        GradientDrawable track = roundedBar(activity, 0x1fe5392e);
        ClipDrawable progress = new ClipDrawable(roundedBar(activity, Theme.RED),
                Gravity.LEFT, ClipDrawable.HORIZONTAL);
        LayerDrawable layer = new LayerDrawable(new Drawable[] { track, progress });
        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.progress);
        return layer;
    }

    private static GradientDrawable roundedBar(Activity activity, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, DOWNLOAD_PROGRESS_HEIGHT_DP) / 2f);
        return drawable;
    }

    private static final class IndeterminateRoundedBarDrawable extends Drawable implements Animatable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int trackColor;
        private final int progressColor;
        private final float cornerRadius;
        private final ValueAnimator animator;
        private float offset;

        IndeterminateRoundedBarDrawable(Activity activity, int trackColor, int progressColor) {
            this.trackColor = trackColor;
            this.progressColor = progressColor;
            this.cornerRadius = dp(activity, DOWNLOAD_PROGRESS_HEIGHT_DP) / 2f;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(900L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                offset = (float) animation.getAnimatedValue();
                invalidateSelf();
            });
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;

            RectF track = new RectF(bounds);
            paint.setColor(trackColor);
            canvas.drawRoundRect(track, cornerRadius, cornerRadius, paint);

            float width = bounds.width();
            float segmentWidth = Math.max(width * 0.34f, bounds.height() * 3f);
            float start = bounds.left + (width + segmentWidth) * offset - segmentWidth;
            RectF segment = new RectF(start, bounds.top, start + segmentWidth, bounds.bottom);

            canvas.save();
            canvas.clipRect(bounds);
            paint.setColor(progressColor);
            canvas.drawRoundRect(segment, cornerRadius, cornerRadius, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            if (visible) {
                if (restart) stop();
                start();
            } else {
                stop();
            }
            return changed;
        }

        @Override
        public void start() {
            if (!animator.isStarted()) animator.start();
        }

        @Override
        public void stop() {
            if (animator.isStarted()) animator.cancel();
        }

        @Override
        public boolean isRunning() {
            return animator.isRunning();
        }
    }

    private static final class DownloadSession {
        private volatile boolean canceled;
        private volatile HttpURLConnection connection;
        private volatile File apk;

        void cancel() {
            canceled = true;
            HttpURLConnection activeConnection = connection;
            if (activeConnection != null) activeConnection.disconnect();
            deleteQuietly(apk);
        }

        boolean isCanceled() {
            return canceled;
        }
    }

    private static final class DownloadCanceledException extends Exception {
    }

    private static void dismissDialog(IosDialog dialog) {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static String currentVersionName(Activity activity) {
        if (activity == null) return "0.1.0";
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "0.1.0" : info.versionName;
        } catch (Exception e) {
            return "0.1.0";
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private enum Status {
        UPDATE_AVAILABLE,
        NO_UPDATE,
        RATE_LIMITED,
        FAILED
    }

    private static final class CheckResult {
        final Status status;
        final GitHubRelease release;

        CheckResult(Status status, GitHubRelease release) {
            this.status = status;
            this.release = release;
        }

        static CheckResult update(GitHubRelease release) {
            return new CheckResult(Status.UPDATE_AVAILABLE, release);
        }

        static CheckResult noUpdate() {
            return new CheckResult(Status.NO_UPDATE, null);
        }

        static CheckResult rateLimited() {
            return new CheckResult(Status.RATE_LIMITED, null);
        }

        static CheckResult failed() {
            return new CheckResult(Status.FAILED, null);
        }
    }

    private interface DownloadProgressListener {
        void onProgress(long downloaded, long total);
    }
}
