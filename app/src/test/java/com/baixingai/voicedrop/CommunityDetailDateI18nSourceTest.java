package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class CommunityDetailDateI18nSourceTest {
    @Test
    public void articleAndCommunityDatesUseTheCurrentAppLocale() throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop/CommunityDetailActivity.java");
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        assertTrue(source.contains("protected String englishMonthDay(RecordingName.Parsed parsed)"));
        assertTrue(source.contains("english ? \"MMM d, yyyy\" : \"yyyy年M月d日\""));
        assertTrue(source.contains("english ? \"MMM d\" : \"M月d日\""));
        assertTrue(source.contains("return englishMonthDay(parsed)"));
    }
}
