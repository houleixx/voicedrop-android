package com.baixingai.voicedrop.data;

import android.content.Context;

import com.baixingai.voicedrop.net.HttpClient;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ExportManager {
    private final Context context;
    private final AuthStore auth;
    private final HttpClient http;
    private final LibraryStore library;

    public ExportManager(Context context, AuthStore auth, HttpClient http, LibraryStore library) {
        this.context = context.getApplicationContext();
        this.auth = auth;
        this.http = http;
        this.library = library;
    }

    public File exportAll(List<Recording> recordings) throws Exception {
        File dir = new File(context.getCacheDir(), "exports");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, "VoiceDrop-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip");
        ExportArchiveWriter.write(out, recordings, new ExportArchiveWriter.Source() {
            @Override
            public byte[] download(String key) throws Exception {
                return library.download(key);
            }

            @Override
            public ArticleDoc fetchDoc(Recording recording) {
                return library.fetchDoc(recording);
            }
        });
        return out;
    }
}
