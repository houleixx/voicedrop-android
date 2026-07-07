package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.WechatLogin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WechatLoginConfigTest {
    @Test
    public void usesProductionWechatOpenAppId() {
        assertEquals("wx1573f936967f5420", WechatLogin.APP_ID);
    }
}
