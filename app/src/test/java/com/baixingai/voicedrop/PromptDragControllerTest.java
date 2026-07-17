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

    @Test public void relativeMovesUseThePostRemovalIndex() {
        PromptDragController controller = new PromptDragController();
        controller.begin(Arrays.asList(action("a"), action("b"), action("c")));

        assertTrue(controller.moveRelative("a", null, "b", false));
        assertEquals(Arrays.asList("a", "b", "c"), ids(controller));
        assertTrue(controller.moveRelative("a", null, "b", true));
        assertEquals(Arrays.asList("b", "a", "c"), ids(controller));
        assertTrue(controller.moveRelative("c", null, "b", false));
        assertEquals(Arrays.asList("c", "b", "a"), ids(controller));
        assertFalse(controller.moveRelative("c", null, "c", true));
    }

    @Test public void twoItemsSwapRegardlessOfWhichHalfOfTheTargetReceivesTheDrop() {
        PromptDragController controller = new PromptDragController();
        controller.begin(Arrays.asList(action("a"), action("b")));
        assertTrue(controller.moveOnto("a", null, "b", false));
        assertEquals(Arrays.asList("b", "a"), ids(controller));

        controller.begin(Arrays.asList(action("a"), action("b")));
        assertTrue(controller.moveOnto("b", null, "a", true));
        assertEquals(Arrays.asList("b", "a"), ids(controller));
    }

    private static java.util.List<String> ids(PromptDragController controller) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (PromptNode node : controller.draft()) result.add(node.id);
        return result;
    }

    private static PromptNode action(String id) {
        PromptNode n = new PromptNode(); n.id = id; n.type = "action"; n.label = id; n.origin = "user"; n.prompt = id; n.appliesTo.add("text"); return n;
    }
    private static PromptNode group(String id) {
        PromptNode n = new PromptNode(); n.id = id; n.type = "group"; n.label = id; n.origin = "user"; return n;
    }
}
