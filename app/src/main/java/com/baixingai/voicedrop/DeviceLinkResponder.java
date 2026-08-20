package com.baixingai.voicedrop;

import android.app.Activity;

import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.DeviceLinkCrypto;
import com.baixingai.voicedrop.data.DeviceLinkStore;
import com.baixingai.voicedrop.ui.IosDialog;

import org.json.JSONObject;

import java.util.concurrent.Executor;

/** Owns old-device pairing decisions and state for any screen receiving status events. */
public final class DeviceLinkResponder {
    public interface MessageSink {
        void show(String message);
    }

    interface Account {
        boolean isWechatAuthenticated();
        String anonymousBearer();
    }

    interface Gateway {
        void cancel(String pairingId) throws Exception;
        void complete(String pairingId, String pubkey, String bearer) throws Exception;
    }

    interface Presentation {
        void showApproval(String code, Runnable onReject);
        void dismissApproval();
        void showMessage(String message);
    }

    private final Account account;
    private final Gateway gateway;
    private final Executor executor;
    private final Presentation presentation;
    private String pendingPairingId;
    private String pendingPubkey;

    public DeviceLinkResponder(Activity activity, AuthStore auth, DeviceLinkStore store,
                               Executor executor, MessageSink messages) {
        this(accountAdapter(auth), gatewayAdapter(store), executor,
                presentationAdapter(activity, messages));
    }

    DeviceLinkResponder(Account account, Gateway gateway, Executor executor,
                        Presentation presentation) {
        this.account = account;
        this.gateway = gateway;
        this.executor = executor;
        this.presentation = presentation;
    }

    public void onRequest(String pairingId, String code, String pubkey) {
        if (account.isWechatAuthenticated()) return;
        pendingPairingId = pairingId;
        pendingPubkey = pubkey;
        presentation.showApproval(code, () -> reject(pairingId));
    }

    public void onRelease(String pairingId) {
        if (!isPending(pairingId)) return;
        if (account.isWechatAuthenticated()) {
            clearPending();
            presentation.dismissApproval();
            return;
        }
        String pubkey = pendingPubkey;
        String bearer = account.anonymousBearer();
        clearPending();
        executor.execute(() -> {
            try {
                gateway.complete(pairingId, pubkey, bearer);
                presentation.dismissApproval();
            } catch (Exception e) {
                presentation.showMessage("发送账号失败：" + e.getMessage());
            }
        });
    }

    private void reject(String pairingId) {
        if (isPending(pairingId)) clearPending();
        presentation.dismissApproval();
        executor.execute(() -> {
            try {
                gateway.cancel(pairingId);
            } catch (Exception ignored) {
            }
        });
    }

    private boolean isPending(String pairingId) {
        return pendingPairingId != null && pendingPairingId.equals(pairingId) && pendingPubkey != null;
    }

    private void clearPending() {
        pendingPairingId = null;
        pendingPubkey = null;
    }

    private static Account accountAdapter(AuthStore auth) {
        return new Account() {
            @Override public boolean isWechatAuthenticated() { return auth.isWechatAuthenticated(); }
            @Override public String anonymousBearer() { return auth.anonymousBearer(); }
        };
    }

    private static Gateway gatewayAdapter(DeviceLinkStore store) {
        return new Gateway() {
            @Override public void cancel(String pairingId) throws Exception {
                store.cancel(pairingId);
            }

            @Override public void complete(String pairingId, String pubkey, String bearer) throws Exception {
                DeviceLinkCrypto.Blob blob = DeviceLinkCrypto.encrypt(bearer, pubkey);
                JSONObject json = new JSONObject()
                        .put("epk", blob.epkB64)
                        .put("sealed", blob.sealedB64);
                store.complete(pairingId, json);
            }
        };
    }

    private static Presentation presentationAdapter(Activity activity, MessageSink messages) {
        return new Presentation() {
            private IosDialog approval;

            @Override public void showApproval(String code, Runnable onReject) {
                approval = IosDialog.showDeviceLinkApproval(activity, code, null, onReject);
            }

            @Override public void dismissApproval() {
                activity.runOnUiThread(() -> {
                    IosDialog current = approval;
                    approval = null;
                    if (current != null) current.dismiss();
                });
            }

            @Override public void showMessage(String message) { messages.show(message); }
        };
    }
}
