package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.core.PromptTree;
import com.baixingai.voicedrop.data.UIConfigStore;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PromptTreeTest {
    private static final String RESOLVED_PROMPTS = "{\"schema\":1,\"items\":["
            + "{\"id\":\"sys_rewrite\",\"type\":\"group\",\"label\":\"改写\",\"origin\":\"system\",\"children\":["
            + "{\"id\":\"sys_concise\",\"type\":\"action\",\"label\":\"精简\",\"origin\":\"system\",\"prompt\":\"精简内容\",\"appliesTo\":[\"text\"]}]}]}";

    @Test
    public void systemGroupSerializesAsRefWithChildren() throws Exception {
        List<PromptNode> nodes = PromptTree.decode(RESOLVED_PROMPTS);
        JSONObject raw = new JSONObject(PromptTree.encodeRaw(nodes));
        JSONObject group = raw.getJSONArray("items").getJSONObject(0);
        assertEquals("sys_rewrite", group.getString("ref"));
        assertEquals("sys_concise", group.getJSONArray("children").getJSONObject(0).getString("ref"));
        assertFalse(group.has("origin"));
    }

    @Test
    public void unknownResolvedFieldsAreIgnored() throws Exception {
        PromptNode node = PromptNode.fromResolved(new JSONObject(
                "{\"id\":\"p_12345678\",\"type\":\"action\",\"label\":\"A\",\"origin\":\"user\",\"prompt\":\"P\",\"appliesTo\":[\"text\"],\"future\":1}"));
        assertEquals("p_12345678", node.id);
    }

    @Test
    public void editingSystemNodeForksInPlace() {
        PromptNode source = action("sys_concise", "精简", "system", "text");
        PromptNode fork = PromptTree.fork(source, () -> "p_abcdefgh");
        assertEquals("p_abcdefgh", fork.id);
        assertEquals("sys_concise", fork.forkedFrom);
        assertEquals("custom", fork.origin);
        assertEquals("精简", fork.label);
    }

    @Test
    public void replaceAndRemoveWorkInsideGroups() {
        PromptNode group = group("g", action("a", "A", "user", "text"));
        PromptNode replacement = action("b", "B", "user", "text");
        PromptTree.MutationResult replaced = PromptTree.replace(java.util.Collections.singletonList(group), "a", replacement);
        assertTrue(replaced.changed);
        assertEquals("b", replaced.items.get(0).children.get(0).id);
        PromptTree.MutationResult removed = PromptTree.remove(replaced.items, "b");
        assertEquals("b", removed.removed.id);
        assertTrue(removed.items.get(0).children.isEmpty());
    }

    @Test
    public void groupCannotMoveIntoAnotherGroup() {
        PromptNode first = group("g1", action("a", "A", "user", "text"));
        PromptNode second = group("g2");
        assertNull(PromptTree.move(java.util.Arrays.asList(first, second), "g1", "g2", 0));
    }

    @Test
    public void pastedEightDigitsAreRejected() {
        assertNull(PromptTree.extractShareCode("12345678"));
        assertEquals("12", PromptTree.mergeCodeInput("12", "12345678"));
        assertEquals("7654321", PromptTree.extractShareCode("https://jianshuo.dev/p/7654321"));
    }

    @Test
    public void importedShareCodeSurvivesResolvedAndRawTrees() throws Exception {
        PromptNode imported = PromptNode.fromResolved(new JSONObject(
                "{\"id\":\"p_12345678\",\"type\":\"action\",\"label\":\"共享\",\"origin\":\"user\","
                        + "\"prompt\":\"P\",\"appliesTo\":[\"text\"],\"importedFrom\":\"3295225\"}"));
        PromptNode nested = group("g", imported);

        assertEquals("3295225", imported.importedFrom);
        assertTrue(PromptTree.containsImport(java.util.Collections.singletonList(nested), "3295225"));
        JSONObject rawItem = new JSONObject(PromptTree.encodeRaw(java.util.Collections.singletonList(imported)))
                .getJSONArray("items").getJSONObject(0);
        assertEquals("3295225", rawItem.getString("importedFrom"));
        PromptNode restored = PromptTree.decode(PromptTree.encodeResolved(
                java.util.Collections.singletonList(imported))).get(0);
        assertEquals("3295225", restored.importedFrom);
    }

    @Test
    public void imageMenuContainsOnlyImageActions() {
        PromptNode style = group("style",
                action("sys_cartoon", "卡通", "system", "image"),
                action("sys_concise", "精简", "system", "text"));
        UIConfigStore.MenuConfig menu = PromptTree.menu(java.util.Collections.singletonList(style), "image");
        assertEquals(1, menu.groups.size());
        assertEquals("sys_cartoon", menu.groups.get(0).get(0).children.get(0).id);
        assertEquals("user", menu.groups.get(0).get(0).origin);
        assertEquals("system", menu.groups.get(0).get(0).children.get(0).origin);
        assertEquals(1, menu.groups.get(0).get(0).children.size());
    }

    private static PromptNode action(String id, String label, String origin, String appliesTo) {
        PromptNode node = new PromptNode();
        node.id = id;
        node.type = "action";
        node.label = label;
        node.origin = origin;
        node.prompt = "prompt-" + id;
        node.appliesTo.add(appliesTo);
        return node;
    }

    private static PromptNode group(String id, PromptNode... children) {
        PromptNode node = new PromptNode();
        node.id = id;
        node.type = "group";
        node.label = id;
        node.origin = "user";
        node.children.addAll(java.util.Arrays.asList(children));
        return node;
    }
}
