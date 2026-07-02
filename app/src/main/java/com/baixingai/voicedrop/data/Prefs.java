package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class Prefs {
    private static final String PREFS = "voicedrop.prefs";
    private static final String DELETE_LOCAL = "deleteLocalAfterUpload";
    private static final String LIKED_COMMUNITY_POSTS = "likedCommunityPosts";
    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean deleteLocalAfterUpload() {
        return prefs.getBoolean(DELETE_LOCAL, true);
    }

    public void setDeleteLocalAfterUpload(boolean value) {
        prefs.edit().putBoolean(DELETE_LOCAL, value).apply();
    }

    public boolean likedCommunityPost(String shareId) {
        if (shareId == null || shareId.isEmpty()) return false;
        return prefs.getStringSet(LIKED_COMMUNITY_POSTS, new HashSet<>()).contains(shareId);
    }

    public void setLikedCommunityPost(String shareId, boolean liked) {
        if (shareId == null || shareId.isEmpty()) return;
        Set<String> current = new HashSet<>(prefs.getStringSet(LIKED_COMMUNITY_POSTS, new HashSet<>()));
        if (liked) current.add(shareId);
        else current.remove(shareId);
        prefs.edit().putStringSet(LIKED_COMMUNITY_POSTS, current).apply();
    }

    public void setLikedCommunityPosts(Set<String> shareIds) {
        Set<String> next = shareIds == null ? new HashSet<>() : new HashSet<>(shareIds);
        prefs.edit().putStringSet(LIKED_COMMUNITY_POSTS, next).apply();
    }
}
