package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ApiRouteIntegrationContractTest {
    @Test public void appProbesAtConsentedColdStartAndThrottledForegroundResume() throws Exception {
        String app = read("VoiceDropApplication.java");
        String route = read("net/ApiRoute.java");

        assertTrue(app.contains("ApiRoute.initialize(this)"));
        assertTrue(app.contains("ApiRoute.probe(true)"));
        assertTrue(app.contains("ApiRoute.probe(false)"));
        assertTrue(app.contains("consentedServicesActivated"));
        assertTrue(route.contains("30L * 60L * 1000L"));
        assertTrue(route.contains("PROBE_TIMEOUT_MS = 6_000"));
        assertTrue(route.contains("setRequestMethod(\"HEAD\")"));
        assertTrue(route.contains("newFixedThreadPool(2)"));
        assertTrue(route.contains("getSharedPreferences"));
    }

    @Test public void publicBookRequestsAreDynamicButShareAndLabUrlsStayFixed() throws Exception {
        String shelf = read("BooksShelfActivity.java") + read("ui/BooksShelfPanel.java");
        String reader = read("BookReaderActivity.java");
        String writer = read("BookWritingActivity.java") + read("BookReviseBottomSheet.java");

        assertTrue(shelf.contains("Api.publicWebBase() + \"/books/?format=json\""));
        assertTrue(reader.contains("web.loadUrl(Api.publicWebBase()"));
        assertTrue(reader.contains("String root = \"https://voicedrop.cn/books/\""));
        assertTrue(writer.contains("https://lab.jianshuo.dev/api/book"));
        assertFalse(shelf.contains("https://voicedrop.cn/books/?format=json"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
