package com.baixingai.voicedrop.data;

import com.baixingai.voicedrop.core.ArticleBody;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ExportArchiveWriter {
    private ExportArchiveWriter() {}

    public interface Source {
        byte[] download(String key) throws Exception;
        ArticleDoc fetchDoc(Recording recording) throws Exception;
    }

    public static void write(File out, List<Recording> recordings, Source source) throws Exception {
        if (recordings == null || recordings.isEmpty()) {
            throw new IllegalArgumentException("没有录音可以导出");
        }

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            StringBuilder cards = new StringBuilder();
            int articleCount = 0;
            for (Recording rec : recordings) {
                if (rec == null || rec.uploading) continue;
                ArticleDoc doc = rec.hasArticles ? source.fetchDoc(rec) : null;
                if (doc != null) articleCount++;

                putRemote(zip, source, rec.audioName, "audio/" + rec.stem() + ".m4a");
                if (doc != null) {
                    Set<String> downloadedPhotos = putArticlePhotos(zip, source, doc);
                    put(zip, "recordings/" + rec.stem() + ".html", recordingHtml(rec, doc, downloadedPhotos));
                    putRemote(zip, source, rec.srtKey(), "recordings/" + rec.stem() + ".srt");
                }
                cards.append(indexCard(rec, doc));
            }
            put(zip, "index.html", indexHtml(recordings.size(), articleCount, cards.toString()));
        }
    }

    private static Set<String> putArticlePhotos(ZipOutputStream zip, Source source, ArticleDoc doc) {
        Set<String> downloaded = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (MinedArticle article : doc.articles) {
            for (ArticleBody.Segment segment : ArticleBody.segments(article.body)) {
                if (segment.type != ArticleBody.Segment.Type.PHOTO) continue;
                String key = ArticleBody.resolvePhotoKey(segment.value, doc.photos);
                if (key == null || !seen.add(key)) continue;
                try {
                    byte[] data = source.download(key);
                    String entry = safeEntryName(key);
                    if (data != null && data.length > 0 && entry != null) {
                        put(zip, entry, data);
                        downloaded.add(key);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return downloaded;
    }

    private static void putRemote(ZipOutputStream zip, Source source, String key, String entryName) {
        try {
            byte[] body = source.download(key);
            if (body != null && body.length > 0) put(zip, entryName, body);
        } catch (Exception ignored) {
        }
    }

    private static byte[] indexHtml(int total, int articleCount, String cards) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        String html = "<!DOCTYPE html><html lang=\"zh\"><head>"
                + "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>VoiceDrop 导出</title><style>"
                + "*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}"
                + "body{background:#FAF6EF;color:#2A2521;font-family:-apple-system,\"PingFang SC\",\"Helvetica Neue\",sans-serif;min-height:100vh}"
                + ".hdr{padding:18px 0 14px;border-bottom:1px solid #ECE3D5;background:#FAF6EF;position:sticky;top:0;z-index:9}"
                + ".hi{max-width:680px;margin:0 auto;padding:0 24px;display:flex;justify-content:space-between;align-items:center}"
                + ".lw{font-size:16px;font-weight:700}.lw em{color:#D8593B;font-style:normal}.ed{font-size:13px;color:#A89E8E}"
                + ".stats{max-width:680px;margin:0 auto;padding:16px 24px 4px;display:flex;gap:28px}.sn{font-size:22px;font-weight:700;line-height:1}.sl{font-size:12px;color:#A89E8E;margin-top:3px;letter-spacing:.4px}"
                + ".wrap{max-width:680px;margin:0 auto;padding:14px 24px 64px}.card{background:#fff;border-radius:5px;border:1px solid #ECE3D5;padding:16px 18px;margin-bottom:10px}"
                + ".card-top{display:flex;align-items:flex-start;gap:12px}.tile{width:40px;height:40px;border-radius:8px;background:#F6EFE3;flex-shrink:0;display:flex;align-items:center;justify-content:center}"
                + ".ci{flex:1;min-width:0}.ct{font-size:16px;font-weight:600;line-height:1.45}.cm{font-size:13px;color:#A89E8E;margin-top:3px}.cp{font-size:14px;color:#4A443C;line-height:1.6;margin-top:10px}"
                + ".row{margin-top:12px;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px}.badge{font-size:11.5px;font-weight:600;padding:2px 9px;border-radius:4px;letter-spacing:.4px}"
                + ".badge.done{background:#EAF1EC;color:#3C5A47}.badge.empty{background:#F1ECE3;color:#8A8175}.links{display:flex;gap:8px;flex-wrap:wrap}"
                + ".btn{font-size:13px;font-weight:500;color:#D8593B;text-decoration:none;padding:5px 14px;border:1px solid #F6E4DC;border-radius:8px;background:#FDF8F5}.btn.audio{color:#6E8576;border-color:#D5E3D9;background:#F4F8F5}"
                + "</style></head><body><header class=\"hdr\"><div class=\"hi\"><div class=\"lw\">Voice<em>Drop</em> 口述</div><span class=\"ed\">"
                + h(date) + "</span></div></header><div class=\"stats\"><div><div class=\"sn\">"
                + total + "</div><div class=\"sl\">条录音</div></div><div><div class=\"sn\">"
                + articleCount + "</div><div class=\"sl\">篇文章</div></div></div><div class=\"wrap\">"
                + cards + "</div></body></html>";
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private static String indexCard(Recording rec, ArticleDoc doc) {
        String title = doc != null && !doc.articles.isEmpty() ? doc.articles.get(0).title : rec.rowTitle();
        String preview = "";
        if (doc != null && !doc.articles.isEmpty()) {
            preview = ArticleBody.stripMarkers(doc.articles.get(0).body).replace("\n", " ");
            if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
        }
        String badge = doc != null
                ? "<span class=\"badge done\">已成文</span>"
                : "<span class=\"badge empty\">无文章</span>";
        String read = doc != null ? "<a class=\"btn\" href=\"recordings/" + h(rec.stem()) + ".html\">读文章</a>" : "";
        return "<div class=\"card\"><div class=\"card-top\"><div class=\"tile\">VD</div><div class=\"ci\"><div class=\"ct\">"
                + h(title) + "</div><div class=\"cm\">" + h(rec.rowTitle()) + "</div></div></div>"
                + (preview.isEmpty() ? "" : "<div class=\"cp\">" + h(preview) + "</div>")
                + "<div class=\"row\">" + badge + "<div class=\"links\">" + read
                + "<a class=\"btn audio\" href=\"audio/" + h(rec.stem()) + ".m4a\">听录音</a></div></div></div>\n";
    }

    private static byte[] recordingHtml(Recording rec, ArticleDoc doc, Set<String> downloadedPhotos) {
        String title = doc.articles.isEmpty() ? rec.rowTitle() : doc.articles.get(0).title;
        StringBuilder sections = new StringBuilder();
        for (int i = 0; i < doc.articles.size(); i++) {
            MinedArticle article = doc.articles.get(i);
            if (i > 0) sections.append("<hr>\n");
            sections.append("<h1>").append(h(article.title)).append("</h1>\n<div class=\"body\">");
            for (ArticleBody.Segment segment : ArticleBody.segments(article.body)) {
                if (segment.type == ArticleBody.Segment.Type.TEXT) {
                    for (String paragraph : segment.value.split("\\n\\n")) {
                        String text = paragraph.trim().replace("\n", " ");
                        if (!text.isEmpty()) sections.append("<p>").append(h(text)).append("</p>\n");
                    }
                } else {
                    String key = ArticleBody.resolvePhotoKey(segment.value, doc.photos);
                    if (key != null && downloadedPhotos.contains(key)) {
                        sections.append("<img class=\"photo\" src=\"../").append(h(key)).append("\" loading=\"lazy\">\n");
                    }
                }
            }
            sections.append("</div>\n");
        }
        String transcript = doc.transcript == null || doc.transcript.isEmpty()
                ? ""
                : "<details class=\"xc\"><summary>原始转录</summary><p class=\"xcp\">" + h(doc.transcript) + "</p></details>";
        String html = "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + h(title) + "</title><style>"
                + "*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}body{background:#F0EDE7;color:#2B2823;font-family:-apple-system,\"PingFang SC\",\"Helvetica Neue\",sans-serif;min-height:100vh}"
                + ".nav{max-width:680px;margin:0 auto;padding:16px 24px 0}.back{font-size:14px;font-weight:500;color:#D8593B;text-decoration:none}.wrap{max-width:680px;margin:0 auto;padding:20px 24px 64px}"
                + ".meta{font-size:13px;color:#9A9387;margin-bottom:20px}audio{width:100%;margin-bottom:28px;border-radius:8px}hr{border:none;border-top:1px solid #E5DFD5;margin:28px 0}"
                + "h1{font-size:23px;font-weight:600;line-height:1.45;margin-bottom:16px}.body p{font-size:16px;color:#494339;line-height:1.9;margin-bottom:18px}.photo{width:100%;border-radius:10px;margin-bottom:18px;display:block}"
                + ".xc{margin-top:32px;border-top:1px solid #E5DFD5;padding-top:20px}.xc summary{font-size:14px;font-weight:600;color:#9A9387;cursor:pointer;margin-bottom:12px;list-style:none}.xcp{font-size:14px;color:#9A9387;line-height:1.8;white-space:pre-wrap}"
                + "</style></head><body><div class=\"nav\"><a class=\"back\" href=\"../index.html\">&lt; 所有录音</a></div><div class=\"wrap\"><div class=\"meta\">"
                + h(rec.rowTitle()) + "</div><audio controls src=\"../audio/" + h(rec.stem()) + ".m4a\"></audio>"
                + sections + transcript + "</div></body></html>";
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        String safeName = safeEntryName(name);
        if (safeName == null) return;
        ZipEntry entry = new ZipEntry(safeName);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String safeEntryName(String name) {
        if (name == null) return null;
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isEmpty() || normalized.contains("../") || normalized.equals("..") || normalized.startsWith("../")) {
            return null;
        }
        return normalized;
    }

    private static String h(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
