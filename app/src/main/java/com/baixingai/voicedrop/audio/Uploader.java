package com.baixingai.voicedrop.audio;

import android.content.Context;
import android.util.Base64;

import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.Prefs;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class Uploader {
    private final Context context;
    private final AuthStore auth;
    private final Prefs prefs;
    private final HttpClient http;

    public Uploader(Context context, AuthStore auth, Prefs prefs, HttpClient http) {
        this.context = context.getApplicationContext();
        this.auth = auth;
        this.prefs = prefs;
        this.http = http;
    }

    public List<File> pendingFiles() {
        File[] files = AudioRecorder.documentsDir(context).listFiles();
        List<File> out = new ArrayList<>();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File file : files) {
            if (file.isFile() && RecordingName.isRecordingFile(file.getName()) && isUploadable(file)) {
                out.add(file);
            }
        }
        return out;
    }

    public List<String> pendingNames() {
        List<String> names = new ArrayList<>();
        for (File file : pendingFiles()) names.add(file.getName());
        return names;
    }

    public java.util.Map<String, List<String>> pendingTagsByName() {
        java.util.Map<String, List<String>> out = new java.util.HashMap<>();
        for (File file : pendingFiles()) {
            List<String> tags = readTagsSidecar(file);
            if (!tags.isEmpty()) out.put(file.getName(), tags);
        }
        return out;
    }

    public boolean upload(File file) {
        if (!isUploadable(file)) return false;
        String bearer = auth.bearer();
        // Uploading audio immediately starts article mining. Keep every recording
        // behind its persisted photo queue so a retry can never overtake its photos.
        if (!uploadPendingPhotosFor(file, bearer)) return false;
        uploadTagsSidecar(file, bearer);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpClient.Response response = http.putFile(
                        Api.filesBase() + "/upload/" + Api.path(file.getName()),
                        bearer,
                        "audio/mp4",
                        file);
                if (response.ok()) {
                    triggerMine(bearer);
                    if (prefs.deleteLocalAfterUpload()) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    } else {
                        File dir = new File(AudioRecorder.documentsDir(context), "uploaded");
                        if (!dir.exists()) dir.mkdirs();
                        //noinspection ResultOfMethodCallIgnored
                        file.renameTo(new File(dir, file.getName()));
                    }
                    //noinspection ResultOfMethodCallIgnored
                    tagsSidecarFile(file).delete();
                    return true;
                }
                if (response.code >= 400 && response.code < 500) return false;
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(attempt * 1500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public static File tagsSidecarFile(File audio) {
        String name = audio.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        return new File(audio.getParentFile(), stem + ".tags.json");
    }

    public static void writeTagsSidecar(File audio, List<String> tags) {
        if (audio == null || tags == null || tags.isEmpty()) return;
        JSONArray arr = new JSONArray();
        for (String tag : tags) {
            if (tag != null && !tag.trim().isEmpty()) arr.put(tag.trim());
        }
        if (arr.length() == 0) return;
        try {
            java.nio.file.Files.write(tagsSidecarFile(audio).toPath(), arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static List<String> readTagsSidecar(File audio) {
        List<String> out = new ArrayList<>();
        try {
            File file = tagsSidecarFile(audio);
            if (!file.isFile()) return out;
            String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                String tag = arr.optString(i, "").trim();
                if (!tag.isEmpty()) out.add(tag);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void uploadTagsSidecar(File audio, String bearer) {
        File sidecar = tagsSidecarFile(audio);
        if (!sidecar.isFile()) return;
        String name = audio.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        try {
            HttpClient.Response response = http.putFile(
                    Api.filesBase() + "/upload/articles/" + Api.path(stem + ".tags"),
                    bearer,
                    "application/json",
                    sidecar);
            if (response.ok()) {
                //noinspection ResultOfMethodCallIgnored
                sidecar.delete();
            }
        } catch (Exception ignored) {
        }
    }

    private void triggerMine(String bearer) {
        try {
            http.postJson(Api.filesBase() + "/mine", bearer, new byte[0]);
        } catch (Exception ignored) {
        }
    }

    public void drainPending() {
        for (File file : pendingFiles()) {
            upload(file);
        }
    }

    public boolean stagePhoto(String key, byte[] bytes) {
        if (key == null || !key.startsWith("photos/") || bytes == null || bytes.length == 0) return false;
        File dir = pendingPhotoDir();
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        File target = pendingPhotoFile(key);
        if (target.isFile() && target.length() > 0) return true;
        File temp = new File(dir, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(bytes);
            out.getFD().sync();
            if (target.isFile() && !target.delete()) return false;
            return temp.renameTo(target);
        } catch (Exception ignored) {
            return false;
        } finally {
            if (temp.isFile()) temp.delete();
        }
    }

    public boolean stagePhotos(Map<String, byte[]> photos) {
        if (photos == null || photos.isEmpty()) return true;
        String sessionTs = null;
        JSONArray expected = new JSONArray();
        for (Map.Entry<String, byte[]> entry : photos.entrySet()) {
            String currentSession = photoSession(entry.getKey());
            if (currentSession == null || (sessionTs != null && !sessionTs.equals(currentSession))) return false;
            sessionTs = currentSession;
            expected.put(entry.getKey());
        }
        File gate = pendingPhotoGateFile(sessionTs);
        if (!writePhotoGate(gate, expected)) return false;
        for (Map.Entry<String, byte[]> entry : photos.entrySet()) {
            if (!stagePhoto(entry.getKey(), entry.getValue())) return false;
        }
        // If deletion is interrupted, uploadPendingPhotosFor reconciles the gate
        // against the fully staged files before allowing audio through.
        //noinspection ResultOfMethodCallIgnored
        gate.delete();
        return !gate.isFile();
    }

    private boolean uploadPendingPhotosFor(File audio, String bearer) {
        RecordingName.Parsed parsed = audio == null ? null : RecordingName.parse(audio.getName());
        if (parsed == null || parsed.sessionTs == null) return true;
        if (!photoStageComplete(parsed.sessionTs)) return false;
        String prefix = "photos/" + parsed.sessionTs + "/";
        File[] pending = pendingPhotoDir().listFiles(file -> file.isFile() && file.getName().endsWith(".jpg"));
        if (pending == null || pending.length == 0) return true;
        Arrays.sort(pending, (a, b) -> a.getName().compareTo(b.getName()));
        boolean complete = true;
        for (File photo : pending) {
            String key = pendingPhotoKey(photo);
            if (key == null || !key.startsWith(prefix)) continue;
            try {
                HttpClient.Response response = http.putFile(
                        Api.filesBase() + "/upload/" + Api.path(key),
                        bearer,
                        "image/jpeg",
                        photo);
                if (response.ok()) {
                    //noinspection ResultOfMethodCallIgnored
                    photo.delete();
                } else {
                    complete = false;
                }
            } catch (Exception ignored) {
                complete = false;
            }
        }
        return complete && !hasPendingPhotos(prefix);
    }

    private boolean hasPendingPhotos(String keyPrefix) {
        File[] pending = pendingPhotoDir().listFiles(file -> {
            if (!file.isFile() || !file.getName().endsWith(".jpg")) return false;
            String key = pendingPhotoKey(file);
            return key != null && key.startsWith(keyPrefix);
        });
        return pending != null && pending.length > 0;
    }

    private File pendingPhotoDir() {
        return new File(AudioRecorder.documentsDir(context), "pending-photos");
    }

    private File pendingPhotoFile(String key) {
        String encoded = Base64.encodeToString(
                key.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return new File(pendingPhotoDir(), encoded + ".jpg");
    }

    private File pendingPhotoGateFile(String sessionTs) {
        String encoded = Base64.encodeToString(
                sessionTs.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return new File(pendingPhotoDir(), "gate-" + encoded + ".json");
    }

    private boolean writePhotoGate(File gate, JSONArray expected) {
        File dir = pendingPhotoDir();
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        File temp = new File(dir, gate.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(expected.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            if (gate.isFile() && !gate.delete()) return false;
            return temp.renameTo(gate);
        } catch (Exception ignored) {
            return false;
        } finally {
            if (temp.isFile()) temp.delete();
        }
    }

    private boolean photoStageComplete(String sessionTs) {
        File gate = pendingPhotoGateFile(sessionTs);
        if (!gate.isFile()) return true;
        try (FileInputStream in = new FileInputStream(gate)) {
            JSONArray expected = new JSONArray(new String(HttpClient.readAll(in), StandardCharsets.UTF_8));
            for (int index = 0; index < expected.length(); index++) {
                String key = expected.optString(index, "");
                if (!sessionTs.equals(photoSession(key)) || !pendingPhotoFile(key).isFile()) return false;
            }
            //noinspection ResultOfMethodCallIgnored
            gate.delete();
            return !gate.isFile();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String photoSession(String key) {
        if (key == null || !key.startsWith("photos/")) return null;
        int end = key.indexOf('/', "photos/".length());
        if (end <= "photos/".length()) return null;
        return key.substring("photos/".length(), end);
    }

    private static String pendingPhotoKey(File file) {
        String name = file == null ? "" : file.getName();
        if (!name.endsWith(".jpg")) return null;
        try {
            byte[] decoded = Base64.decode(
                    name.substring(0, name.length() - 4),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isUploadable(File file) {
        if (file.length() <= 1024) return false;
        byte[] moov = new byte[]{'m', 'o', 'o', 'v'};
        try (FileInputStream in = new FileInputStream(file)) {
            int matched = 0;
            int b;
            while ((b = in.read()) >= 0) {
                if ((byte) b == moov[matched]) {
                    matched++;
                    if (matched == moov.length) return true;
                } else {
                    matched = 0;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
