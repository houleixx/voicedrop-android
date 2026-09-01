package com.baixingai.voicedrop.data;

/** The deliberately small set of per-app language choices exposed by VoiceDrop. */
public enum AppLanguage {
    SYSTEM("", "跟随系统"),
    SIMPLIFIED_CHINESE("zh-Hans", "简体中文"),
    ENGLISH("en", "English");

    private final String languageTags;
    private final String label;

    AppLanguage(String languageTags, String label) {
        this.languageTags = languageTags;
        this.label = label;
    }

    public String languageTags() {
        return languageTags;
    }

    public String label() {
        return label;
    }

    public static AppLanguage fromLanguageTags(String languageTags) {
        if ("zh-Hans".equals(languageTags)) return SIMPLIFIED_CHINESE;
        if ("en".equals(languageTags)) return ENGLISH;
        return SYSTEM;
    }
}
