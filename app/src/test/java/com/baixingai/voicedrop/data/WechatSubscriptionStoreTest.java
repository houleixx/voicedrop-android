package com.baixingai.voicedrop.data;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WechatSubscriptionStoreTest {
    @Test public void parsesActiveStatus() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(new JSONObject("{"
                + "\"active\":true,\"status\":\"active\",\"expires_date\":1770000000000,"
                + "\"sub_suanli\":200,\"monthly_suanli\":200}"));
        assertTrue(status.active);
        assertEquals(1770000000000L, status.expiresAt);
        assertEquals(200, status.subscriptionSuanli, 0.01);
    }

    @Test public void parsesAndroidSigningHandoffWithoutSecrets() throws Exception {
        WechatSubscriptionStore.Contract contract = WechatSubscriptionStore.Contract.fromJson(new JSONObject("{"
                + "\"contract_code\":\"VD123\",\"pre_entrustweb_id\":\"pre123\","
                + "\"wechat_mini_program_username\":\"gh_abc\","
                + "\"wechat_mini_program_path\":\"pages/sign?pre_entrustweb_id=pre123\"}"));
        assertEquals("VD123", contract.contractCode);
        assertEquals("pre123", contract.preEntrustwebId);
        assertTrue(contract.canLaunchWechat());
    }

    @Test public void originalContractResponseDoesNotPretendItCanLaunchWechat() throws Exception {
        WechatSubscriptionStore.Contract contract = WechatSubscriptionStore.Contract.fromJson(
                new JSONObject("{\"contract_code\":\"VD123\",\"plan_id\":\"monthly\"}"));
        assertFalse(contract.canLaunchWechat());
    }
}
