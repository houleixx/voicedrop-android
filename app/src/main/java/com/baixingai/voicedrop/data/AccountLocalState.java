package com.baixingai.voicedrop.data;

import android.content.Context;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AccountLocalState {
    private static final Set<String> PENDING_WORK = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "voicedrop.editqueue",
                    "voicedrop.commandqueue",
                    "voicedrop.commandstate",
                    "voicedrop.pending_replies",
                    "voicedrop.pending_community_share",
                    PhotoMarkerRepairStore.PREFS
            )));
    private static final Set<String> DELETED_ACCOUNT;

    static {
        LinkedHashSet<String> names = new LinkedHashSet<>(PENDING_WORK);
        names.addAll(Arrays.asList(
                "voicedrop.auth",
                "voicedrop.prompts",
                "voicedrop.prefs",
                "voicedrop.referral",
                "vd_community_terms",
                "vd_block_store"
        ));
        DELETED_ACCOUNT = Collections.unmodifiableSet(names);
    }

    private AccountLocalState() {}

    public static Set<String> pendingWorkPreferenceNames() {
        return PENDING_WORK;
    }

    public static Set<String> deletedAccountPreferenceNames() {
        return DELETED_ACCOUNT;
    }

    public static void clearPendingWork(Context context) {
        clear(context, PENDING_WORK);
    }

    public static void clearDeletedAccount(Context context) {
        clear(context, DELETED_ACCOUNT);
        Context app = context.getApplicationContext();
        deleteRecursively(app.getFilesDir());
        deleteRecursively(app.getCacheDir());
    }

    public static boolean deleteRecursively(File target) {
        if (target == null || !target.exists()) return true;
        if (target.isDirectory() && !Files.isSymbolicLink(target.toPath())) {
            File[] children = target.listFiles();
            if (children == null) return false;
            for (File child : children) {
                if (!deleteRecursively(child)) return false;
            }
        }
        return target.delete();
    }

    private static void clear(Context context, Set<String> names) {
        Context app = context.getApplicationContext();
        for (String name : names) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }
}
