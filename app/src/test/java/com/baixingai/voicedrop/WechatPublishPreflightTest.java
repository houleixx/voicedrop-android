package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.SettingsStore;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WechatPublishPreflightTest {
    @Test
    public void bindStatusOnlyAcceptsSuccessfulConnectedResponse() {
        assertTrue(SettingsStore.wechatConnected(200, "{\"connected\":true}"));
        assertFalse(SettingsStore.wechatConnected(200, "{\"connected\":false}"));
        assertFalse(SettingsStore.wechatConnected(503, "{\"connected\":true}"));
        assertFalse(SettingsStore.wechatConnected(200, "not-json"));
    }

    @Test
    public void publishingUsesBindStatusInsteadOfLegacyCredentialFields() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("settingsStore.isWechatConnected()"));
        assertFalse(source.contains("settingsStore.loadWechat()"));
    }

    @Test
    public void publishingShowsBlockingLoadingAndPreventsDuplicateRequests() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(source.contains("wechatPublishInProgress.compareAndSet(false, true)"));
        assertTrue(source.contains("WechatShareLoadingDialog.show(this, \"正在发布...\")"));
        assertTrue(source.contains("finishWechatPublishing();"));
        assertFalse(source.contains("toast(\"正在发布到公众号…\")"));
        assertTrue(source.contains("\"已更新草稿\""));
        assertTrue(source.contains("\"已到草稿箱\""));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
