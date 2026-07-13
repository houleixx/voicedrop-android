package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PrivacyConsentEntryGateSourceTest {
    @Test
    public void recordingsEntryGatesBusinessInitialization() throws Exception {
        String source = readRoot("app/src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String onCreate = methodBody(source, "protected void onCreate(Bundle savedInstanceState)");
        String continuation = methodBody(source, "private void continueAfterPrivacyConsent(Bundle savedInstanceState)");
        String onResume = methodBody(source, "protected void onResume()");

        assertTrue(onCreate.contains("PrivacyConsent consent = new PrivacyConsent(this)"));
        assertTrue(onCreate.contains("PrivacyConsentDialog.show"));
        assertFalse(onCreate.contains("new AuthStore(this)"));
        assertFalse(onCreate.contains("refreshAndDrain()"));
        assertTrue(continuation.contains("new AuthStore(this)"));
        assertTrue(continuation.contains("activateConsentedServices()"));
        assertTrue(onResume.contains("if (!businessInitialized) return;"));
    }

    @Test
    public void shareEntryGatesPayloadAndBusinessInitialization() throws Exception {
        String source = readRoot("app/src/main/java/com/baixingai/voicedrop/ShareCollectActivity.java");
        String onCreate = methodBody(source, "protected void onCreate(Bundle savedInstanceState)");
        String continuation = methodBody(source, "private void continueAfterPrivacyConsent(Bundle savedInstanceState)");

        assertTrue(onCreate.contains("PrivacyConsent consent = new PrivacyConsent(this)"));
        assertTrue(onCreate.contains("PrivacyConsentDialog.show"));
        assertFalse(onCreate.contains("new AuthStore(this)"));
        assertFalse(onCreate.contains("loadPayload("));
        assertTrue(continuation.contains("new AuthStore(this)"));
        assertTrue(continuation.contains("activateConsentedServices()"));
        assertTrue(continuation.contains("loadPayload(getIntent())"));
        assertTrue(continuation.contains("classify(getIntent(), payload)"));
        assertTrue(continuation.contains("render()"));
    }

    private static String readRoot(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..", relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(open + 1, i);
        }
        return "";
    }
}
