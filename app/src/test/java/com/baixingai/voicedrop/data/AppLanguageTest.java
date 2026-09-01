package com.baixingai.voicedrop.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class AppLanguageTest {
    @Test
    public void mapsSupportedLocaleTagsToTheThreeVisibleChoices() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags(""));
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTags("zh-Hans"));
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en"));
    }

    @Test
    public void treatsUnexpectedLocaleListsAsFollowSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("fr-CA"));
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags(null));
    }

    @Test
    public void exposesCanonicalTagsForAppCompat() {
        assertEquals("", AppLanguage.SYSTEM.languageTags());
        assertEquals("zh-Hans", AppLanguage.SIMPLIFIED_CHINESE.languageTags());
        assertEquals("en", AppLanguage.ENGLISH.languageTags());
    }

    @Test
    public void exposesTheThreeProductLabels() {
        assertEquals("跟随系统", AppLanguage.SYSTEM.label());
        assertEquals("简体中文", AppLanguage.SIMPLIFIED_CHINESE.label());
        assertEquals("English", AppLanguage.ENGLISH.label());
    }
}
