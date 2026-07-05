package com.baixingai.voicedrop;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;
import com.baixingai.voicedrop.ui.SystemBarDefaults;
import com.kongzue.dialogx.DialogX;
import com.kongzue.dialogx.style.IOSStyle;

public class VoiceDropApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
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

    private void applySystemBarDefaults(Activity activity) {
        if (activity instanceof InsertPhotoActivity) return;
        SystemBarDefaults.applyLightActivity(activity.getWindow(), android.graphics.Color.TRANSPARENT, true);
    }
}
