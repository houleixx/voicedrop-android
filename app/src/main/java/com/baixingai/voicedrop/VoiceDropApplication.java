package com.baixingai.voicedrop;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.kongzue.dialogx.DialogX;
import com.kongzue.dialogx.style.IOSStyle;
import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;

public class VoiceDropApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        initUmengAnalytics();
        DialogX.init(this);
        DialogX.globalStyle = IOSStyle.style();
        DialogX.globalTheme = DialogX.THEME.LIGHT;
        DialogX.DEBUGMODE = false;
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                applySystemBarDefaults(activity);
            }

            @Override public void onActivityResumed(Activity activity) {
                applySystemBarDefaults(activity);
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private void initUmengAnalytics() {
        if (TextUtils.isEmpty(BuildConfig.UMENG_APP_KEY)) return;
        UMConfigure.preInit(this, BuildConfig.UMENG_APP_KEY, BuildConfig.UMENG_CHANNEL);
        UMConfigure.init(this, BuildConfig.UMENG_APP_KEY, BuildConfig.UMENG_CHANNEL, UMConfigure.DEVICE_TYPE_PHONE, null);
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO);
    }

    private void applySystemBarDefaults(Activity activity) {
        if (activity instanceof InsertPhotoActivity) return;
        SystemBarDefaults.applyLightActivity(activity.getWindow(), android.graphics.Color.TRANSPARENT, true);
    }
}
