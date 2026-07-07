package com.baixingai.voicedrop.data;

import android.content.Context;

import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

public final class WechatLogin {
    public static final String APP_ID = "wx1573f936967f5420";
    public static final String SCOPE = "snsapi_userinfo";
    public static final String STATE = "voicedrop_android_wechat_login";

    private WechatLogin() {
    }

    public static IWXAPI api(Context context) {
        IWXAPI api = WXAPIFactory.createWXAPI(context.getApplicationContext(), APP_ID, false);
        api.registerApp(APP_ID);
        return api;
    }

    public static boolean start(Context context) {
        IWXAPI api = api(context);
        if (!api.isWXAppInstalled()) return false;
        SendAuth.Req req = new SendAuth.Req();
        req.scope = SCOPE;
        req.state = STATE;
        return api.sendReq(req);
    }
}
