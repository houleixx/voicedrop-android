package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public final class VoiceDropActivityLocaleSourceTest {
    @Test
    public void reconcilesI18nAfterAppCompatRestoresThePersistedLocale() throws Exception {
        String source = new String(Files.readAllBytes(
                Path.of("src/main/java/com/baixingai/voicedrop/VoiceDropActivity.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("protected void onCreate(Bundle savedInstanceState)"));
        assertTrue(source.indexOf("super.onCreate(savedInstanceState);")
                < source.indexOf("I18n.syncLanguage(this);"));
    }
}
