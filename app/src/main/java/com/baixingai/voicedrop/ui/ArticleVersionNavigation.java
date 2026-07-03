package com.baixingai.voicedrop.ui;

public final class ArticleVersionNavigation {
    private final int head;
    private final int[] versionHeads;
    private final boolean editing;

    public ArticleVersionNavigation(int head, int count, boolean editing) {
        this(head, sequentialHeads(count), editing);
    }

    public ArticleVersionNavigation(int head, int[] versionHeads, boolean editing) {
        this.head = head;
        this.versionHeads = versionHeads == null ? new int[0] : versionHeads.clone();
        this.editing = editing;
    }

    public boolean canUndo() {
        return undoHead() != null;
    }

    public boolean canRedo() {
        return redoHead() != null;
    }

    public Integer undoHead() {
        if (editing) return null;
        Integer target = null;
        for (int versionHead : versionHeads) {
            if (versionHead < head && (target == null || versionHead > target)) {
                target = versionHead;
            }
        }
        return target;
    }

    public Integer redoHead() {
        if (editing) return null;
        Integer target = null;
        for (int versionHead : versionHeads) {
            if (versionHead > head && (target == null || versionHead < target)) {
                target = versionHead;
            }
        }
        return target;
    }

    private static int[] sequentialHeads(int count) {
        if (count <= 0) return new int[0];
        int[] heads = new int[count];
        for (int i = 0; i < count; i++) heads[i] = i;
        return heads;
    }
}
