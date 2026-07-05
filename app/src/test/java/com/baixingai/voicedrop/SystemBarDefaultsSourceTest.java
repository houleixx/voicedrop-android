package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class SystemBarDefaultsSourceTest {
    @Test
    public void appThemeProvidesLightTransparentSystemBarDefaults() throws Exception {
        String source = readSource("src/main/res/values/styles.xml");

        assertTrue(source.contains("<item name=\"android:statusBarColor\">@android:color/transparent</item>"));
        assertTrue(source.contains("<item name=\"android:navigationBarColor\">@android:color/transparent</item>"));
        assertTrue(source.contains("<item name=\"android:windowLightStatusBar\">true</item>"));
        assertTrue(source.contains("<item name=\"android:windowLightNavigationBar\">true</item>"));
        assertTrue(source.contains("<item name=\"android:enforceStatusBarContrast\">false</item>"));
        assertTrue(source.contains("<item name=\"android:enforceNavigationBarContrast\">false</item>"));
        assertTrue(source.contains("<item name=\"android:windowNoTitle\">true</item>"));
        assertTrue(source.contains("<item name=\"android:windowActionBar\">false</item>"));
    }

    @Test
    public void sharedWindowDefaultsExistForActivitiesAndDialogs() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/SystemBarDefaults.java");

        assertTrue(source.contains("public static void applyLightActivity"));
        assertTrue(source.contains("window.setStatusBarColor(Color.TRANSPARENT)"));
        assertTrue(source.contains("window.setNavigationBarColor(navigationBarColor)"));
        assertTrue(source.contains("window.setStatusBarContrastEnforced(false)"));
        assertTrue(source.contains("window.setNavigationBarContrastEnforced(false)"));
        assertTrue(source.contains("View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR"));
        assertTrue(source.contains("View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR"));
    }

    @Test
    public void applicationReappliesSystemBarDefaultsForOpenedActivities() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/VoiceDropApplication.java");

        assertTrue(source.contains("registerActivityLifecycleCallbacks"));
        assertTrue(source.contains("SystemBarDefaults.applyLightActivity"));
        assertTrue(source.contains("if (activity instanceof InsertPhotoActivity) return"));
        assertTrue(source.contains("onActivityCreated"));
        assertTrue(source.contains("onActivityResumed"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
