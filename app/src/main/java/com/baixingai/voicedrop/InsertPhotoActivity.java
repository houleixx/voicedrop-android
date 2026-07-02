package com.baixingai.voicedrop;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.Theme;

import java.io.ByteArrayOutputStream;
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
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
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
        top.setPadding(dp(18), getStatusBarHeight() + dp(12), dp(18), dp(8));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        TextView cancel = topText("取消", 16, 0xccffffff, Typeface.NORMAL);
        cancel.setOnClickListener(v -> finish());
        top.addView(cancel, new LinearLayout.LayoutParams(dp(64), dp(42)));

        countPill = topText("", 13, 0xddffffff, Typeface.NORMAL);
        countPill.setGravity(Gravity.CENTER);
        countPill.setBackground(round(0x22ffffff, 14));
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(0, dp(32), 1);
        countLp.setMargins(dp(8), 0, dp(8), 0);
        top.addView(countPill, countLp);

        doneButton = topText("完成", 16, 0x80ffffff, Typeface.BOLD);
        doneButton.setGravity(Gravity.CENTER);
        doneButton.setBackground(round(0x24ffffff, 14));
        doneButton.setOnClickListener(v -> finishWithPhotos());
        top.addView(doneButton, new LinearLayout.LayoutParams(dp(72), dp(42)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(24), dp(16), dp(24), dp(32));
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
        FrameLayout.LayoutParams filmLp = new FrameLayout.LayoutParams(-1, dp(86), Gravity.BOTTOM);
        filmLp.setMargins(0, 0, 0, dp(112));
        root.addView(filmstrip, filmLp);

        refreshState();
    }

    private void buildEmptyHint() {
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, AliIconFont.CAMERA, 0x80ffffff);
        stack.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = topText("插入图片", 15, 0x80ffffff, Typeface.NORMAL);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.setMargins(0, dp(12), 0, 0);
        stack.addView(title, titleLp);
        TextView sub = topText("拍照或从相册选择，照片会交给 AI 放进文章", 13, 0x66ffffff, Typeface.NORMAL);
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
        box.setBackground(round(0x20ffffff, 14));
        box.setClickable(true);
        box.setOnClickListener(v -> action.run());
        ImageView icon = new ImageView(this);
        AliIconFont.apply(icon, iconRes, 0xffffffff);
        box.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView text = topText(label, 12, 0xccffffff, Typeface.NORMAL);
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
        doneButton.setTextColor(photos.isEmpty() ? 0x80ffffff : 0xffffffff);
        doneButton.setBackground(round(photos.isEmpty() ? 0x24ffffff : Theme.RED, 14));
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
            delete.setGravity(Gravity.CENTER);
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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "选择图片"), REQ_LIBRARY);
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
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) throw new IllegalStateException("无法解码图片");
                addPhotoBytes(squareJpeg(bitmap, 86), System.currentTimeMillis());
            } catch (Exception e) {
                toast("照片读取失败：" + e.getMessage());
            }
        });
    }

    private void addBitmap(Bitmap bitmap) {
        io.execute(() -> {
            try {
                addPhotoBytes(squareJpeg(bitmap, 86), System.currentTimeMillis());
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
        Bitmap thumb = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (thumb != null) thumb = Bitmap.createScaledBitmap(thumb, dp(70), dp(70), true);
        Bitmap finalThumb = thumb;
        runOnUiThread(() -> {
            photos.add(new SelectedPhoto(file.getAbsolutePath(), capturedAtMillis, finalThumb));
            refreshState();
        });
    }

    private byte[] squareJpeg(Bitmap bitmap, int quality) {
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int left = Math.max(0, (bitmap.getWidth() - side) / 2);
        int top = Math.max(0, (bitmap.getHeight() - side) / 2);
        Bitmap square = Bitmap.createBitmap(bitmap, left, top, side, side);
        int outSide = Math.min(1080, side);
        Bitmap scaled = Bitmap.createScaledBitmap(square, outSide, outSide, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
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

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : dp(24);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
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
