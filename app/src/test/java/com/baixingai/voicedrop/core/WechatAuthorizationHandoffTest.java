package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WechatAuthorizationHandoffTest {
    private static final String AUTH_URL = "https://mp.weixin.qq.com/cgi-bin/componentloginpage"
            + "?component_appid=wx-test&pre_auth_code=pre-code"
            + "&redirect_uri=https%3A%2F%2Fvoicedrop.cn%2Ffiles%2Fapi%2Fwechat%2Fauth-callback"
            + "&auth_type=1";

    @Test
    public void extractsTrustedWechatAuthorizationUrlFromScanPage() {
        String html = "<script>window.location.replace(" + org.json.JSONObject.quote(AUTH_URL) + ");</script>";

        assertEquals(AUTH_URL, WechatAuthorizationHandoff.authorizationUrl(html));
    }

    @Test
    public void rejectsUnexpectedAuthorizationTargetsAndIncompleteUrls() {
        assertThrows(IllegalArgumentException.class, () -> WechatAuthorizationHandoff.authorizationUrl(
                "<script>window.location.replace(\"https://evil.example/steal\")</script>"));
        assertThrows(IllegalArgumentException.class, () -> WechatAuthorizationHandoff.authorizationUrl(
                "<script>window.location.replace(\"https://mp.weixin.qq.com/cgi-bin/componentloginpage\")</script>"));
    }

    @Test
    public void buildsOriginOnlyHandoffPageAndRequiresProductionScanOrigin() {
        String scanUrl = "https://voicedrop.cn/files/api/wechat/scan?state=signed";
        String html = WechatAuthorizationHandoff.handoffHtml(scanUrl,
                "<script>window.location.replace(" + org.json.JSONObject.quote(AUTH_URL) + ");</script>");

        assertTrue(html.contains("name=\"referrer\" content=\"origin\""));
        assertTrue(html.contains("window.location.replace(" + org.json.JSONObject.quote(AUTH_URL) + ")"));
        assertFalse(html.contains("<a "));
        assertThrows(IllegalArgumentException.class, () -> WechatAuthorizationHandoff.handoffHtml(
                "https://evil.example/files/api/wechat/scan?state=signed", html));
    }
}
