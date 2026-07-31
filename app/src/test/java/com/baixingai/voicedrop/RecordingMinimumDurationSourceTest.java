package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class RecordingMinimumDurationSourceTest {
    @Test
    public void normalAndCommunityRecordingsExplainWhyShortAudioIsDiscarded() throws Exception {
        String recordings = source("RecordingsActivity.java");
        String community = source("CommunityDetailActivity.java");

        assertTrue(recordings.contains("IosDialog.show(this, \"录音太短\", "
                + "\"时间太短，不足以产生文章，这条录音不会上传。\")"));
        assertTrue(community.contains("RecordingQuality.discardIfTooShort(take)"));
        assertTrue(community.contains("SimpleToast.show(this, \"时间太短，不足以产生文章\")"));
    }

    private static String source(String name) throws Exception {
        Path relative = Paths.get("src/main/java/com/baixingai/voicedrop/" + name);
        Path path = Files.exists(relative) ? relative : Paths.get("app").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
