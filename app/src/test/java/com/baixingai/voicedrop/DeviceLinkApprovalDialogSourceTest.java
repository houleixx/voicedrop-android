package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DeviceLinkApprovalDialogSourceTest {
    @Test
    public void deviceLinkApprovalRequiresYesOrRejectWithoutAnExtraPrompt() throws Exception {
        String[] activities = {
                "RecordingsActivity.java",
                "RecordingDetailActivity.java",
                "CommunityActivity.java",
                "CommunityDetailActivity.java"
        };

        for (String activity : activities) {
            String source = readSource("src/main/java/com/baixingai/voicedrop/" + activity);
            String method = methodBody(source, "protected void showDeviceLinkApproval");

            assertTrue(activity, method.contains("IosDialog.showDeviceLinkApproval(this, code, null"));
            assertTrue(activity, method.contains("deviceLinkStore.cancel(pairingId)"));
            assertFalse(activity, method.contains("请在新设备输入验证码"));
            assertFalse(activity, method.contains("IosDialog.show(this"));
        }
    }

    @Test
    public void deviceLinkApprovalCopyAndCodeTypographyMatchTheSharedDesign() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/IosDialog.java");
        String method = methodBody(source, "public static IosDialog showDeviceLinkApproval");

        assertTrue(method.contains("有新设备想登录你的账号"));
        assertTrue(method.contains("在新设备上输入下面的验证码"));
        assertTrue(method.contains("不是你本人操作？点「不是我」。"));
        assertTrue(method.contains("Typeface.MONOSPACE, Typeface.BOLD"));
        assertTrue(method.contains("setLetterSpacing(0.18f)"));
        assertTrue(method.contains("\"这是我\", onConfirm, \"不是我\", onReject"));
        assertFalse(method.contains("setBackground"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("Unclosed method: " + signature);
    }
}
