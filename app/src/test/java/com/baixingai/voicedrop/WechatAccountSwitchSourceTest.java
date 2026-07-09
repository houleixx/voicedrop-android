package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WechatAccountSwitchSourceTest {
    @Test
    public void authStoreUsesSessionFirstAndPreservesAnonymousCredential() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/AuthStore.java");

        assertTrue(source.contains("public String anonymousBearer()"));
        assertTrue(source.contains("String session = session();"));
        assertTrue(source.contains("return session.isEmpty() ? anonymousBearer() : session;"));
        assertTrue(source.contains("prefs.edit().remove(SESSION).apply();"));
        assertFalse(methodBody(source, "public void signOutWechat").contains("remove(ANON)"));
    }

    @Test
    public void codeExchangeUsesAnonymousCredentialAndDefersSessionStorage() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/WechatAuthStore.java");
        String exchange = methodBody(source, "public Result exchangeCode");

        assertTrue(exchange.contains("auth.anonymousBearer()"));
        assertFalse(exchange.contains("auth.storeSession"));
        assertTrue(source.contains("public final String session;"));
        assertTrue(source.contains("public final String scope;"));
        assertTrue(source.contains("requiresAccountSwitch"));
    }

    @Test
    public void logoutReturnsToRecordingListWhereAnonymousDataReloads() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/AccountActivity.java");
        String row = methodBody(source, "private View wechatAuthRow");

        assertTrue(row.contains("auth.signOutWechat();"));
        assertTrue(row.contains("openRecordingsAfterAccountChange("));
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
