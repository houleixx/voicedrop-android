package com.baixingai.voicedrop.data;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WechatSubscriptionStoreTest {
    @Test public void pendingContractCanBeSignedAgainAfterUserCancelledWechat() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"status\":\"pending\",\"enabled\":false}"), new JSONObject("{\"active\":false}"));
        assertTrue(status.canSubscribe());
        assertEquals("继续微信签约", status.actionLabel());
    }

    @Test public void appleSubscriptionBlocksWechatSigning() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"enabled\":true}"), new JSONObject("{\"active\":true,\"provider\":\"apple\"}"));
        assertFalse(status.canSubscribe());
        assertEquals("已通过 Apple 订阅", status.actionLabel());
    }

    @Test public void sharedStatusOutageBlocksPurchaseButKeepsCancellationAvailable() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"enabled\":true,\"status\":\"active\",\"can_cancel\":true}"), null);
        assertFalse(status.canSubscribe());
        assertTrue(status.canCancel);
        assertEquals("解除自动续费", status.actionLabel());
        assertFalse(WechatSubscriptionStore.Status.fromJson(new JSONObject("{\"enabled\":true}"),
                new JSONObject("{\"active\":false,\"degraded\":true}")).canSubscribe());
    }

    @Test public void cancelledCoverageCanResubscribeOnlyWhenSaleIsOpen() throws Exception {
        JSONObject current = new JSONObject("{\"active\":true,\"provider\":\"wechat\"}");
        assertTrue(WechatSubscriptionStore.Status.fromJson(new JSONObject("{\"enabled\":true,\"status\":\"cancelled\"}"), current).canSubscribe());
        assertFalse(WechatSubscriptionStore.Status.fromJson(new JSONObject("{\"enabled\":false,\"status\":\"cancelled\"}"), current).canSubscribe());
    }

    @Test public void jsonNullHandoffNeverLaunchesALiteralNullMiniProgram() throws Exception {
        WechatSubscriptionStore.Contract contract = WechatSubscriptionStore.Contract.fromJson(new JSONObject(
                "{\"wechat_mini_program_username\":null,\"wechat_mini_program_path\":null,\"pre_entrustweb_id\":\"valid-old-template\"}"));
        assertFalse(contract.canLaunchWechat());
        assertEquals("valid-old-template", contract.preEntrustwebId);
    }
    @Test public void parsesActiveStatus() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(new JSONObject("{"
                + "\"active\":true,\"enabled\":true,\"can_cancel\":true,\"status\":\"active\",\"expires_date\":1770000000000,\"scheduled_charge_at\":1760000000000,"
        + "\"sub_suanli\":200,\"monthly_suanli\":200}"));
        assertTrue(status.active);
        assertTrue(status.enabled);
        assertTrue(status.canCancel);
        assertEquals(1770000000000L, status.expiresAt);
        assertEquals(1760000000000L, status.scheduledChargeAt);
        assertEquals(200, status.subscriptionSuanli, 0.01);
    }

    @Test public void missingSaleFlagDefaultsToClosedLikeIos() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(new JSONObject("{}"));
        assertFalse(status.enabled);
        assertFalse(status.canCancel);
    }

    @Test public void parsesCancelledSubscriptionThatKeepsCurrentPeriodVisible() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(new JSONObject("{"
                + "\"active\":true,\"status\":\"cancelled\",\"expires_date\":1770000000000}"));
        assertTrue(status.active);
        assertTrue(status.cancelled());
        assertTrue(status.hasContract());
        assertFalse(status.canCancel);
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
