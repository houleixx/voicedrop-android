package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.ui.PromptDragController;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PromptDragControllerTest {
    @Test public void movesAcrossTopLevelAndGroupsAndCanCancel() {
        PromptNode a = action("a");
        PromptNode b = action("b");
        PromptNode group = group("g");
        PromptDragController controller = new PromptDragController();
        controller.begin(Arrays.asList(a, b, group));
        assertTrue(controller.move("b", null, 0));
        assertEquals("b", controller.draft().get(0).id);
        assertTrue(controller.move("a", "g", 0));
        assertEquals("a", controller.draft().get(1).children.get(0).id);
        assertFalse(controller.move("g", "g", 0));
        controller.cancel();
        assertEquals("a", controller.draft().get(0).id);
    }

    private static PromptNode action(String id) {
        PromptNode n = new PromptNode(); n.id = id; n.type = "action"; n.label = id; n.origin = "user"; n.prompt = id; n.appliesTo.add("text"); return n;
    }
    private static PromptNode group(String id) {
        PromptNode n = new PromptNode(); n.id = id; n.type = "group"; n.label = id; n.origin = "user"; return n;
    }
}
