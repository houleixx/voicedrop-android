package com.baixingai.voicedrop.wxapi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.baixingai.voicedrop.data.WechatLogin;
import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;

/** SDK return only resumes the app; server notifications and queries authorize all grants. */
public final class WXPayEntryActivity extends Activity implements IWXAPIEventHandler {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        handle(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }
    private void handle(Intent intent) {
        if (!WechatLogin.api(this).handleIntent(intent, this)) finish();
    }
    @Override public void onReq(BaseReq req) { finish(); }
    @Override public void onResp(BaseResp resp) { finish(); }
}
