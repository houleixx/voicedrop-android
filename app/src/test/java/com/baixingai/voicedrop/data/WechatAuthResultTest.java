package com.baixingai.voicedrop.data;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WechatAuthResultTest {
    @Test
    public void matchingAnonymousScopeDoesNotRequireSwitchConfirmation() {
        WechatAuthStore.Result result = new WechatAuthStore.Result(
                true, null, "", "header.payload.signature", "users/anon-current/");

        assertFalse(result.requiresAccountSwitch("anon-current"));
    }

    @Test
    public void differentScopeRequiresSwitchConfirmation() {
        WechatAuthStore.Result result = new WechatAuthStore.Result(
                true, null, "", "header.payload.signature", "users/anon-other/");

        assertTrue(result.requiresAccountSwitch("anon-current"));
    }
}
