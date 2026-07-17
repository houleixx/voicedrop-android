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
    public void authStoreUsesWechatSessionWhileSignedInAndRestoresThePreviousAnonymousAccount() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/AuthStore.java");
        String switchAccount = methodBody(source, "public boolean switchToWechatAccount");
        String signOut = methodBody(source, "public void signOutWechat");

        assertTrue(source.contains("public String anonymousBearer()"));
        assertTrue(methodBody(source, "public String bearer").contains("session.isEmpty() ? anonymousBearer() : session"));
        assertTrue(methodBody(source, "public String communityBearer").contains("return bearer();"));
        assertTrue(source.contains("PRE_WECHAT_ANON"));
        assertTrue(switchAccount.contains("putString(PRE_WECHAT_ANON, anonymousBearer())"));
        assertTrue(switchAccount.contains("putString(SESSION, token)"));
        assertTrue(signOut.contains("getString(PRE_WECHAT_ANON"));
        assertTrue(signOut.contains("putString(ANON, previous)"));
        assertTrue(signOut.contains("remove(PRE_WECHAT_ANON)"));
        assertTrue(signOut.contains("remove(SESSION)"));
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

    @Test
    public void accountSwitchConfirmationOffersSwitchAndKeepActions() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/wxapi/WXEntryActivity.java");
        String confirmation = methodBody(source, "private void showAccountSwitchConfirmation");
        String switchedLogin = methodBody(source, "private void completeSwitchedLogin");

        assertTrue(confirmation.contains("切换到微信空间"));
        assertTrue(confirmation.contains("保留当前空间"));
        assertTrue(confirmation.contains("completeSwitchedLogin(auth, result)"));
        assertTrue(switchedLogin.contains("auth.switchToWechatAccount(result.session)"));
    }

    @Test
    public void onlyAnonymousAccountTokenCanBeImportedAndReceivedByDeviceLink() throws Exception {
        String auth = readSource("src/main/java/com/baixingai/voicedrop/data/AuthStore.java");
        String link = readSource("src/main/java/com/baixingai/voicedrop/data/DeviceLinkSession.java");

        assertFalse(auth.contains("public boolean adoptCredential(String credential)"));
        assertTrue(link.contains("auth.adoptToken(token)"));
    }

    @Test
    public void ownerScopeCacheIsBoundToTheCurrentCredential() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/LibraryStore.java");
        String method = methodBody(source, "public String ownerScope");

        assertTrue(source.contains("private String cachedScopeToken"));
        assertTrue(method.contains("String token = auth.bearer()"));
        assertTrue(method.contains("token.equals(cachedScopeToken)"));
        assertTrue(method.contains("cachedScopeToken = token"));
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
