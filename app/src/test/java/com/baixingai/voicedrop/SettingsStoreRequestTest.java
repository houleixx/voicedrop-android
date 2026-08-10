package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.SettingsStore;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class SettingsStoreRequestTest {
    @Test
    public void appConfigBodyDoesNotPersistFollowupSwitch() throws Exception {
        JSONObject enabled = SettingsStore.appConfigBody(true);
        JSONObject disabled = SettingsStore.appConfigBody(false);

        assertTrue(enabled.getBoolean("autoShareCommunity"));
        assertFalse(enabled.has("noFollowups"));
        assertFalse(disabled.getBoolean("autoShareCommunity"));
        assertFalse(disabled.has("noFollowups"));
    }

    @Test
    public void nameBodyTrimsProfileName() throws Exception {
        JSONObject body = SettingsStore.nameBody("  王小明  ");

        assertEquals("王小明", body.getString("name"));
    }

    @Test public void feedbackBodyTrimsAndCapsUserText() throws Exception {
        JSONObject body = SettingsStore.feedbackBody("  建议增加搜索  ", " 小王 ", " 1.0 ");
        assertEquals("建议增加搜索", body.getString("text"));
        assertEquals("小王", body.getString("name"));
        assertEquals("1.0", body.getString("version"));
        assertEquals(2000, SettingsStore.feedbackBody("长".repeat(2100), "", "").getString("text").length());
    }

    @Test
    public void validatesWechatCredentialFormatsBeforeRemoteCheck() {
        assertNull(SettingsStore.wechatCredentialFormatError(
                "wx1234567890abcdef", "0123456789abcdef0123456789abcdef"));
        assertNotNull(SettingsStore.wechatCredentialFormatError(
                "wx123", "0123456789abcdef0123456789abcdef"));
        assertNotNull(SettingsStore.wechatCredentialFormatError(
                "wx1234567890abcdef", "ABCDEF0123456789ABCDEF0123456789"));
    }

    @Test
    public void mapsWechatRelayErrorsWithoutAcceptingMissingWhitelist() throws Exception {
        assertNull(SettingsStore.wechatValidationMessage(new JSONObject().put("ok", true)));
        assertTrue(SettingsStore.wechatValidationMessage(
                new JSONObject().put("ok", false).put("errcode", 40164).put("errmsg", "invalid ip"))
                .contains("IP 白名单"));
        assertEquals("AppSecret 无效", SettingsStore.wechatValidationMessage(
                new JSONObject().put("ok", false).put("errcode", 40125)));
    }
}
