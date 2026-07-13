package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PrivacyDocumentationSourceTest {
    @Test
    public void aboutOpensPublishedPrivacyPolicy() throws Exception {
        String source = readRoot("app/src/main/java/com/baixingai/voicedrop/AboutActivity.java");

        assertTrue(source.contains("PrivacyConsent.POLICY_URL"));
        assertTrue(source.contains("Intent.ACTION_VIEW"));
        assertFalse(source.contains("录音只上传到你自己的云端空间"));
    }

    @Test
    public void readmeDoesNotDocumentUmeng() throws Exception {
        String readme = readRoot("README.md");

        assertFalse(readme.contains("## 友盟统计"));
        assertFalse(readme.contains("umeng.appKey"));
        assertFalse(readme.contains("UMENG_APP_KEY"));
    }

    private static String readRoot(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..", relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
