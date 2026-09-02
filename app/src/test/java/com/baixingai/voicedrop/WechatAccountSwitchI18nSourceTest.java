package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class WechatAccountSwitchI18nSourceTest {
    @Test
    public void usesSingleLineEnglishLabelsForTheTwoStorageActions() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/I18n.java");

        assertTrue(source.contains("copy.put(\"切换到微信空间\", \"Switch storage\");"));
        assertTrue(source.contains("copy.put(\"保留当前空间\", \"Keep current\");"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
