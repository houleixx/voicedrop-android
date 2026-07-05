package com.baixingai.voicedrop.ui;

import android.view.Window;

public final class DialogWindowDefaults {
    private DialogWindowDefaults() {}

    public static void applyModal(Window window, int statusBarColor, int navigationBarColor, boolean lightNavigationBar) {
        SystemBarDefaults.applyModal(window, statusBarColor, navigationBarColor, lightNavigationBar);
    }

    public static void applyFullscreen(Window window) {
        SystemBarDefaults.applyFullscreen(window);
    }
}
