package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class RecordingMiniProgramShareSourceTest {
    @Test
    public void usesPublicShareLinkWithoutRequiringCommunityVisibility() throws Exception {
        String source = new String(Files.readAllBytes(Path.of(
                "src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java")), StandardCharsets.UTF_8);
        int share = source.indexOf("protected void shareMiniProgramCard");
        int prepare = source.indexOf("private void prepareMiniProgramCard", share);
        String check = source.substring(share, prepare);
        assertTrue(check.contains("prepareMiniProgramCard(rec)"));
        assertTrue(!check.contains("sharedToCommunity"));
        assertTrue(!check.contains("community.sharedShareId"));
        assertTrue(source.substring(prepare).contains("WechatShareLoadingDialog.show(this)"));
        assertTrue(source.substring(prepare).contains("WechatShareContent.publicShareId(url)"));
        assertTrue(source.substring(prepare).contains("WechatShareContent.sharedArticlePath(shareId, articleIndex)"));
    }
}
