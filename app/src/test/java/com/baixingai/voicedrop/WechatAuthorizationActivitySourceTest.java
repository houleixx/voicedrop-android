package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class WechatAuthorizationActivitySourceTest {
    @Test
    public void authorizationPageCapturesWebViewAndUsesTheAuthenticatedProductionEndpoint() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/WechatAuthorizationActivity.java");

        assertTrue(source.contains("Api.filesBase() + \"/wechat/authorization\""));
        assertTrue(source.contains("scan_url"));
        assertTrue(source.contains("Api.filesBase() + \"/wechat/bind-status\""));
        assertFalse(source.contains("wechat.voicedrop.cn"));
        assertFalse(source.contains("bind_status?user"));
        assertTrue(source.contains("重新授权公众号"));
        assertTrue(source.contains("PixelCopy.request(getWindow()"));
        assertTrue(source.contains("captureWebViewFallback"));
        assertTrue(source.contains("MediaStore.Images.Media.EXTERNAL_CONTENT_URI"));
        assertTrue(source.contains("com.tencent.mm"));
        assertTrue(source.contains("截图二维码"));
        assertTrue(source.contains("new LinearLayout.LayoutParams(-1, dp(360))"));
        assertTrue(source.contains("二维码已保存到相册"));
        assertTrue(source.contains("打开微信"));
        assertTrue(source.contains("IosDialog.showConfirmation"));
        assertTrue(source.contains("点击页面上的「相册」"));
        assertFalse(source.contains("右上角「相册」"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
