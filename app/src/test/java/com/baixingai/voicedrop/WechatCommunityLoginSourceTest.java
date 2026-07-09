package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WechatCommunityLoginSourceTest {
    @Test
    public void successfulWechatLoginSwitchesAccountAndReturnsToRecordingList() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/wxapi/WXEntryActivity.java");
        String completeLogin = methodBody(source, "private void completeLogin");

        assertTrue(source.contains("import com.baixingai.voicedrop.RecordingsActivity;"));
        assertTrue(source.contains("import com.baixingai.voicedrop.ui.IosDialog;"));
        assertTrue(source.contains("import com.baixingai.voicedrop.data.PendingCommunityShareStore;"));
        assertTrue(source.contains("result.requiresAccountSwitch(auth.anonId())"));
        assertTrue(source.contains("切换到微信账号"));
        assertTrue(source.contains("保留当前账号"));
        assertTrue(source.contains("\"保留当前账号\", this::keepCurrentAccount,\n"
                + "                false, false);"));
        assertTrue(completeLogin.contains("auth.storeSession(result.session)"));
        assertTrue(completeLogin.contains("clearPendingCommunityShare();"));
        assertTrue(completeLogin.contains("openRecordings("));
        assertFalse(source.contains("CommunityShareResume"));
        assertFalse(source.contains("RecordingDetailActivity"));
    }

    @Test
    public void cancelledOrFailedWechatCallbacksClearPendingCommunityShareState() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/wxapi/WXEntryActivity.java");

        String onResp = methodBody(source, "public void onResp");
        String exchange = methodBody(source, "private void exchange");

        assertTrue(onResp.contains("clearPendingCommunityShare();"));
        assertTrue(exchange.contains("clearPendingCommunityShare();"));
        assertTrue(exchange.contains("clearPendingCommunityShare();\n                    toast(\"微信登录失败：\" + e.getMessage());"));
        assertTrue(source.contains("private void clearPendingCommunityShare()"));
        assertTrue(source.contains("new PendingCommunityShareStore(this).clear();"));
        assertTrue(source.contains("private void keepCurrentAccount()"));
        assertFalse(source.contains("clearPendingCommunityShare();\n        exchange(authResp.code.trim());"));
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
