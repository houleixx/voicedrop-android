package com.baixingai.voicedrop.core;

import com.baixingai.voicedrop.data.UIConfigStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptTree {
    private static final Pattern SHARE_CODE = Pattern.compile("(?<![0-9])[1-9][0-9]{6}(?![0-9])");

    private PromptTree() {}

    public static List<PromptNode> copy(List<PromptNode> nodes) {
        List<PromptNode> out = new ArrayList<>();
        for (PromptNode node : nodes) {
            out.add(node.copy());
        }
        return out;
    }

    public static List<PromptNode> decode(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("schema", 1) > 1) {
            throw new JSONException("unsupported prompt schema");
        }
        JSONArray array = root.getJSONArray("items");
        List<PromptNode> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            out.add(PromptNode.fromResolved(array.getJSONObject(i)));
        }
        return out;
    }

    public static JSONArray rawItems(List<PromptNode> nodes) throws JSONException {
        JSONArray out = new JSONArray();
        for (PromptNode node : nodes) {
            JSONObject item = new JSONObject();
            if (node.isSystem()) {
                item.put("ref", node.id);
                if (node.isGroup()) {
                    item.put("children", rawItems(node.children));
                }
            } else {
                item.put("id", node.id);
                item.put("type", node.type);
                item.put("label", node.label);
                if (node.forkedFrom != null) {
                    item.put("forkedFrom", node.forkedFrom);
                }
                if (node.isGroup()) {
                    item.put("children", rawItems(node.children));
                } else {
                    item.put("prompt", node.prompt);
                    item.put("appliesTo", new JSONArray(node.appliesTo));
                    if (node.kind != null) {
                        item.put("kind", node.kind);
                    }
                }
            }
            out.put(item);
        }
        return out;
    }

    public static String encodeRaw(List<PromptNode> nodes) throws JSONException {
        return new JSONObject().put("items", rawItems(nodes)).toString();
    }

    public static PromptNode fork(PromptNode node, Supplier<String> ids) {
        PromptNode out = node.copy();
        out.forkedFrom = node.id;
        out.id = ids.get();
        out.origin = "custom";
        return out;
    }

    public static String newUserId() {
        String compact = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        return "p_" + compact.substring(0, 8);
    }

    public static final class MutationResult {
        public final List<PromptNode> items;
        public final boolean changed;
        public final PromptNode removed;

        private MutationResult(List<PromptNode> items, boolean changed, PromptNode removed) {
            this.items = items;
            this.changed = changed;
            this.removed = removed;
        }
    }

    public static MutationResult replace(List<PromptNode> source, String id, PromptNode replacement) {
        List<PromptNode> out = new ArrayList<>();
        boolean changed = false;
        for (PromptNode node : source) {
            if (same(node.id, id)) {
                out.add(replacement.copy());
                changed = true;
                continue;
            }
            PromptNode nodeCopy = node.copy();
            if (node.isGroup()) {
                MutationResult nested = replace(node.children, id, replacement);
                nodeCopy.children.clear();
                nodeCopy.children.addAll(nested.items);
                changed |= nested.changed;
            }
            out.add(nodeCopy);
        }
        return new MutationResult(out, changed, null);
    }

    public static MutationResult remove(List<PromptNode> source, String id) {
        List<PromptNode> out = new ArrayList<>();
        boolean changed = false;
        PromptNode removed = null;
        for (PromptNode node : source) {
            if (removed == null && same(node.id, id)) {
                changed = true;
                removed = node.copy();
                continue;
            }
            PromptNode nodeCopy = node.copy();
            if (removed == null && node.isGroup()) {
                MutationResult nested = remove(node.children, id);
                nodeCopy.children.clear();
                nodeCopy.children.addAll(nested.items);
                if (nested.changed) {
                    changed = true;
                    removed = nested.removed;
                }
            }
            out.add(nodeCopy);
        }
        return new MutationResult(out, changed, removed);
    }

    public static List<PromptNode> append(List<PromptNode> source, PromptNode node, String groupId) {
        List<PromptNode> out = copy(source);
        if (groupId == null) {
            out.add(node.copy());
            return out;
        }
        for (PromptNode candidate : out) {
            if (candidate.isGroup() && same(candidate.id, groupId) && !node.isGroup()) {
                candidate.children.add(node.copy());
                return out;
            }
        }
        return copy(source);
    }

    /** Moves an item to the top level when groupId is null, otherwise into that group's children. */
    public static List<PromptNode> move(List<PromptNode> source, String id, String groupId, int index) {
        if (same(id, groupId)) return null;
        MutationResult result = remove(source, id);
        if (!result.changed || result.removed == null) return copy(source);
        if (groupId != null && result.removed.isGroup()) return null;

        List<PromptNode> out = result.items;
        if (groupId == null) {
            out.add(clamp(index, out.size()), result.removed);
            return out;
        }
        for (PromptNode node : out) {
            if (node.isGroup() && same(node.id, groupId)) {
                node.children.add(clamp(index, node.children.size()), result.removed);
                return out;
            }
        }
        return null;
    }

    public static List<String> flattenIds(List<PromptNode> source) {
        List<String> out = new ArrayList<>();
        for (PromptNode node : source) {
            out.add(node.id);
            out.addAll(flattenIds(node.children));
        }
        return out;
    }

    public static String extractShareCode(String text) {
        Matcher matcher = SHARE_CODE.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : null;
    }

    public static String mergeCodeInput(String previous, String incoming) {
        String old = previous == null ? "" : previous;
        String next = incoming == null ? "" : incoming;
        if (old.equals(next)) return next;
        boolean typing = (next.length() == old.length() + 1 && next.startsWith(old))
                || (next.length() == old.length() - 1 && old.startsWith(next));
        if (!typing) {
            String code = extractShareCode(next);
            return code == null ? old : code;
        }
        String digits = next.replaceAll("[^0-9]", "");
        return digits.substring(0, Math.min(7, digits.length()));
    }

    public static UIConfigStore.MenuConfig menu(List<PromptNode> source, String anchor) {
        UIConfigStore.MenuConfig result = new UIConfigStore.MenuConfig();
        List<UIConfigStore.MenuNode> loose = new ArrayList<>();
        for (PromptNode node : source) {
            UIConfigStore.MenuNode converted = menuNode(node, anchor);
            if (converted == null) continue;
            if (node.isGroup()) {
                if (!loose.isEmpty()) {
                    result.groups.add(loose);
                    loose = new ArrayList<>();
                }
                List<UIConfigStore.MenuNode> section = new ArrayList<>();
                section.add(converted);
                result.groups.add(section);
            } else {
                loose.add(converted);
            }
        }
        if (!loose.isEmpty()) result.groups.add(loose);
        return result;
    }

    private static UIConfigStore.MenuNode menuNode(PromptNode node, String anchor) {
        if (node.isGroup()) {
            UIConfigStore.MenuNode group = new UIConfigStore.MenuNode(node.id, node.label, "submenu", "");
            for (PromptNode child : node.children) {
                UIConfigStore.MenuNode converted = menuNode(child, anchor);
                if (converted != null) group.children.add(converted);
            }
            return group.children.isEmpty() ? null : group;
        }
        if (!node.appliesTo.contains(anchor)) return null;
        return new UIConfigStore.MenuNode(node.id, node.label, "", node.prompt);
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(value, size));
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
