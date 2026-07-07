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

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
