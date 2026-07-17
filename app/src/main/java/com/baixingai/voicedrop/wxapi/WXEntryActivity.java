package com.baixingai.voicedrop.wxapi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.baixingai.voicedrop.RecordingsActivity;
import com.baixingai.voicedrop.data.AuthStore;
import com.baixingai.voicedrop.data.PendingCommunityShareStore;
import com.baixingai.voicedrop.data.WechatAuthStore;
import com.baixingai.voicedrop.data.WechatLogin;
import com.baixingai.voicedrop.net.HttpClient;
import com.baixingai.voicedrop.ui.IosDialog;
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
                    if (!result.ok) {
                        clearPendingCommunityShare();
                        toast("微信登录失败：" + message(result));
                        keepCurrentAccount();
                        return;
                    }
                    if (result.requiresAccountSwitch(auth.anonId())) {
                        showAccountSwitchConfirmation(auth, result);
                    } else {
                        completeLogin(auth, result);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    clearPendingCommunityShare();
                    toast("微信登录失败：" + e.getMessage());
                    keepCurrentAccount();
                });
            }
        });
    }

    private String message(WechatAuthStore.Result result) {
        return result.detail == null || result.detail.isEmpty() ? result.error : result.detail;
    }

    private void showAccountSwitchConfirmation(AuthStore auth, WechatAuthStore.Result result) {
        IosDialog.showConfirmation(this, "该微信已关联另一个云端空间",
                "是否切换到微信已绑定的云端空间？当前空间会保存在本机，退出微信登录后会恢复当前空间。",
                "切换到微信空间", () -> completeSwitchedLogin(auth, result),
                "保留当前空间", this::keepCurrentAccount);
    }

    private void completeLogin(AuthStore auth, WechatAuthStore.Result result) {
        if (!auth.storeSession(result.session)) {
            clearPendingCommunityShare();
            toast("微信登录失败：无效会话");
            keepCurrentAccount();
            return;
        }
        boolean fromCommunityShare = new PendingCommunityShareStore(this).peek() != null;
        clearPendingCommunityShare();
        openRecordings(fromCommunityShare
                ? "已登录微信，请重新选择文章分享"
                : "已登录微信");
    }

    private void completeSwitchedLogin(AuthStore auth, WechatAuthStore.Result result) {
        if (!auth.switchToWechatAccount(result.session)) {
            clearPendingCommunityShare();
            toast("微信登录失败：无效会话");
            keepCurrentAccount();
            return;
        }
        boolean fromCommunityShare = new PendingCommunityShareStore(this).peek() != null;
        clearPendingCommunityShare();
        openRecordings(fromCommunityShare
                ? "已切换到微信空间，请重新选择文章分享"
                : "已切换到微信空间");
    }

    private void openRecordings(String message) {
        toast(message);
        Intent intent = new Intent(this, RecordingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void clearPendingCommunityShare() {
        new PendingCommunityShareStore(this).clear();
    }

    private void keepCurrentAccount() {
        clearPendingCommunityShare();
        finish();
    }

    private void toast(String message) {
        SimpleToast.show(this, message);
    }
}
