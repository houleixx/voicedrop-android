package com.baixingai.voicedrop.data;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountLocalStateTest {
    @Test public void accountChangesDropEveryPendingCrossAccountQueue() {
        assertTrue(AccountLocalState.pendingWorkPreferenceNames().containsAll(Arrays.asList(
                "voicedrop.editqueue",
                "voicedrop.commandqueue",
                "voicedrop.commandstate",
                "voicedrop.pending_replies",
                "voicedrop.pending_community_share",
                PhotoMarkerRepairStore.PREFS
        )));
    }

    @Test public void accountDeletionAlsoDropsAccountCachesAndLocalSettings() {
        assertTrue(AccountLocalState.deletedAccountPreferenceNames().containsAll(Arrays.asList(
                "voicedrop.auth",
                "voicedrop.prompts",
                "voicedrop.prefs",
                "voicedrop.referral",
                WritingStyleHistoryCache.PREFS,
                "vd_community_terms",
                "vd_block_store"
        )));
        assertTrue(AccountLocalState.deletedAccountPreferenceNames()
                .containsAll(AccountLocalState.pendingWorkPreferenceNames()));
    }

    @Test public void accountDeletionRemovesNestedLocalFilesAndCaches() throws Exception {
        File root = Files.createTempDirectory("voicedrop-account-delete").toFile();
        File nested = new File(root, "pending/photos");
        assertTrue(nested.mkdirs());
        Files.write(new File(nested, "old.jpg").toPath(), new byte[]{1, 2, 3});

        assertTrue(AccountLocalState.deleteRecursively(root));
        assertFalse(root.exists());
    }
}
