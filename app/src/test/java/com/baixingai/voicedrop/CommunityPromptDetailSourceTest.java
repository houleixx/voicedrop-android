package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommunityPromptDetailSourceTest {
    @Test
    public void promptPostsUseAnIsolatedCollectibleDetailRenderer() throws Exception {
        String source = source();
        assertTrue(source.contains("if (post.isPrompt()) {\n            showCommunityPromptPost(post, doc, animateOpen);\n            return;"));

        String prompt = between(source,
                "protected void showCommunityPromptPost(",
                "protected void startCommunityReplyRecording(");
        assertTrue(prompt.contains("一条 VoiceDrop 提示词 · 分享码"));
        assertTrue(prompt.contains("怎么用"));
        assertTrue(prompt.contains("收下这条提示词"));
        assertTrue(prompt.contains("promptStore.importCode(post.promptCode)"));
        assertTrue(prompt.contains("community.engage(shareId, \"like\", liked[0])"));
        assertTrue(prompt.contains("showCommunityPostMenu(post, authorName, v)"));
        assertFalse(prompt.contains("renderArticleBody("));
        assertFalse(prompt.contains("renderReplies("));
        assertFalse(prompt.contains("startCommunityReplyRecording("));
    }

    @Test
    public void articleRendererKeepsBodyPhotosAndReplies() throws Exception {
        String source = source();
        String article = between(source,
                "protected void showCommunityPost(CommunityStore.Post post, ArticleDoc doc, boolean animateOpen)",
                "protected void showCommunityPromptPost(");
        assertTrue(article.contains("renderArticleBody(content, article.body, doc)"));
        assertTrue(article.contains("renderReplies(repliesSection, fullReplies, post)"));
        assertTrue(article.contains("recordingBarContainer"));
    }

    private static String source() throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop/CommunityDetailActivity.java");
        if (!Files.exists(path)) path = Paths.get("app", path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        return source.substring(from, to);
    }
}
