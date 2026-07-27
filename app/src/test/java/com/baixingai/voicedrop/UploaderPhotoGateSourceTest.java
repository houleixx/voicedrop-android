package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class UploaderPhotoGateSourceTest {
    @Test
    public void persistedPhotosGateEveryAudioUploadAndPendingDrain() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/audio/Uploader.java");
        String upload = methodBody(source, "public boolean upload(File file)");
        String drain = methodBody(source, "public void drainPending()");
        String photoUpload = methodBody(source, "private boolean uploadPendingPhotosFor(File audio, String bearer)");

        assertTrue(source.contains("new File(AudioRecorder.documentsDir(context), \"pending-photos\")"));
        assertTrue(source.contains("public boolean stagePhoto(String key, byte[] bytes)"));
        assertTrue(source.contains("public boolean stagePhotos(Map<String, byte[]> photos)"));
        assertTrue(source.contains("if (!photoStageComplete(parsed.sessionTs)) return false"));
        assertTrue(upload.contains("String bearer = auth.bearer()"));
        assertTrue(upload.indexOf("uploadPendingPhotosFor(file, bearer)") < upload.indexOf("uploadTagsSidecar(file, bearer)"));
        assertTrue(upload.indexOf("uploadPendingPhotosFor(file, bearer)") < upload.indexOf("http.putFile("));
        assertTrue(!photoUpload.contains("auth.bearer()"));
        assertTrue(photoUpload.contains("bearer,"));
        assertTrue(photoUpload.contains("String prefix = \"photos/\" + parsed.sessionTs + \"/\""));
        assertTrue(photoUpload.contains("photo.delete()"));
        assertTrue(photoUpload.contains("return complete && !hasPendingPhotos(prefix)"));
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
