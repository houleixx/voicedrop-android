package com.baixingai.voicedrop;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.core.ArticlePhotoInsert;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InsertPhotoActivity extends Activity {
    public static final String EXTRA_PHOTO_PATHS = "photoPaths";
    public static final String EXTRA_CAPTURE_TIMES = "captureTimes";
    private static final int REQ_CAMERA = 31;
    private static final int REQ_LIBRARY = 32;

    private final List<SelectedPhoto> photos = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private TextView countPill;
    private TextView doneButton;
    private FrameLayout emptyHint;
    private HorizontalScrollView filmstrip;
    private LinearLayout filmRow;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarDefaults.applyLightActivity(getWindow(), Theme.BG, false);
        getWindow().setStatusBarColor(Theme.BG);
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        render();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void render() {
        root.removeAllViews();

        emptyHint = new FrameLayout(this);
        root.addView(emptyHint, match());
        buildEmptyHint();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(12), dp(18), dp(8));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        TextView cancel = topText("取消", 16, Theme.SECONDARY, Typeface.NORMAL);
        cancel.setOnClickListener(v -> finish());
        top.addView(cancel, new LinearLayout.LayoutParams(dp(64), dp(42)));

        countPill = topText("", 13, Theme.SECONDARY, Typeface.NORMAL);
        countPill.setGravity(Gravity.CENTER);
        countPill.setBackground(roundStroke(Theme.CARD, 14, Theme.BORDER_CHROME, 1));
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(0, dp(32), 1);
        countLp.setMargins(dp(8), 0, dp(8), 0);
        top.addView(countPill, countLp);

        doneButton = topText("完成", 16, Theme.FAINT, Typeface.BOLD);
        doneButton.setGravity(Gravity.CENTER);
        doneButton.setBackground(roundStroke(Theme.CARD, 14, Theme.BORDER_CHROME, 1));
        doneButton.setOnClickListener(v -> finishWithPhotos());
        top.addView(doneButton, new LinearLayout.LayoutParams(dp(72), dp(42)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(24), dp(12), dp(24), dp(12));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, dp(112), Gravity.BOTTOM));

        bottom.addView(iconButton(AliIconFont.IMAGE, "相册", this::openLibrary), new LinearLayout.LayoutParams(dp(84), dp(72)));
        View spacer = new View(this);
        bottom.addView(spacer, new LinearLayout.LayoutParams(dp(38), 1));
        bottom.addView(iconButton(AliIconFont.CAMERA, "拍照", this::openCamera), new LinearLayout.LayoutParams(dp(84), dp(72)));

        filmstrip = new HorizontalScrollView(this);
        filmstrip.setHorizontalScrollBarEnabled(false);
        filmstrip.setPadding(dp(18), 0, dp(18), 0);
        filmRow = new LinearLayout(this);
        filmRow.setOrientation(LinearLayout.HORIZONTAL);
        filmRow.setGravity(Gravity.CENTER_VERTICAL);
        filmstrip.addView(filmRow, new HorizontalScrollView.LayoutParams(-2, -1));
        FrameLayout.LayoutParams filmLp = new FrameLayout.LayoutParams(-1, dp(86), Gravity.TOP);
        filmLp.setMargins(0, dp(70), 0, 0);
        root.addView(filmstrip, filmLp);

        refreshState();
    }

    private void buildEmptyHint() {
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, AliIconFont.CAMERA, Theme.ACCENT);
        stack.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = topText("插入图片", 15, Theme.INK, Typeface.NORMAL);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.setMargins(0, dp(12), 0, 0);
        stack.addView(title, titleLp);
        TextView sub = topText("拍照或从相册选择，照片会交给 AI 放进文章", 13, Theme.SECONDARY, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.setMargins(0, dp(6), 0, 0);
        stack.addView(sub, subLp);
        emptyHint.addView(stack, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
    }

    private View iconButton(int iconRes, String label, Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(roundStroke(Theme.CARD, 14, Theme.BORDER_CHROME, 1));
        box.setClickable(true);
        box.setOnClickListener(v -> action.run());
        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, iconRes, Theme.ACCENT);
        box.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView text = topText(label, 12, Theme.INK, Typeface.NORMAL);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-2, -2);
        textLp.setMargins(0, dp(6), 0, 0);
        box.addView(text, textLp);
        return box;
    }

    private void refreshState() {
        emptyHint.setVisibility(photos.isEmpty() ? View.VISIBLE : View.GONE);
        filmstrip.setVisibility(photos.isEmpty() ? View.GONE : View.VISIBLE);
        countPill.setText(photos.isEmpty() ? "选择要插入的图片" : "已选 " + photos.size() + " 张");
        doneButton.setTextColor(photos.isEmpty() ? Theme.FAINT : Color.WHITE);
        doneButton.setBackground(photos.isEmpty()
                ? roundStroke(Theme.CARD, 14, Theme.BORDER_CHROME, 1)
                : round(Theme.RED, 14));
        rebuildFilmstrip();
    }

    private void rebuildFilmstrip() {
        filmRow.removeAllViews();
        for (int i = 0; i < photos.size(); i++) {
            final int index = i;
            SelectedPhoto photo = photos.get(i);
            FrameLayout cell = new FrameLayout(this);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(photo.thumb);
            cell.addView(image, new FrameLayout.LayoutParams(dp(70), dp(70), Gravity.CENTER));
            TextView delete = topText("×", 16, 0xffffffff, Typeface.BOLD);
            delete.setIncludeFontPadding(false);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(0, dp(1), 0, 0);
            delete.setBackground(round(0xaa000000, 11));
            delete.setOnClickListener(v -> {
                if (index < photos.size()) {
                    photos.remove(index);
                    refreshState();
                }
            });
            cell.addView(delete, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.RIGHT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(78));
            lp.setMargins(0, 0, dp(8), 0);
            filmRow.addView(cell, lp);
        }
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            toast("没有可用相机");
            return;
        }
        startActivityForResult(intent, REQ_CAMERA);
    }

    private void openLibrary() {
        Intent intent = albumIntent();
        startActivityForResult(intent, REQ_LIBRARY);
    }

    private Intent albumIntent() {
        if (Build.VERSION.SDK_INT >= 33) {
            Intent picker = new Intent(MediaStore.ACTION_PICK_IMAGES);
            picker.setType("image/*");
            picker.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, Math.min(50, MediaStore.getPickImagesMaxLimit()));
            if (picker.resolveActivity(getPackageManager()) != null) return picker;
        }

        Intent album = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        album.setType("image/*");
        if (album.resolveActivity(getPackageManager()) != null) return album;

        Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
        fallback.setType("image/*");
        fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        return Intent.createChooser(fallback, "选择图片");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_LIBRARY) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    addUri(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                addUri(data.getData());
            }
            return;
        }
        if (requestCode == REQ_CAMERA) {
            Object extra = data.getExtras() == null ? null : data.getExtras().get("data");
            if (extra instanceof Bitmap) addBitmap((Bitmap) extra);
        }
    }

    private void addUri(Uri uri) {
        io.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IllegalStateException("无法读取图片");
                    bytes = HttpClient.readAll(in);
                }
                Bitmap bitmap = ArticlePhotoInsert.decodeSampledBitmap(bytes, 1440);
                if (bitmap == null) throw new IllegalStateException("无法解码图片");
                addPhotoBytes(ArticlePhotoInsert.fitJpeg(bitmap, 1440, 900_000, 86), System.currentTimeMillis());
            } catch (Exception e) {
                toast("照片读取失败：" + e.getMessage());
            }
        });
    }

    private void addBitmap(Bitmap bitmap) {
        io.execute(() -> {
            try {
                addPhotoBytes(ArticlePhotoInsert.squareJpeg(bitmap, 1080, 86), System.currentTimeMillis());
            } catch (Exception e) {
                toast("照片保存失败：" + e.getMessage());
            }
        });
    }

    private void addPhotoBytes(byte[] bytes, long capturedAtMillis) throws Exception {
        File dir = new File(getCacheDir(), "insert_photos");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建缓存目录");
        File file = File.createTempFile("photo-", ".jpg", dir);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
        Bitmap thumb = ArticlePhotoInsert.decodeSampledBitmap(bytes, dp(140));
        if (thumb != null) thumb = Bitmap.createScaledBitmap(thumb, dp(70), dp(70), true);
        Bitmap finalThumb = thumb;
        runOnUiThread(() -> {
            photos.add(new SelectedPhoto(file.getAbsolutePath(), capturedAtMillis, finalThumb));
            refreshState();
        });
    }

    private void finishWithPhotos() {
        if (photos.isEmpty()) return;
        ArrayList<String> paths = new ArrayList<>();
        long[] times = new long[photos.size()];
        for (int i = 0; i < photos.size(); i++) {
            paths.add(photos.get(i).path);
            times[i] = photos.get(i).capturedAtMillis;
        }
        Intent data = new Intent();
        data.putStringArrayListExtra(EXTRA_PHOTO_PATHS, paths);
        data.putExtra(EXTRA_CAPTURE_TIMES, times);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            toast("权限被拒绝");
            return;
        }
        if (requestCode == REQ_CAMERA) openCamera();
    }

    private TextView topText(String value, int sp, int color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, style);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundStroke(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = round(color, radiusDp);
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private void toast(String message) {
        runOnUiThread(() -> SimpleToast.show(this, message));
    }

    private static final class SelectedPhoto {
        final String path;
        final long capturedAtMillis;
        final Bitmap thumb;

        SelectedPhoto(String path, long capturedAtMillis, Bitmap thumb) {
            this.path = path;
            this.capturedAtMillis = capturedAtMillis;
            this.thumb = thumb;
        }
    }
}
