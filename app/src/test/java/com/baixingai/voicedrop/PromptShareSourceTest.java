package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class PromptShareSourceTest {
    @Test
    public void promptSettingsSupportSharingCodesAndLandingPageUrls() throws Exception {
        String legacyStore = readSource("src/main/java/com/baixingai/voicedrop/data/UIConfigStore.java");
        String store = readSource("src/main/java/com/baixingai/voicedrop/data/PromptStore.java");
        String activity = readSource("src/main/java/com/baixingai/voicedrop/PromptEditActivity.java");
        String detail = readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java");

        assertTrue(store.contains("/prompt-share"));
        assertTrue(store.contains("/prompt-shares"));
        assertTrue(store.contains("sharing"));
        assertTrue(activity.contains("分享这条提示词"));
        assertTrue(activity.contains("Api.sharePage"));
        assertTrue(activity.contains("Intent.ACTION_SEND"));
        assertTrue(activity.contains("setOnCheckedChangeListener"));
        assertTrue(activity.contains("分享中，关闭后分享码立即失效"));
        assertTrue(activity.contains("分享的始终是已保存的版本"));
        assertTrue(activity.contains("updateShareVersionWarning"));
        assertTrue(activity.contains("state.sharing && !state.code.isEmpty()"));
        assertTrue(activity.contains("shareAction(\"复制数字\", R.drawable.ic_copy_flat"));
        assertTrue(activity.contains("shareAction(\"复制链接\", R.drawable.ic_link_flat"));
        assertTrue(activity.contains("shareAction(\"分享…\", R.drawable.ic_share_up"));
        assertTrue(activity.contains("labelParams.leftMargin = dp(4)"));
        assertFalse(activity.contains("setCompoundDrawablePadding"));
        assertTrue(activity.contains("setIncludeFontPadding(false)"));
        assertTrue(readSource("src/main/res/drawable/ic_copy_flat.xml").contains("<vector"));
        assertTrue(readSource("src/main/res/drawable/ic_link_flat.xml").contains("<vector"));
        assertFalse(legacyStore.contains("/ui-config"));
        assertTrue(detail.contains("PromptStore"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
