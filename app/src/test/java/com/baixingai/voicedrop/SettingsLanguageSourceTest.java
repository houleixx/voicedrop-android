package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public final class SettingsLanguageSourceTest {
    @Test
    public void settingsUsesAppCompatPersistedApplicationLocales() throws Exception {
        String source = new String(Files.readAllBytes(
                Path.of("src/main/java/com/baixingai/voicedrop/LanguageSettingsActivity.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("AppCompatDelegate.getApplicationLocales()"));
        assertTrue(source.contains("I18n.applyLanguage(this, selected.languageTags())"));
    }

    @Test
    public void settingsPlacesLanguageBeforeCacheAndOpensItsOwnPage() throws Exception {
        String source = new String(Files.readAllBytes(
                Path.of("src/main/java/com/baixingai/voicedrop/SettingsActivity.java")), StandardCharsets.UTF_8);
        assertTrue(source.indexOf("this::openLanguageSettings") < source.indexOf("addCacheRow(card)"));
        assertTrue(source.contains("new Intent(this, LanguageSettingsActivity.class)"));
    }

    @Test
    public void declaresTheSameLocalesToAndroid13SystemSettings() throws Exception {
        String config = new String(Files.readAllBytes(
                Path.of("src/main/res/xml/locales_config.xml")), StandardCharsets.UTF_8);
        assertTrue(config.contains("android:name=\"zh-Hans\""));
        assertTrue(config.contains("android:name=\"en\""));
    }
}
