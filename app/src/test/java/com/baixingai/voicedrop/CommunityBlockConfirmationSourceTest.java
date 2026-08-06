package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommunityBlockConfirmationSourceTest {
    @Test
    public void blockingRequiresExplicitConfirmation() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/CommunityDetailActivity.java");
        String confirm = methodBody(source, "protected void showBlockConfirm");

        assertTrue(confirm.contains("IosDialog.showConfirmation"));
        assertTrue(confirm.contains("\"确定\""));
        assertTrue(confirm.contains("\"取消\", null"));
        assertTrue(confirm.contains("blockStore.block(post.author)"));
        assertTrue(confirm.indexOf("blockStore.block(post.author)") > confirm.indexOf("\"确定\""));
        assertFalse(confirm.contains("IosDialog.show(this"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("Unclosed method: " + signature);
    }
}
