package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UploaderPhotoGateSourceTest {
    @Test
    public void persistedPhotosUploadIndependentlyWithoutGatingAudio() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/Uploader.java");
        String upload = methodBody(source, "public boolean upload(File file)");
        String drain = methodBody(source, "public void drainPending()");
        String photoDrain = methodBody(source, "private void drainAllPendingPhotos(String bearer)");

        assertTrue(source.contains("new File(AudioRecorder.documentsDir(context), \"pending-photos\")"));
        assertTrue(source.contains("public boolean stagePhoto(String key, byte[] bytes)"));
        assertTrue(source.contains("public boolean stagePhotos(Map<String, byte[]> photos)"));
        assertTrue(upload.contains("String bearer = auth.bearer()"));
        assertTrue(upload.contains("schedulePendingPhotoDrain(bearer)"));
        assertFalse(upload.contains("uploadPendingPhotosFor(file, bearer)"));
        assertTrue(source.contains("newFixedThreadPool(3)"));
        assertTrue(photoDrain.contains("PHOTO_UPLOADS.submit"));
        assertTrue(photoDrain.contains("upload.get()"));
        assertTrue(source.contains("photo.delete()"));
        assertTrue(drain.contains("schedulePendingPhotoDrain(auth.bearer())"));
        assertTrue(drain.contains("schedulePendingTagDrain(auth.bearer())"));
        assertTrue(source.contains("endsWith(\".tags.json\")"));
        assertTrue(drain.contains("upload(file)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new IllegalArgumentException("Missing " + signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}') {
                depth--;
                if (depth == 0) return source.substring(brace, i + 1);
            }
        }
        throw new IllegalArgumentException("Unclosed method " + signature);
    }
}
