package com.baixingai.voicedrop.ui;

import com.baixingai.voicedrop.core.PromptNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PromptListPresentation {
    public static final class Row {
        public final PromptNode node;
        public final int depth;
        public final boolean group;
        public final int childCount;
        public final boolean expanded;
        public final String appliesLabel;

        private Row(PromptNode node, int depth, boolean expanded) {
            this.node = node;
            this.depth = depth;
            this.group = node.isGroup();
            this.childCount = node.children.size();
            this.expanded = expanded;
            this.appliesLabel = appliesLabel(node);
        }
    }

    public static List<Row> rows(List<PromptNode> items, Set<String> expandedGroups) {
        List<Row> result = new ArrayList<>();
        for (PromptNode node : items) {
            boolean expanded = node.isGroup() && expandedGroups.contains(node.id);
            result.add(new Row(node, 0, expanded));
            if (expanded) {
                for (PromptNode child : node.children) result.add(new Row(child, 1, false));
            }
        }
        return result;
    }

    public static String appliesLabel(PromptNode node) {
        boolean text = node.appliesTo.contains("text");
        boolean image = node.appliesTo.contains("image");
        if (text && image) return "文字+图片";
        if (image) return "仅图片";
        if (text) return "仅文字";
        return "";
    }

    private PromptListPresentation() {}
}
