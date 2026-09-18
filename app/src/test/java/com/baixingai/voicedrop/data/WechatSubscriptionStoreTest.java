package com.baixingai.voicedrop.data;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WechatSubscriptionStoreTest {
    @Test public void appCheckoutResumeAndPaidUnsignedStatus() throws Exception {
        JSONObject shared = new JSONObject("{\"active\":false}");
        JSONObject body = new JSONObject("{\"enabled\":true,\"status\":\"pending\",\"payment_pending\":true,\"checkout_pending\":true,\"amount_fen\":1}");
        WechatSubscriptionStore.Status pending = WechatSubscriptionStore.Status.fromJson(body, shared);
        assertTrue(pending.canSubscribe());
        assertEquals("继续支付并签约", pending.actionLabel());
        assertEquals("¥0.01/月", pending.priceLabel());
        body.put("checkout_pending", false).put("checkout_paid", true).put("active", true).put("payment_pending", false);
        WechatSubscriptionStore.Status paid = WechatSubscriptionStore.Status.fromJson(body, shared);
        assertFalse(paid.canSubscribe());
        assertEquals("已付款 · 自动续费待确认", paid.displayTitle());
        assertTrue(paid.paymentNotice().contains("无需再次付款"));
    }

    @Test public void appCheckoutCannotBypassAppleOrUnknownPurchaseStatus() throws Exception {
        JSONObject body = new JSONObject("{\"enabled\":true,\"status\":\"pending\",\"checkout_pending\":true}");
        assertFalse(WechatSubscriptionStore.Status.fromJson(body).canSubscribe());
        assertFalse(WechatSubscriptionStore.Status.fromJson(body,
                new JSONObject("{\"active\":true,\"provider\":\"apple\"}")).canSubscribe());
    }

    @Test public void appPayParamsRequireAllFieldsAndRegisteredApp() throws Exception {
        JSONObject params = new JSONObject("{\"appId\":\"app\",\"partnerId\":\"merchant\",\"prepayId\":\"prepay\",\"packageValue\":\"Sign=WXPay\",\"nonceStr\":\"nonce\",\"timeStamp\":\"1789704000\",\"sign\":\"signature\"}");
        JSONObject body = new JSONObject().put("pay_params", params);
        WechatSubscriptionStore.AppPayParams p = WechatSubscriptionStore.Contract.fromJson(body).appPayParams;
        assertTrue(p.validFor("app"));
        assertFalse(p.validFor("another-app"));
        params.remove("sign");
        assertFalse(WechatSubscriptionStore.Contract.fromJson(body).appPayParams.validFor("app"));
        assertTrue(WechatSubscriptionStore.Contract.fromJson(new JSONObject()).appPayParams == null);
    }

    @Test public void unresolvedPaymentBlocksResigningEvenAfterCancellation() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"status\":\"cancelled\",\"enabled\":true,\"payment_pending\":true}"),
                new JSONObject("{\"active\":false}"));
        assertFalse(status.canSubscribe());
        assertEquals("扣款结果确认中", status.actionLabel());
        assertEquals("扣款结果确认中", status.displayTitle());
    }

    @Test public void stoppedRenewalKeepsCancellationAvailableAndExplainsRecovery() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"status\":\"active\",\"active\":true,\"can_cancel\":true,\"renewal_stopped\":true,\"payment_issue\":\"payment-failed\"}"),
                new JSONObject("{\"active\":true,\"provider\":\"wechat\"}"));
        assertTrue(status.canCancel);
        assertFalse(status.canSubscribe());
        assertEquals("本周期续费已暂停", status.displayTitle());
        assertTrue(status.paymentNotice().contains("停止自动重试"));
    }

    @Test public void existingCoverageAndSigningDoNotProduceFalsePaymentSuccess() throws Exception {
        WechatSubscriptionStore.Status old = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"active\":true,\"expires_date\":1000,\"status\":\"active\",\"payment_pending\":true}"));
        assertFalse(old.hasNewCoverage(1000));
        WechatSubscriptionStore.Status signed = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"can_cancel\":true,\"status\":\"active\"}"));
        assertFalse(signed.hasNewCoverage(0));
        assertEquals("已签约 · 等待扣款", signed.displayTitle());
        WechatSubscriptionStore.Status paid = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"active\":true,\"expires_date\":2000,\"status\":\"active\"}"));
        assertTrue(paid.hasNewCoverage(1000));
    }

    @Test public void degradedWechatStatusCannotAuthorizePurchase() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"enabled\":true,\"degraded\":true}"), new JSONObject("{\"active\":false}"));
        assertFalse(status.canSubscribe());
    }

    @Test public void oldPaymentFailureDoesNotHideSuccessfulCancellation() throws Exception {
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(
                new JSONObject("{\"status\":\"cancelled\",\"payment_issue\":\"payment-failed\"}"));
        assertEquals("自动续费已解除", status.displayTitle());
        assertTrue(status.paymentNotice().contains("不再发起新的续费扣款"));
    }

    @Test public void paymentFailureMessagesNeverExposeRawProviderDetails() {
        assertTrue(WechatSubscriptionStore.failureMessage("payment-pending").contains("请勿重复开通"));
        assertFalse(WechatSubscriptionStore.failureMessage("internal-provider-secret").contains("secret"));
        assertFalse(WechatSubscriptionStore.userMessage(new java.io.IOException("private host detail")).contains("private host"));
    }

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


    @Test public void onlyAppPaymentPayloadCanProvidePaymentParameters() throws Exception {
        WechatSubscriptionStore.Contract old = WechatSubscriptionStore.Contract.fromJson(new JSONObject(
                "{\"contract_code\":\"old\",\"pre_entrustweb_id\":\"old-session\",\"wechat_mini_program_username\":\"gh_old\"}"));
        assertTrue(old.appPayParams == null);
    }

    @Test public void unsupportedRenewalIsExplicitWithoutPretendingToCancelAuthorization() throws Exception {
        JSONObject body = new JSONObject("{\"active\":true,\"status\":\"pending\",\"checkout_paid\":true,\"can_cancel\":true,\"renewal_available\":false,\"contract_management_available\":false}");
        WechatSubscriptionStore.Status status = WechatSubscriptionStore.Status.fromJson(body, new JSONObject("{\"active\":true,\"provider\":\"wechat\"}"));
        assertFalse(status.canCancel);
        assertFalse(status.canSubscribe());
        assertEquals("已付款 · 自动续费暂不可用", status.displayTitle());
        assertEquals("请在微信管理签约", status.actionLabel());
        assertTrue(status.paymentNotice().contains("App 内解约"));
        assertTrue(status.paymentNotice().contains("无需再次付款"));
    }
}
