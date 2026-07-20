package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.SettingsStore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class SettingsStoreRequestTest {
    @Test
    public void styleSelectionBodyMatchesIosProfileStylesContract() throws Exception {
        JSONObject body = SettingsStore.styleSelectionBody(Arrays.asList(12, 9, 3));
        JSONArray styles = body.getJSONArray("styles");

        assertEquals(3, styles.length());
        assertEquals(12, styles.getInt(0));
        assertEquals(9, styles.getInt(1));
        assertEquals(3, styles.getInt(2));
    }

    @Test
    public void emptyStyleSelectionClearsMultiStyle() throws Exception {
        JSONObject body = SettingsStore.styleSelectionBody(Collections.emptyList());

        assertEquals(0, body.getJSONArray("styles").length());
    }

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
