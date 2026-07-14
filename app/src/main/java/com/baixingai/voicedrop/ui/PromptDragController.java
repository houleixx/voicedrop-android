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

    public List<PromptNode> draft() { return PromptTree.copy(draft); }
    public List<String> baseline() { return new ArrayList<>(baseline); }

    public void cancel() { draft = PromptTree.copy(original); }
}
