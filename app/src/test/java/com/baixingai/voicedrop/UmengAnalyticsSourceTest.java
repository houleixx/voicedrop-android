package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class UmengAnalyticsSourceTest {
    @Test
    public void gradleInjectsUmengAppKeyWithoutHardcodingIt() throws Exception {
        String gradle = readRoot("app/build.gradle");

        assertTrue(gradle.contains("UMENG_APP_KEY"));
        assertTrue(gradle.contains("local.properties"));
        assertTrue(gradle.contains("buildConfigField \"String\", \"UMENG_APP_KEY\""));
        assertTrue(gradle.contains("manifestPlaceholders"));
        assertTrue(gradle.contains("com.umeng.umsdk:common:9.9.2"));
        assertTrue(gradle.contains("com.umeng.umsdk:asms:1.8.7.2"));
        assertFalse(gradle.matches("(?s).*buildConfigField \"String\", \"UMENG_APP_KEY\", \"\\\"[0-9a-f]{24,}\\\"\".*"));
    }

    @Test
    public void applicationInitializesUmengOnlyWhenKeyIsInjected() throws Exception {
        String source = readRoot("app/src/main/java/com/baixingai/voicedrop/VoiceDropApplication.java");
        String onCreate = methodBody(source, "public void onCreate()");

        assertTrue(source.contains("UMConfigure.preInit"));
        assertTrue(source.contains("UMConfigure.init"));
        assertTrue(source.contains("MobclickAgent.setPageCollectionMode"));
        assertTrue(source.contains("TextUtils.isEmpty(BuildConfig.UMENG_APP_KEY)"));
        assertTrue(onCreate.contains("new PrivacyConsent(this).isAccepted()"));
        assertTrue(onCreate.contains("activateConsentedServices();"));
        assertFalse(onCreate.contains("UMConfigure.init"));
        assertFalse(onCreate.contains("new ReferralManager(this).runOnLaunch()"));
        assertTrue(source.contains("public synchronized void activateConsentedServices()"));
        assertTrue(source.contains("if (consentedServicesActivated) return;"));
        assertTrue(source.contains("new ReferralManager(this).runOnLaunch();"));
        assertFalse(source.matches("(?s).*[0-9a-f]{24,}.*"));
    }

    @Test
    public void releaseWorkflowRequiresUmengAppKeySecret() throws Exception {
        String workflow = readRoot(".github/workflows/release-apk.yml");

        assertTrue(workflow.contains("UMENG_APP_KEY: ${{ secrets.UMENG_APP_KEY }}"));
        assertTrue(workflow.contains("test -n \"$UMENG_APP_KEY\""));
    }

    private static String readRoot(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..", relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(open + 1, i);
        }
        return "";
    }
}
