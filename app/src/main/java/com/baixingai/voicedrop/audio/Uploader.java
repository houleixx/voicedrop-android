package com.baixingai.voicedrop.audio;

import android.content.Context;

import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.Prefs;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        uploadTagsSidecar(file);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpClient.Response response = http.putFile(
                        Api.filesBase() + "/upload/" + Api.path(file.getName()),
                        auth.bearer(),
                        "audio/mp4",
                        file);
                if (response.ok()) {
                    triggerMine();
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

    private void uploadTagsSidecar(File audio) {
        File sidecar = tagsSidecarFile(audio);
        if (!sidecar.isFile()) return;
        String name = audio.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        try {
            HttpClient.Response response = http.putFile(
                    Api.filesBase() + "/upload/articles/" + Api.path(stem + ".tags"),
                    auth.bearer(),
                    "application/json",
                    sidecar);
            if (response.ok()) {
                //noinspection ResultOfMethodCallIgnored
                sidecar.delete();
            }
        } catch (Exception ignored) {
        }
    }

    private void triggerMine() {
        try {
            http.postJson(Api.filesBase() + "/mine", auth.bearer(), new byte[0]);
        } catch (Exception ignored) {
        }
    }

    public void drainPending() {
        for (File file : pendingFiles()) {
            upload(file);
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
