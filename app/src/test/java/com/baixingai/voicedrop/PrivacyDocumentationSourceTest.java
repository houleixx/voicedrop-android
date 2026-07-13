package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PrivacyDocumentationSourceTest {
    @Test
    public void policyOpensInsideAppWithRestrictedWebView() throws Exception {
        String about = readRoot("app/src/main/java/com/baixingai/voicedrop/AboutActivity.java");
        String dialog = readRoot("app/src/main/java/com/baixingai/voicedrop/ui/PrivacyConsentDialog.java");
        String activity = readRoot("app/src/main/java/com/baixingai/voicedrop/PrivacyPolicyActivity.java");
        String manifest = readRoot("app/src/main/AndroidManifest.xml");

        assertTrue(about.contains("PrivacyPolicyActivity.open(this)"));
        assertFalse(about.contains("Intent.ACTION_VIEW"));
        assertFalse(about.contains("录音只上传到你自己的云端空间"));
        assertTrue(dialog.contains("PrivacyPolicyActivity.open(activity)"));
        assertFalse(dialog.contains("Intent.ACTION_VIEW"));
        assertTrue(activity.contains("new WebView(this)"));
        assertTrue(activity.contains("setJavaScriptEnabled(false)"));
        assertTrue(activity.contains("setAllowFileAccess(false)"));
        assertTrue(activity.contains("setAllowContentAccess(false)"));
        assertTrue(activity.contains("PrivacyConsent.POLICY_URL.equals"));
        assertTrue(activity.contains("webView.loadUrl(PrivacyConsent.POLICY_URL)"));
        assertTrue(manifest.contains("android:name=\".PrivacyPolicyActivity\""));
        assertTrue(manifest.contains("android:exported=\"false\""));
    }

    @Test
    public void readmeDoesNotDocumentUmeng() throws Exception {
        String readme = readRoot("README.md");

        assertFalse(readme.contains("## 友盟统计"));
        assertFalse(readme.contains("umeng.appKey"));
        assertFalse(readme.contains("UMENG_APP_KEY"));
    }

    @Test
    public void policyPageSlidesInFromRightAndSlidesBackOut() throws Exception {
        String about = readRoot("app/src/main/java/com/baixingai/voicedrop/AboutActivity.java");
        String dialog = readRoot("app/src/main/java/com/baixingai/voicedrop/ui/PrivacyConsentDialog.java");
        String activity = readRoot("app/src/main/java/com/baixingai/voicedrop/PrivacyPolicyActivity.java");
        String slideIn = readRoot("app/src/main/res/anim/slide_in_right.xml");
        String slideOut = readRoot("app/src/main/res/anim/slide_out_right.xml");

        assertTrue(about.contains("PrivacyPolicyActivity.open(this)"));
        assertTrue(dialog.contains("PrivacyPolicyActivity.open(activity)"));
        assertTrue(activity.contains("R.anim.slide_in_right"));
        assertTrue(activity.contains("R.anim.stay"));
        assertTrue(activity.contains("R.anim.slide_out_right"));
        assertTrue(slideIn.contains("android:fromXDelta=\"100%\""));
        assertTrue(slideIn.contains("android:toXDelta=\"0%\""));
        assertTrue(slideOut.contains("android:fromXDelta=\"0%\""));
        assertTrue(slideOut.contains("android:toXDelta=\"100%\""));
    }

    @Test
    public void policyBackButtonMatchesOtherTopLevelPages() throws Exception {
        String activity = readRoot("app/src/main/java/com/baixingai/voicedrop/PrivacyPolicyActivity.java");

        assertTrue(activity.contains("top.setPadding(dp(12), dp(14) + statusBarHeight(), dp(16), dp(10))"));
        assertTrue(activity.contains("FrameLayout backTouch = new FrameLayout(this)"));
        assertTrue(activity.contains("GradientDrawable backBg = new GradientDrawable()"));
        assertTrue(activity.contains("backBg.setColor(Theme.CARD)"));
        assertTrue(activity.contains("backBg.setCornerRadius(dp(11))"));
        assertTrue(activity.contains("backBg.setStroke(dp(1), 0xffe0d8cc)"));
        assertTrue(activity.contains("back.setElevation(dp(2))"));
        assertTrue(activity.contains("dp(40), dp(40), Gravity.CENTER"));
        assertTrue(activity.contains("dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL"));
    }

    @Test
    public void settingsPageTitlesAreVerticallyCentered() throws Exception {
        String[] pages = {
                "SettingsActivity.java",
                "AccountActivity.java",
                "AboutActivity.java",
                "UsageActivity.java",
                "InstructionSettingsActivity.java"
        };

        for (String page : pages) {
            String activity = readRoot("app/src/main/java/com/baixingai/voicedrop/" + page);
            assertTrue(page, activity.contains("topTitle.setGravity(Gravity.CENTER)"));
        }
    }

    private static String readRoot(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..", relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
