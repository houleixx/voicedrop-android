package com.baixingai.voicedrop;

import android.app.Application;
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
    }
}
