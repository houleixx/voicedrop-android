package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public final class PrivacyConsentDialogSourceTest {
    @Test
    public void dialogIsRequiredAndLinksThePublishedPolicy() throws Exception {
        String dialog = readRoot("app/src/main/java/com/baixingai/voicedrop/ui/PrivacyConsentDialog.java");
        String ios = readRoot("app/src/main/java/com/baixingai/voicedrop/ui/IosDialog.java");

        assertTrue(dialog.contains("隐私保护提示"));
        assertTrue(dialog.contains("同意并继续"));
        assertTrue(dialog.contains("不同意并退出"));
        assertTrue(dialog.contains("PrivacyPolicyActivity.open(activity)"));
        assertTrue(dialog.contains("LinkMovementMethod.getInstance()"));
        assertTrue(dialog.contains("IosDialog.showRequiredChoice"));
        assertTrue(ios.contains("dialog.setCancelable(false)"));
        assertTrue(ios.contains("dialog.setCanceledOnTouchOutside(false)"));
    }

    private static String readRoot(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..", relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
