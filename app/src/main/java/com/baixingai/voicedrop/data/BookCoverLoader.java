package com.baixingai.voicedrop.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;

import com.baixingai.voicedrop.core.BookCoverPolicy;
import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.net.HttpClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loads immutable, versioned book covers while retaining the cloth cover as fallback. */
public final class BookCoverLoader {
    private static final long MAX_CACHE_BYTES = 64L * 1024L * 1024L;
    private final File diskDir;
    /** Dedicated pool: cover failures can never delay the shelf index request. */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Set<Request> requests = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public BookCoverLoader(Context context) {
        diskDir = new File(context.getApplicationContext().getFilesDir(), "book-cover-cache");
        if (!diskDir.exists()) diskDir.mkdirs();
    }

    public void load(BookShelfIndex.Book book, String coverUrl, ImageView image) {
        if (book == null || image == null || !book.cover) return;
        Request request = new Request(book, coverUrl, image);
        requests.add(request);
        image.addOnAttachStateChangeListener(request);
        request.start();
    }

    public void cancelAll() {
        for (Request request : new ArrayList<>(requests)) request.cancel();
    }

    public void shutdown() {
        cancelAll();
        scheduler.shutdownNow();
    }

    private final class Request implements View.OnAttachStateChangeListener {
        private final BookShelfIndex.Book book;
        private final String coverUrl;
        private final ImageView image;
        private final AtomicBoolean cleanedUp = new AtomicBoolean();
        private volatile Future<?> future;
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;

        Request(BookShelfIndex.Book book, String coverUrl, ImageView image) {
            this.book = book;
            this.coverUrl = coverUrl;
            this.image = image;
        }

        synchronized void start() {
            if (!cancelled) future = scheduler.submit(this::loadCache);
        }

        private void loadCache() {
            String key = BookCoverPolicy.cacheKey(book.slug, book.coverAt);
            File target = new File(diskDir, safeFileName(key) + ".jpg");
            Bitmap cached = decode(target);
            if (cached != null) {
                deliver(cached);
                cleanup();
                return;
            }
            if (target.exists()) target.delete();
            scheduleAttempt(target, 0);
        }

        private synchronized void scheduleAttempt(File target, int attempt) {
            if (cancelled) return;
            long[] delays = BookCoverPolicy.retryDelaysMs();
            if (attempt >= delays.length) {
                cleanup();
                return;
            }
            future = scheduler.schedule(() -> runAttempt(target, attempt),
                    delays[attempt], TimeUnit.MILLISECONDS);
        }

        private void runAttempt(File target, int attempt) {
            if (cancelled) return;
            try {
                byte[] data = download(coverUrl, attempt >= 2);
                if (!cancelled && data != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bitmap != null) {
                        writeAtomically(target, data);
                        pruneDiskCache();
                        deliver(bitmap);
                        cleanup();
                        return;
                    }
                }
            } catch (Exception ignored) {
                // Schedule the next bounded attempt; no worker is held during backoff.
            }
            scheduleAttempt(target, attempt + 1);
        }

        private byte[] download(String url, boolean bypassHttpCache) throws Exception {
            HttpURLConnection current = (HttpURLConnection) new URL(url).openConnection();
            connection = current;
            try {
                current.setConnectTimeout(15_000);
                current.setReadTimeout(30_000);
                current.setRequestProperty("X-VD-Platform", "android");
                if (bypassHttpCache) {
                    current.setUseCaches(false);
                    current.setRequestProperty("Cache-Control", "no-cache");
                }
                int status = current.getResponseCode();
                if (status < 200 || status >= 300) return null;
                return HttpClient.readAll(current.getInputStream());
            } finally {
                current.disconnect();
                if (connection == current) connection = null;
            }
        }

        private void deliver(Bitmap bitmap) {
            if (cancelled) return;
            image.post(() -> {
                if (!cancelled) image.setImageBitmap(bitmap);
            });
        }

        synchronized void cancel() {
            cancelled = true;
            HttpURLConnection current = connection;
            if (current != null) current.disconnect();
            Future<?> task = future;
            if (task != null) task.cancel(true);
            cleanup();
        }

        private void cleanup() {
            if (!cleanedUp.compareAndSet(false, true)) return;
            requests.remove(this);
            image.post(() -> image.removeOnAttachStateChangeListener(this));
        }

        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) { cancel(); }
    }

    private static Bitmap decode(File file) {
        if (!file.isFile()) return null;
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private static void writeAtomically(File target, byte[] data) throws Exception {
        File temp = new File(target.getParentFile(), target.getName() + "." + UUID.randomUUID() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(data);
        }
        if (!temp.renameTo(target)) {
            try (FileInputStream in = new FileInputStream(temp);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8_192];
                int count;
                while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
            } finally {
                temp.delete();
            }
        }
    }

    private void pruneDiskCache() {
        File[] files = diskDir.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        java.util.Arrays.sort(files, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        long keptBytes = 0L;
        for (File file : files) {
            keptBytes += file.length();
            if (keptBytes > MAX_CACHE_BYTES) file.delete();
        }
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
