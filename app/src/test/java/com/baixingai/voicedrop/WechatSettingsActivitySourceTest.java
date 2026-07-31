package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class WechatSettingsActivitySourceTest {
    @Test
    public void officialAccountPageUsesThirdPartyAuthorizationInsteadOfCollectingSecrets() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/WechatSettingsActivity.java");

        assertTrue(source.contains("Api.filesBase() + \"/wechat/bind-status\""));
        assertTrue(source.contains("new AuthStore(this).bearer()"));
        assertTrue(source.contains("/wechat/unbind"));
        assertFalse(source.contains("wechat.voicedrop.cn"));
        assertFalse(source.contains("bind_status?user"));
        assertTrue(source.contains("未连接微信公众号"));
        assertTrue(source.contains("取消连接"));
        assertTrue(source.contains("connectionContent.setVisibility(LinearLayout.INVISIBLE)"));
        assertTrue(source.contains("R.drawable.ic_check_flat"));
        assertTrue(source.contains("WechatAuthorizationActivity.class"));
        assertFalse(source.contains("AppSecret"));
        assertFalse(source.contains("IP 白名单"));
    }

    @Test
    public void providedSvgPathsAreStoredAsVectorDrawables() throws Exception {
        String compass = readSource("src/main/res/drawable/ic_wechat_help_compass.xml");
        String arrow = readSource("src/main/res/drawable/ic_arrow_up_right_flat.xml");

        assertTrue(compass.contains("android:viewportWidth=\"1024\""));
        assertTrue(compass.contains("M580.27,580.27"));
        assertTrue(arrow.contains("android:viewportWidth=\"1042\""));
        assertTrue(arrow.contains("M383.05,659.91"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
