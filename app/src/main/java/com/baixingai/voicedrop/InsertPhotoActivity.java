package com.baixingai.voicedrop;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.baixingai.voicedrop.core.ArticlePhotoInsert;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.AliIconFont;
import com.baixingai.voicedrop.ui.RoundedImageView;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.baixingai.voicedrop.ui.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InsertPhotoActivity extends Activity {
    public static final String EXTRA_PHOTO_PATHS = "photoPaths";
    public static final String EXTRA_CAPTURE_TIMES = "captureTimes";
    private static final int REQ_CAMERA_PERMISSION = 31;
    private static final int REQ_LIBRARY = 32;

    private final List<SelectedPhoto> photos = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private FrameLayout pickerContent;
    private TextView countPill;
    private TextView doneButton;
    private FrameLayout emptyHint;
    private HorizontalScrollView filmstrip;
    private LinearLayout filmRow;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private FrameLayout cameraOverlay;
    private TextureView cameraPreview;
    private TextView cameraCount;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraSession;
    private CaptureRequest.Builder previewRequest;
    private Surface cameraPreviewSurface;
    private Size previewSize;
    private String cameraId;
    private boolean cameraOpening;
    private boolean takingPhoto;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restorePickerSystemBars();
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        cameraThread = new HandlerThread("voicedrop-camera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        render();
    }

    @Override protected void onDestroy() {
        closeCameraOverlay();
        super.onDestroy();
        cameraThread.quitSafely();
        io.shutdownNow();
    }

    @Override protected void onResume() {
        super.onResume();
        if (cameraOverlay != null) {
            SystemBarDefaults.applyCameraImmersive(getWindow());
            if (cameraPreview != null && cameraPreview.isAvailable()) startCamera();
        }
    }

    @Override protected void onPause() {
        closeCameraDevice();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (cameraOverlay != null) {
            closeCameraOverlay();
            return;
        }
        super.onBackPressed();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && cameraOverlay != null) {
            SystemBarDefaults.applyCameraImmersive(getWindow());
        }
    }

    private void render() {
        root.removeAllViews();
        pickerContent = new FrameLayout(this);
        root.addView(pickerContent, match());
        SystemBarDefaults.applyTopAndBottomInsets(pickerContent, 0, 0, 0, 0);

        emptyHint = new FrameLayout(this);
        pickerContent.addView(emptyHint, match());
        buildEmptyHint();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(12), dp(18), dp(8));
        pickerContent.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

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
        pickerContent.addView(bottom, new FrameLayout.LayoutParams(-1, dp(112), Gravity.BOTTOM));

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
        pickerContent.addView(filmstrip, filmLp);

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
            ImageView image = new RoundedImageView(this);
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
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
        showCameraOverlay();
    }

    private void showCameraOverlay() {
        if (cameraOverlay != null) return;
        cameraOverlay = new FrameLayout(this);
        cameraOverlay.setBackgroundColor(Color.BLACK);

        cameraPreview = new TextureView(this);
        cameraPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                startCamera();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeCameraDevice();
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
        cameraOverlay.addView(cameraPreview, match());

        FrameLayout cameraTopControls = new FrameLayout(this);
        cameraOverlay.addView(cameraTopControls,
                new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        SystemBarDefaults.applyTopInsetsIgnoringVisibility(cameraTopControls, 0, 0, 0, 0);

        ImageView close = new ImageView(this);
        AliIconFont.apply(close, AliIconFont.BACK, Color.WHITE);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setContentDescription("返回");
        close.setBackground(round(0x66000000, 24));
        close.setOnClickListener(v -> closeCameraOverlay());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.LEFT);
        closeLp.setMargins(dp(18), dp(18), 0, 0);
        cameraTopControls.addView(close, closeLp);

        cameraCount = topText("", 14, Color.WHITE, Typeface.BOLD);
        cameraCount.setGravity(Gravity.CENTER);
        cameraCount.setBackground(round(0x66000000, 18));
        FrameLayout.LayoutParams countLp = new FrameLayout.LayoutParams(dp(110), dp(36), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        countLp.setMargins(0, dp(24), 0, 0);
        cameraTopControls.addView(cameraCount, countLp);

        TextView returnToPicker = topText("完成", 16, Color.WHITE, Typeface.BOLD);
        returnToPicker.setGravity(Gravity.CENTER);
        returnToPicker.setBackground(round(0x66000000, 18));
        returnToPicker.setOnClickListener(v -> closeCameraOverlay());
        FrameLayout.LayoutParams doneLp = new FrameLayout.LayoutParams(dp(72), dp(42), Gravity.TOP | Gravity.RIGHT);
        doneLp.setMargins(0, dp(20), dp(18), 0);
        cameraTopControls.addView(returnToPicker, doneLp);

        FrameLayout shutter = new FrameLayout(this);
        shutter.setBackground(roundStroke(Color.WHITE, 38, 0x99ffffff, 5));
        View shutterCore = new View(this);
        shutterCore.setBackground(round(Color.WHITE, 28));
        shutter.addView(shutterCore, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        shutter.setOnClickListener(v -> captureStillPhoto());
        FrameLayout.LayoutParams shutterLp = new FrameLayout.LayoutParams(dp(76), dp(76), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        shutterLp.setMargins(0, 0, 0, dp(28));
        cameraOverlay.addView(shutter, shutterLp);

        root.addView(cameraOverlay, match());
        SystemBarDefaults.applyCameraImmersive(getWindow());
        refreshCameraCount();
        if (cameraPreview.isAvailable()) startCamera();
    }

    private void refreshCameraCount() {
        if (cameraCount != null) cameraCount.setText(photos.isEmpty() ? "拍摄照片" : "已拍 " + photos.size() + " 张");
    }

    @SuppressLint("MissingPermission")
    private void startCamera() {
        if (cameraOverlay == null || cameraPreview == null || !cameraPreview.isAvailable()
                || cameraDevice != null || cameraOpening) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        cameraOpening = true;
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            cameraId = selectBackCamera(manager);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    cameraOpening = false;
                    if (cameraOverlay == null) {
                        camera.close();
                        return;
                    }
                    cameraDevice = camera;
                    createPreviewSession();
                }

                @Override public void onDisconnected(CameraDevice camera) {
                    cameraOpening = false;
                    camera.close();
                    if (cameraDevice == camera) cameraDevice = null;
                }

                @Override public void onError(CameraDevice camera, int error) {
                    cameraOpening = false;
                    camera.close();
                    if (cameraDevice == camera) cameraDevice = null;
                    toast("相机启动失败：" + error);
                    runOnUiThread(() -> closeCameraOverlay());
                }
            }, cameraHandler);
        } catch (Exception error) {
            cameraOpening = false;
            toast("无法打开相机：" + error.getMessage());
            closeCameraOverlay();
        }
    }

    private String selectBackCamera(CameraManager manager) throws CameraAccessException {
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics info = manager.getCameraCharacteristics(id);
            if (fallback == null) fallback = id;
            Integer facing = info.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                configureCameraSizes(info);
                return id;
            }
        }
        if (fallback == null) throw new IllegalStateException("没有可用相机");
        configureCameraSizes(manager.getCameraCharacteristics(fallback));
        return fallback;
    }

    private void configureCameraSizes(CameraCharacteristics info) {
        StreamConfigurationMap map = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) throw new IllegalStateException("相机不支持预览");
        Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
        if (previewSizes == null || previewSizes.length == 0) {
            throw new IllegalStateException("相机不支持预览");
        }
        previewSize = Arrays.stream(previewSizes)
                .filter(size -> size.getWidth() <= 1920 && size.getHeight() <= 1920)
                .max(Comparator.comparingLong(size ->
                        (long) size.getWidth() * size.getHeight()))
                .orElse(previewSizes[0]);
    }

    private void createPreviewSession() {
        CameraDevice device = cameraDevice;
        TextureView preview = cameraPreview;
        if (device == null || preview == null || !preview.isAvailable()) return;
        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            if (cameraPreviewSurface != null) cameraPreviewSurface.release();
            cameraPreviewSurface = new Surface(texture);
            previewRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequest.addTarget(cameraPreviewSurface);
            previewRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            device.createCaptureSession(Arrays.asList(cameraPreviewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null || cameraOverlay == null) {
                                session.close();
                                return;
                            }
                            cameraSession = session;
                            try {
                                session.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
                            } catch (CameraAccessException error) {
                                toast("相机预览失败：" + error.getMessage());
                            }
                        }

                        @Override public void onConfigureFailed(CameraCaptureSession session) {
                            toast("相机预览配置失败");
                        }
                    }, cameraHandler);
        } catch (Exception error) {
            toast("相机预览失败：" + error.getMessage());
        }
    }

    private void captureStillPhoto() {
        TextureView preview = cameraPreview;
        if (preview == null || !preview.isAvailable() || takingPhoto) return;
        Bitmap frame = preview.getBitmap();
        if (frame == null) {
            toast("相机画面尚未准备好，请重试");
            return;
        }

        takingPhoto = true;
        io.execute(() -> {
            try {
                addPhotoBytes(
                        ArticlePhotoInsert.squareJpeg(frame, 1080, 86),
                        System.currentTimeMillis());
            } catch (Exception error) {
                toast("照片保存失败：" + error.getMessage());
            } finally {
                frame.recycle();
                runOnUiThread(() -> takingPhoto = false);
            }
        });
    }

    private void closeCameraOverlay() {
        closeCameraDevice();
        if (cameraOverlay != null && root != null) root.removeView(cameraOverlay);
        cameraOverlay = null;
        cameraPreview = null;
        cameraCount = null;
        restorePickerSystemBars();
        refreshState();
    }

    private void restorePickerSystemBars() {
        SystemBarDefaults.applyLightEdgeToEdgeVisible(getWindow());
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.BG));
        getWindow().setStatusBarColor(Theme.BG);
    }

    private void closeCameraDevice() {
        cameraOpening = false;
        takingPhoto = false;
        if (cameraSession != null) {
            cameraSession.close();
            cameraSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (cameraPreviewSurface != null) {
            cameraPreviewSurface.release();
            cameraPreviewSurface = null;
        }
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
        if (bytes == null || bytes.length == 0) throw new IllegalStateException("照片编码失败");
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
            refreshCameraCount();
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
        if (requestCode == REQ_CAMERA_PERMISSION) openCamera();
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
