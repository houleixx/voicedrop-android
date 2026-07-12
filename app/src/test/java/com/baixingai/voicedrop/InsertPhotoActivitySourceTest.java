package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

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
