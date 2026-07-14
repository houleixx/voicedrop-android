package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.ui.PromptListPresentation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PromptListPresentationTest {
    @Test public void groupsStartCollapsedAndExposeTheirChildCount() {
        List<PromptListPresentation.Row> rows = PromptListPresentation.rows(
                Arrays.asList(group("g", "图片风格", action("a", "卡通", "image")), action("top", "简洁", "text")),
                Collections.emptySet());

        assertEquals(Arrays.asList("g", "top"), ids(rows));
        assertTrue(rows.get(0).group);
        assertEquals(1, rows.get(0).childCount);
        assertFalse(rows.get(0).expanded);
    }

    @Test public void expandedGroupInsertsChildrenDirectlyBelowItsHeader() {
        PromptNode group = group("g", "图片风格", action("a", "卡通", "image"), action("b", "广告", "image"));

        List<PromptListPresentation.Row> rows = PromptListPresentation.rows(
                Arrays.asList(group, group("g2", "改写这段", action("c", "精简", "text"))),
                new HashSet<>(Collections.singletonList("g")));

        assertEquals(Arrays.asList("g", "a", "b", "g2"), ids(rows));
        assertTrue(rows.get(0).expanded);
        assertEquals(1, rows.get(1).depth);
        assertEquals("仅图片", rows.get(1).appliesLabel);
        assertEquals("仅文字", PromptListPresentation.appliesLabel(action("t", "润色", "text")));
    }

    private static List<String> ids(List<PromptListPresentation.Row> rows) {
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        for (PromptListPresentation.Row row : rows) ids.add(row.node.id);
        return ids;
    }

    private static PromptNode group(String id, String label, PromptNode... children) {
        PromptNode node = new PromptNode(); node.id = id; node.type = "group"; node.label = label; node.origin = "system";
        node.children.addAll(Arrays.asList(children)); return node;
    }

    private static PromptNode action(String id, String label, String appliesTo) {
        PromptNode node = new PromptNode(); node.id = id; node.type = "action"; node.label = label; node.origin = "system";
        node.appliesTo.add(appliesTo); return node;
    }
}
