package com.baixingai.voicedrop.ui;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.core.PromptTree;

import java.util.ArrayList;
import java.util.List;

public final class PromptDragController {
    private List<PromptNode> original = new ArrayList<>();
    private List<PromptNode> draft = new ArrayList<>();
    private List<String> baseline = new ArrayList<>();

    public void begin(List<PromptNode> items) {
        original = PromptTree.copy(items);
        draft = PromptTree.copy(items);
        baseline = PromptTree.flattenIds(items);
    }

    public boolean move(String id, String targetGroup, int index) {
        List<PromptNode> moved = PromptTree.move(draft, id, targetGroup, index);
        if (moved == null) return false;
        draft = moved;
        return true;
    }

    /** Moves next to a visible row. The destination index is calculated after removing the dragged row. */
    public boolean moveRelative(String id, String targetGroup, String targetId, boolean after) {
        if (same(id, targetId) || find(draft, id) == null) return false;
        List<PromptNode> scope = draft;
        if (targetGroup != null) {
            PromptNode group = find(draft, targetGroup);
            if (group == null || !group.isGroup()) return false;
            scope = group.children;
        }
        int targetIndex = 0;
        boolean found = false;
        for (PromptNode node : scope) {
            if (same(node.id, id)) continue;
            if (same(node.id, targetId)) {
                found = true;
                break;
            }
            targetIndex++;
        }
        if (!found) return false;
        return move(id, targetGroup, targetIndex + (after ? 1 : 0));
    }

    /**
     * Drops onto a row. Within the same scope, direction wins over the row's half so crossing a
     * target always changes order; across scopes, the visual before/after half is preserved.
     */
    public boolean moveOnto(String id, String targetGroup, String targetId, boolean dropAfter) {
        if (same(id, targetId) || find(draft, id) == null) return false;
        boolean after = dropAfter;
        String sourceGroup = parentOf(draft, id);
        if (same(sourceGroup, targetGroup)) {
            List<PromptNode> scope = scope(draft, targetGroup);
            int from = indexOf(scope, id);
            int to = indexOf(scope, targetId);
            if (from >= 0 && to >= 0) {
                if (from < to) after = true;
                else if (from > to) after = false;
            }
        }
        return moveRelative(id, targetGroup, targetId, after);
    }

    public List<PromptNode> draft() { return PromptTree.copy(draft); }
    public List<String> baseline() { return new ArrayList<>(baseline); }

    public void cancel() { draft = PromptTree.copy(original); }

    private static PromptNode find(List<PromptNode> items, String id) {
        for (PromptNode node : items) {
            if (same(node.id, id)) return node;
            PromptNode child = find(node.children, id);
            if (child != null) return child;
        }
        return null;
    }

    private static String parentOf(List<PromptNode> items, String id) {
        for (PromptNode node : items) {
            for (PromptNode child : node.children) {
                if (same(child.id, id)) return node.id;
            }
        }
        return null;
    }

    private static List<PromptNode> scope(List<PromptNode> items, String groupId) {
        if (groupId == null) return items;
        PromptNode group = find(items, groupId);
        return group == null ? new ArrayList<>() : group.children;
    }

    private static int indexOf(List<PromptNode> items, String id) {
        for (int i = 0; i < items.size(); i++) if (same(items.get(i).id, id)) return i;
        return -1;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
