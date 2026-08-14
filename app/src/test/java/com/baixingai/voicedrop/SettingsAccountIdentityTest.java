package com.baixingai.voicedrop;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsAccountIdentityTest {
    @Test
    public void anonymousAccountUsesLocalStorageSubtitleAndBackendPrefixCode() {
        assertEquals("匿名 ID 保存在本机", SettingsActivity.accountSubtitle(false));
        assertEquals("A1B2C3", SettingsActivity.anonymousShortCode(
                "anon-a1b2c3d4e5f60718293a4b5c6d7e8f90", false));
    }

    @Test
    public void wechatAccountHidesAnonymousPairingCode() {
        assertEquals("已登录微信账号", SettingsActivity.accountSubtitle(true));
        assertEquals("", SettingsActivity.anonymousShortCode(
                "anon-a1b2c3d4e5f60718293a4b5c6d7e8f90", true));
    }

    @Test
    public void invalidAnonymousIdsAreNotPresentedAsPairingCodes() {
        assertEquals("", SettingsActivity.anonymousShortCode(null, false));
        assertEquals("", SettingsActivity.anonymousShortCode("anon-local", false));
        assertEquals("", SettingsActivity.anonymousShortCode("anon-12345", false));
    }
}
