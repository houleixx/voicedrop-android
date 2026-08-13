package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Locks Android to the POST /api/book contract used by iOS and the live backend. */
public final class BookWritingApiContractTest {
    @Test public void submissionMatchesIosAndBackendContract() throws Exception {
        String source = read("BookWritingActivity.java");

        assertTrue(source.contains("API = \"https://lab.jianshuo.dev/api/book\""));
        assertTrue(source.contains("new JSONObject().put(\"seed\", value)"));
        assertTrue(source.contains("new AuthStore(this).bearer()"));
        assertTrue(source.contains("postJson(API,"));
        assertTrue(source.contains("new HttpClient.RequestOptions().readTimeoutMs(30_000)"));
        assertTrue(source.contains("BookWritingResult.from(response.code, response.text())"));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
