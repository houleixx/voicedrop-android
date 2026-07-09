package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommunityShareErrorSourceTest {
    @Test
    public void detailPageStartsWechatLoginForSigninRequiredShareAndDoesNotMentionApple() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String doShareCommunity = methodBody(source, "protected void doShareCommunity");

        assertTrue(source.contains("import com.baixingai.voicedrop.data.PendingCommunityShareStore;"));
        assertTrue(source.contains("import com.baixingai.voicedrop.data.WechatLogin;"));
        assertTrue(doShareCommunity.contains("if (result.hasInvalidSession()) auth.signOutWechat();"));
        assertTrue(doShareCommunity.contains("PendingCommunityShareStore pending = new PendingCommunityShareStore(this);"));
        assertTrue(doShareCommunity.contains("pending.save(rec.audioName, replyTo);"));
        assertTrue(doShareCommunity.contains("if (!WechatLogin.start(this)) {"));
        assertTrue(doShareCommunity.contains("pending.clear();"));
        assertTrue(doShareCommunity.contains("toast(\"无法打开微信，请确认已安装微信\");"));
        assertFalse(source.contains("社区分享失败，可能需要 Apple 会话"));
    }

    @Test
    public void detailPageConsumesPendingShareAfterReloadingMatchingRecording() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");
        String showArticle = methodBody(source, "protected void showArticle(Recording rec, ArticleDoc doc, boolean animateOpen, boolean refreshHistory)");

        assertTrue(showArticle.contains("PendingCommunityShareStore.Pending pending = new PendingCommunityShareStore(this).consume(rec.audioName);"));
        assertTrue(showArticle.contains("if (pending != null) {"));
        assertTrue(showArticle.contains("doShareCommunity(rec, pending.replyToShareId);"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new IllegalArgumentException("Missing " + signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(brace, i + 1);
            }
        }
        throw new IllegalArgumentException("Unclosed method " + signature);
    }
}
