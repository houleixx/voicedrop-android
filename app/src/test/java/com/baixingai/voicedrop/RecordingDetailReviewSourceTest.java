package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;

public class RecordingDetailReviewSourceTest {
    @Test
    public void openingRecordingDetailNeverLaunchesAnAppStoreReview() throws Exception {
        Path detailPath = sourcePath("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String detail = new String(Files.readAllBytes(detailPath), StandardCharsets.UTF_8);
        Path reviewPrompter = sourcePath("src/main/java/com/baixingai/voicedrop/data/ReviewPrompter.java");

        assertFalse(detail.contains("ReviewPrompter"));
        assertFalse(Files.exists(reviewPrompter));
    }

    private static Path sourcePath(String moduleRelative) {
        Path path = Paths.get(moduleRelative);
        return Files.exists(path) ? path : Paths.get("app", moduleRelative);
    }
}
