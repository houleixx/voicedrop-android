package com.baixingai.voicedrop.data;

import android.content.Context;

import com.baixingai.voicedrop.core.ArticleBody;
import com.baixingai.voicedrop.net.Api;
import com.baixingai.voicedrop.net.HttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        StringBuilder index = new StringBuilder("<!doctype html><meta charset=\"utf-8\"><title>VoiceDrop Export</title><h1>VoiceDrop Export</h1><ol>");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            for (Recording rec : recordings) {
                if (rec.uploading) continue;
                String folder = safe(rec.stem()) + "/";
                index.append("<li>").append(escape(rec.rowTitle())).append("<ul>");
                putRemote(zip, rec.audioName, folder + rec.audioName.substring(rec.audioName.lastIndexOf('/') + 1));
                index.append("<li>").append(escape(rec.audioName)).append("</li>");
                putRemote(zip, rec.srtKey(), folder + "transcript.srt");
                ArticleDoc doc = library.fetchDoc(rec);
                if (doc != null) {
                    StringBuilder plain = new StringBuilder();
                    StringBuilder html = new StringBuilder("<!doctype html><meta charset=\"utf-8\">");
                    for (MinedArticle article : doc.articles) {
                        plain.append(article.title).append("\n\n")
                                .append(ArticleBody.stripMarkers(article.body)).append("\n\n---\n\n");
                        html.append("<article><h1>").append(escape(article.title)).append("</h1><pre>")
                                .append(escape(ArticleBody.stripMarkers(article.body))).append("</pre></article>");
                    }
                    put(zip, folder + "articles.txt", plain.toString().getBytes(StandardCharsets.UTF_8));
                    put(zip, folder + "articles.html", html.toString().getBytes(StandardCharsets.UTF_8));
                    index.append("<li>articles.txt / articles.html</li>");
                }
                index.append("</ul></li>");
            }
            index.append("</ol>");
            put(zip, "index.html", index.toString().getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    private void putRemote(ZipOutputStream zip, String key, String entryName) {
        try {
            byte[] body = library.download(key);
            if (body != null && body.length > 0) put(zip, entryName, body);
        } catch (Exception ignored) {
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
