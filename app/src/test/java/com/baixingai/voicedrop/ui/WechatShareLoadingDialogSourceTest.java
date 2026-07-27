package com.baixingai.voicedrop.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class WechatShareLoadingDialogSourceTest {
    @Test
    public void loadingOverlayUsesWeChatStyleDarkCardAndWhiteSpinner() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ui/WechatShareLoadingDialog.java");

        assertTrue(source.contains("0xdd202020"));
        assertTrue(source.contains("Color.WHITE"));
        assertTrue(source.contains("window.setLayout(dp(context, 120), dp(context, 120))"));
        assertTrue(source.contains("card.setGravity(Gravity.CENTER)"));
        assertTrue(source.contains("card.setMinimumHeight(dp(context, 120))"));
        assertTrue(source.contains("textParams.topMargin = dp(context, 8)"));
        assertTrue(source.contains("加载中..."));
        assertTrue(source.contains("window.setGravity(Gravity.CENTER)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
