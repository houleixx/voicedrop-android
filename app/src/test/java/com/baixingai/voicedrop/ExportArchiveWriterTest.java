package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.ArticleDoc;
import com.baixingai.voicedrop.data.ExportArchiveWriter;
import com.baixingai.voicedrop.data.MinedArticle;
import com.baixingai.voicedrop.data.Recording;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ExportArchiveWriterTest {
    @Test
    public void writesIosStyleArchiveWithAudioArticleTranscriptAndPhotos() throws Exception {
        Recording rec = new Recording("2026-07-04-120000-10s-Morning-Shanghai.m4a", "2026", true, false);
        ArticleDoc doc = new ArticleDoc("doc-1", "原始转录", Arrays.asList(
                new MinedArticle("第一篇", "第一段\n\n[[photo:1]]\n\n第二段", null)
        ), Arrays.asList("photos/session/photo.jpg"));
        FakeSource source = new FakeSource();
        source.bytes.put(rec.audioName, "audio".getBytes(StandardCharsets.UTF_8));
        source.bytes.put(rec.srtKey(), "1\n00:00:00,000 --> 00:00:01,000\nhello".getBytes(StandardCharsets.UTF_8));
        source.bytes.put("photos/session/photo.jpg", "jpg".getBytes(StandardCharsets.UTF_8));
        source.docs.put(rec.audioName, doc);

        File out = Files.createTempFile("voicedrop-export", ".zip").toFile();
        ExportArchiveWriter.write(out, Arrays.asList(rec), source);

        Map<String, byte[]> entries = unzip(out);
        assertNotNull(entries.get("index.html"));
        assertArrayEquals("audio".getBytes(StandardCharsets.UTF_8),
                entries.get("audio/" + rec.stem() + ".m4a"));
        assertArrayEquals("jpg".getBytes(StandardCharsets.UTF_8),
                entries.get("photos/session/photo.jpg"));
        assertNotNull(entries.get("recordings/" + rec.stem() + ".srt"));

        String articleHtml = new String(entries.get("recordings/" + rec.stem() + ".html"), StandardCharsets.UTF_8);
        assertTrue(articleHtml.contains("<audio controls src=\"../audio/" + rec.stem() + ".m4a\"></audio>"));
        assertTrue(articleHtml.contains("<img class=\"photo\" src=\"../photos/session/photo.jpg\""));
        assertTrue(articleHtml.contains("原始转录"));

        String indexHtml = new String(entries.get("index.html"), StandardCharsets.UTF_8);
        assertTrue(indexHtml.contains("1</div><div class=\"sl\">条录音"));
        assertTrue(indexHtml.contains("recordings/" + rec.stem() + ".html"));
    }

    @Test
    public void rejectsEmptyRecordingList() throws Exception {
        File out = Files.createTempFile("voicedrop-export-empty", ".zip").toFile();

        try {
            ExportArchiveWriter.write(out, Arrays.asList(), new FakeSource());
        } catch (IllegalArgumentException e) {
            assertEquals("没有录音可以导出", e.getMessage());
            return;
        }

        throw new AssertionError("Expected empty exports to fail");
    }

    private static Map<String, byte[]> unzip(File file) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file.toPath()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = zip.read(buffer)) != -1) out.write(buffer, 0, n);
                entries.put(entry.getName(), out.toByteArray());
            }
        }
        return entries;
    }

    private static final class FakeSource implements ExportArchiveWriter.Source {
        final Map<String, byte[]> bytes = new HashMap<>();
        final Map<String, ArticleDoc> docs = new HashMap<>();

        @Override
        public byte[] download(String key) {
            return bytes.get(key);
        }

        @Override
        public ArticleDoc fetchDoc(Recording recording) {
            return docs.get(recording.audioName);
        }
    }
}
