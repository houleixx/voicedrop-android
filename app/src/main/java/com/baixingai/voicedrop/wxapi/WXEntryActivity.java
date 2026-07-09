package com.baixingai.voicedrop.wxapi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.baixingai.voicedrop.AccountActivity;
import com.baixingai.voicedrop.RecordingDetailActivity;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.PendingCommunityShareStore;
import com.baixingai.voicedrop.data.WechatAuthStore;
import com.baixingai.voicedrop.data.WechatLogin;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.SimpleToast;
import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WXEntryActivity extends Activity implements IWXAPIEventHandler {
    private static final String TAG = "WechatLogin";
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WechatLogin.api(this).handleIntent(getIntent(), this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        WechatLogin.api(this).handleIntent(intent, this);
    }

    @Override
    public void onReq(BaseReq req) {
        finish();
    }

    @Override
    public void onResp(BaseResp resp) {
        if (!(resp instanceof SendAuth.Resp)) {
            clearPendingCommunityShare();
            finish();
            return;
        }
        SendAuth.Resp authResp = (SendAuth.Resp) resp;
        Log.d(TAG, "onResp errCode=" + authResp.errCode
                + " state=" + authResp.state
                + " codeLength=" + (authResp.code == null ? 0 : authResp.code.length()));
        if (authResp.errCode != BaseResp.ErrCode.ERR_OK) {
            clearPendingCommunityShare();
            toast(authResp.errCode == BaseResp.ErrCode.ERR_USER_CANCEL ? "已取消微信登录" : "微信登录失败");
            finish();
            return;
        }
        if (!WechatLogin.STATE.equals(authResp.state) || authResp.code == null || authResp.code.trim().isEmpty()) {
            clearPendingCommunityShare();
            toast("微信登录返回无效");
            finish();
            return;
        }
        exchange(authResp.code.trim());
    }

    private void exchange(String code) {
        io.execute(() -> {
            try {
                AuthStore auth = new AuthStore(this);
                WechatAuthStore.Result result = new WechatAuthStore(auth, new HttpClient())
                        .exchangeCode(code, null, null);
                runOnUiThread(() -> {
                    toast(result.ok ? "微信登录成功" : "微信登录失败：" + message(result));
                    if (!result.ok) clearPendingCommunityShare();
                    routeAfterLogin(result.ok);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    clearPendingCommunityShare();
                    toast("微信登录失败：" + e.getMessage());
                    openAccount();
                });
            }
        });
    }

    private String message(WechatAuthStore.Result result) {
        return result.detail == null || result.detail.isEmpty() ? result.error : result.detail;
    }

    private void routeAfterLogin(boolean ok) {
        if (!ok) {
            openAccount();
            return;
        }
        PendingCommunityShareStore.Pending pending = new PendingCommunityShareStore(this).peek();
        if (pending == null) {
            openAccount();
            return;
        }
        clearPendingCommunityShare();
        Intent intent = new Intent(this, RecordingDetailActivity.class);
        intent.putExtra(RecordingDetailActivity.EXTRA_AUDIO_NAME, pending.audioName);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void clearPendingCommunityShare() {
        new PendingCommunityShareStore(this).clear();
    }

    private void openAccount() {
        Intent intent = new Intent(this, AccountActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void toast(String message) {
        SimpleToast.show(this, message);
    }
}
