package com.baixingai.voicedrop.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class I18nLocaleCacheSourceTest {
    @Test
    public void textReadsTheStaticLocaleSnapshotInsteadOfAppCompatPerCall() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/I18n.java");
        String text = methodBody(source, "public static String text");

        assertTrue(source.contains("private static volatile LocaleState current"));
        assertTrue(source.contains("public static void syncLanguage(Context context)"));
        assertTrue(source.contains("public static void applyLanguage(Context context, String languageTags)"));
        assertTrue(text.contains("CATALOGS.getOrDefault(current.locale.getLanguage()"));
        assertFalse(text.contains("AppCompatDelegate"));
        assertFalse(source.contains("isEnglish("));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unclosed method");
    }
}
