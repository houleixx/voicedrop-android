package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class AccountTokenImportDialogSourceTest {
    @Test
    public void tokenImportDialogKeepsTheActionCloseAndFocusesTheInput() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/AccountActivity.java");
        String method = methodBody(source, "private void showTokenImport");

        assertTrue(method.contains("IosDialog dialog = IosDialog.showBottomSheet(this, \"登录已有账号\", form, 300"));
        assertTrue(method.contains("input.requestFocus()"));
        assertTrue(method.contains("SOFT_INPUT_STATE_ALWAYS_VISIBLE"));
        assertTrue(method.contains("keyboard.showSoftInput(input"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new IllegalArgumentException("Missing " + signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(brace, i + 1);
        }
        throw new IllegalArgumentException("Unclosed method " + signature);
    }
}
