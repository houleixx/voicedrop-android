package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Local block list for community posts — stored on-device only (SharedPreferences).
 * Matches iOS {@code BlockStore}: blocking never touches the server; the community
 * feed filters blocked authors client-side.
 */
public final class BlockStore {
    private static final String KEY = "vd.blockedAuthors";
    private final SharedPreferences prefs;

    public BlockStore(Context context) {
        this.prefs = context.getSharedPreferences("vd_block_store", Context.MODE_PRIVATE);
    }

    /** Returns the set of blocked author names. */
    public Set<String> blocked() {
        return Collections.unmodifiableSet(prefs.getStringSet(KEY, Collections.<String>emptySet()));
    }

    /** Returns true if the given author name is blocked. */
    public boolean isBlocked(String author) {
        if (author == null || author.isEmpty()) return false;
        return blocked().contains(author);
    }

    /** Add an author to the block list. */
    public void block(String author) {
        if (author == null || author.isEmpty()) return;
        Set<String> current = new HashSet<>(blocked());
        current.add(author);
        prefs.edit().putStringSet(KEY, current).apply();
    }

    /** Remove an author from the block list. */
    public void unblock(String author) {
        Set<String> current = new HashSet<>(blocked());
        current.remove(author);
        prefs.edit().putStringSet(KEY, current).apply();
    }

    /** Returns a sorted list of blocked authors (for display). */
    public java.util.List<String> blockedList() {
        java.util.List<String> list = new java.util.ArrayList<>(blocked());
        java.util.Collections.sort(list);
        return list;
    }
}
