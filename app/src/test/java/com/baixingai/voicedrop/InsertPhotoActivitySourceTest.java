package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class InsertPhotoActivitySourceTest {
    @Test
    public void libraryButtonPrefersPhotoPickerOrAlbumBeforeFilePicker() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/InsertPhotoActivity.java");

        int photoPicker = source.indexOf("MediaStore.ACTION_PICK_IMAGES");
        int albumPicker = source.indexOf("new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)");
        int filePicker = source.indexOf("new Intent(Intent.ACTION_GET_CONTENT)");

        assertTrue(photoPicker >= 0);
        assertTrue(albumPicker > photoPicker);
        assertTrue(filePicker > albumPicker);
    }

    @Test
    public void galleryImportsKeepTheirOriginalAspectRatio() throws Exception {
        String picker = readSource("src/main/java/com/baixingai/voicedrop/InsertPhotoActivity.java");
        String photo = readSource("src/main/java/com/baixingai/voicedrop/core/ArticlePhotoInsert.java");

        assertTrue(photo.contains("fitJpeg"));
        assertTrue(methodBody(picker, "private void addUri").contains("ArticlePhotoInsert.fitJpeg"));
        assertTrue(methodBody(picker, "private void addBitmap").contains("ArticlePhotoInsert.squareJpeg"));
    }

    @Test
    public void cameraStaysInAppSoBackgroundRecordingIsNotSilenced() throws Exception {
        String picker = readSource("src/main/java/com/baixingai/voicedrop/InsertPhotoActivity.java");
        String openCamera = methodBody(picker, "private void openCamera");
        String startCamera = methodBody(picker, "private void startCamera");
        String preview = methodBody(picker, "private void createPreviewSession");
        String capture = methodBody(picker, "private void captureStillPhoto");

        assertTrue(openCamera.contains("showCameraOverlay()"));
        assertTrue(startCamera.contains("CameraManager"));
        assertTrue(startCamera.contains("manager.openCamera"));
        assertTrue(preview.contains("CameraDevice.TEMPLATE_PREVIEW"));
        assertTrue(capture.contains("preview.getBitmap()"));
        assertTrue(capture.contains("ArticlePhotoInsert.squareJpeg(frame, 1080, 86)"));
        assertFalse(capture.contains("CameraDevice.TEMPLATE_STILL_CAPTURE"));
        assertFalse(picker.contains("ImageReader.newInstance"));
        assertTrue(picker.contains("new TextureView(this)"));
        assertFalse(openCamera.contains("startActivityForResult"));
        assertFalse(picker.contains("MediaStore.ACTION_IMAGE_CAPTURE"));
    }

    @Test
    public void cameraBackIconIsCenteredInsideItsTouchTarget() throws Exception {
        String picker = readSource("src/main/java/com/baixingai/voicedrop/InsertPhotoActivity.java");
        String overlay = methodBody(picker, "private void showCameraOverlay");

        assertTrue(overlay.contains("ImageView close = new ImageView(this)"));
        assertTrue(overlay.contains("AliIconFont.apply(close, AliIconFont.BACK, Color.WHITE)"));
        assertTrue(overlay.contains("close.setScaleType(ImageView.ScaleType.CENTER)"));
        assertFalse(overlay.contains("topText(\"‹\""));
    }

    @Test
    public void capturedPhotoThumbnailIsClippedToRoundedCorners() throws Exception {
        String picker = readSource("src/main/java/com/baixingai/voicedrop/InsertPhotoActivity.java");
        String filmstrip = methodBody(picker, "private void rebuildFilmstrip");

        assertTrue(filmstrip.contains("new RoundedImageView(this)"));
        assertTrue(filmstrip.contains("ImageView.ScaleType.CENTER_CROP"));
    }

    @Test
    public void inAppCameraHidesBothSystemBarsAndRestoresThemOnClose() throws Exception {
        String picker = readSource("src/main/java/com/baixingai/voicedrop/InsertPhotoActivity.java");
        String bars = readSource("src/main/java/com/baixingai/voicedrop/ui/SystemBarDefaults.java");
        String overlay = methodBody(picker, "private void showCameraOverlay");
        String close = methodBody(picker, "private void closeCameraOverlay");
        String restore = methodBody(picker, "private void restorePickerSystemBars");
        String lightBars = methodBody(bars, "public static void applyLightActivity");
        String visibleEdgeBars = methodBody(bars, "public static void applyLightEdgeToEdgeVisible");
        String cameraBars = methodBody(bars, "public static void applyCameraImmersive");
        String cutout = methodBody(bars, "private static void allowDisplayCutout");
        String safeTop = methodBody(bars, "public static void applyTopInsetsIgnoringVisibility");
        String relayout = methodBody(bars, "private static void relayoutSystemBars");

        assertTrue(overlay.contains("SystemBarDefaults.applyCameraImmersive(getWindow())"));
        assertTrue(close.contains("restorePickerSystemBars()"));
        assertTrue(bars.contains("public static void applyCameraImmersive"));
        assertTrue(bars.contains("controller.hide(WindowInsetsCompat.Type.systemBars())"));
        assertTrue(bars.contains("controller.show(WindowInsetsCompat.Type.systemBars())"));
        assertTrue(cameraBars.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"));
        assertTrue(lightBars.contains("relayoutSystemBars(window)"));
        assertTrue(cameraBars.contains("relayoutSystemBars(window)"));
        assertTrue(relayout.contains("ViewCompat.requestApplyInsets(window.getDecorView())"));
        assertTrue(relayout.contains("window.getDecorView().requestLayout()"));
        assertTrue(restore.contains("new ColorDrawable(Theme.BG)"));
        assertTrue(restore.contains("applyLightEdgeToEdgeVisible(getWindow())"));
        assertTrue(visibleEdgeBars.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"));
        assertTrue(visibleEdgeBars.contains("allowDisplayCutout(window)"));
        assertTrue(visibleEdgeBars.contains("controller.setAppearanceLightStatusBars(true)"));
        assertTrue(visibleEdgeBars.contains("controller.show(WindowInsetsCompat.Type.systemBars())"));
        assertTrue(cameraBars.contains("allowDisplayCutout(window)"));
        assertTrue(cutout.contains("LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS"));
        assertTrue(cutout.contains("LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES"));
        assertTrue(cutout.contains("window.setAttributes(attributes)"));
        assertTrue(overlay.contains("FrameLayout cameraTopControls = new FrameLayout(this)"));
        assertTrue(overlay.contains("SystemBarDefaults.applyTopInsetsIgnoringVisibility(cameraTopControls"));
        assertTrue(overlay.contains("cameraTopControls.addView(close"));
        assertTrue(overlay.contains("cameraTopControls.addView(cameraCount"));
        assertTrue(overlay.contains("cameraTopControls.addView(returnToPicker"));
        assertTrue(safeTop.contains("getInsetsIgnoringVisibility"));
        assertTrue(picker.contains("private FrameLayout pickerContent;"));
        assertTrue(picker.contains("SystemBarDefaults.applyTopAndBottomInsets(pickerContent"));
        assertTrue(picker.contains("pickerContent.addView(emptyHint"));
        assertTrue(picker.contains("root.addView(cameraOverlay, match())"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("Unclosed method: " + signature);
    }
}
