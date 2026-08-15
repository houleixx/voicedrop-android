package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class CommunityMiniProgramShareSourceTest {
    @Test
    public void communityArticleMenuCanShareAWechatMiniProgramCard() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/CommunityDetailActivity.java");

        assertTrue(source.contains("ShareBottomSheet.drawable(\"小程序卡片\""));
        assertTrue(source.contains("shareCommunityMiniProgramCard(post)"));
        assertTrue(source.contains("ShareBottomSheet.remix(\"朋友圈\""));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"复制链接\""));
        assertTrue(source.contains("ShareBottomSheet.drawable(\"其它分享\""));
        assertTrue(source.contains("Api.sharePage(post.shareId)"));
        assertTrue(source.contains("WechatMiniProgramShare.communityPath(post.shareId)"));
        assertTrue(source.contains("communityShareThumbnail(post)"));
        assertTrue(source.contains("ArticleBody.firstPhotoKey(article.body, doc.photos)"));
        assertTrue(source.contains("warmCommunityShareThumbnail(post)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
