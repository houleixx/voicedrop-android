package com.baixingai.voicedrop.audio;

import android.content.Context;

import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.Prefs;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import java.io.File;
import java.io.FileInputStream;
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

    public boolean upload(File file) {
        if (!isUploadable(file)) return false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpClient.Response response = http.putFile(
                        Api.filesBase() + "/upload/" + Api.path(file.getName()),
                        auth.bearer(),
                        "audio/mp4",
                        file);
                if (response.ok()) {
                    if (prefs.deleteLocalAfterUpload()) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    } else {
                        File dir = new File(AudioRecorder.documentsDir(context), "uploaded");
                        if (!dir.exists()) dir.mkdirs();
                        //noinspection ResultOfMethodCallIgnored
                        file.renameTo(new File(dir, file.getName()));
                    }
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
