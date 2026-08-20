package com.baixingai.voicedrop;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class DeviceLinkResponderTest {
    @Test
    public void wechatDeviceIgnoresRequestWithoutCancellingSharedPairing() {
        Harness h = new Harness(true);

        h.responder.onRequest("p1", "7391", "pub1");

        assertNull(h.presentation.reject);
        assertEquals(0, h.gateway.cancelled.size());
        assertEquals(0, h.gateway.completed.size());
    }

    @Test
    public void anonymousDeviceCompletesMatchingRequestWithAnonymousCredential() {
        Harness h = new Harness(false);
        h.responder.onRequest("p1", "7391", "pub1");

        h.responder.onRelease("p1");

        assertEquals("7391", h.presentation.code);
        assertEquals(List.of("p1:pub1:anon-token"), h.gateway.completed);
        assertEquals(true, h.presentation.dismissed);
        assertEquals(0, h.presentation.messages.size());
    }

    @Test
    public void explicitRejectionIsTheOnlyPathThatCancels() {
        Harness h = new Harness(false);
        h.responder.onRequest("p1", "7391", "pub1");
        assertNotNull(h.presentation.reject);

        h.presentation.reject.run();
        h.responder.onRelease("p1");

        assertEquals(List.of("p1"), h.gateway.cancelled);
        assertEquals(0, h.gateway.completed.size());
    }

    @Test
    public void accountBecomingWechatBeforeReleaseClearsLocallyWithoutCancelling() {
        Harness h = new Harness(false);
        h.responder.onRequest("p1", "7391", "pub1");
        h.account.wechatAuthenticated = true;

        h.responder.onRelease("p1");
        h.account.wechatAuthenticated = false;
        h.responder.onRelease("p1");

        assertEquals(0, h.gateway.cancelled.size());
        assertEquals(0, h.gateway.completed.size());
    }

    private static final class Harness {
        final FakeAccount account;
        final FakeGateway gateway = new FakeGateway();
        final FakePresentation presentation = new FakePresentation();
        final DeviceLinkResponder responder;

        Harness(boolean wechatAuthenticated) {
            account = new FakeAccount(wechatAuthenticated);
            responder = new DeviceLinkResponder(account, gateway, Runnable::run, presentation);
        }
    }

    private static final class FakeAccount implements DeviceLinkResponder.Account {
        boolean wechatAuthenticated;

        FakeAccount(boolean wechatAuthenticated) {
            this.wechatAuthenticated = wechatAuthenticated;
        }

        @Override public boolean isWechatAuthenticated() { return wechatAuthenticated; }
        @Override public String anonymousBearer() { return "anon-token"; }
    }

    private static final class FakeGateway implements DeviceLinkResponder.Gateway {
        final List<String> cancelled = new ArrayList<>();
        final List<String> completed = new ArrayList<>();

        @Override public void cancel(String pairingId) { cancelled.add(pairingId); }

        @Override public void complete(String pairingId, String pubkey, String bearer) {
            completed.add(pairingId + ":" + pubkey + ":" + bearer);
        }
    }

    private static final class FakePresentation implements DeviceLinkResponder.Presentation {
        String code;
        Runnable reject;
        boolean dismissed;
        final List<String> messages = new ArrayList<>();

        @Override public void showApproval(String code, Runnable onReject) {
            this.code = code;
            reject = onReject;
        }

        @Override public void dismissApproval() { dismissed = true; }

        @Override public void showMessage(String message) { messages.add(message); }
    }
}
