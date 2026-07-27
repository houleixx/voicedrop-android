package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class RecordingMiniProgramShareSourceTest {
    @Test
    public void checksCommunityVisibilityBeforeOpeningTheShareLoadingDialog() throws Exception {
        String source = new String(Files.readAllBytes(Path.of(
                "src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java")), StandardCharsets.UTF_8);
        int share = source.indexOf("protected void shareMiniProgramCard");
        int prepare = source.indexOf("private void prepareMiniProgramCard", share);
        String check = source.substring(share, prepare);

        assertTrue(check.contains("Boolean.FALSE.equals(sharedToCommunity)"));
        assertTrue(check.contains("prepareMiniProgramCard(rec, communityShareId)"));
        assertTrue(check.contains("community.sharedShareId(rec)"));
        assertTrue(check.indexOf("WechatShareLoadingDialog.show") < 0);
        assertTrue(source.substring(prepare).contains("WechatShareLoadingDialog.show(this)"));
    }
}
