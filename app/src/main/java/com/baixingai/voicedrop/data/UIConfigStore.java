package com.baixingai.voicedrop.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rendering types and placeholder helpers for the native long-press menu. */
public final class UIConfigStore {
    private UIConfigStore() {}

    public static String fill(String instruction, String key, String value) {
        return instruction == null ? "" : instruction.replace("{{" + key + "}}", value == null ? "" : value);
    }

    public static String fill(String instruction, String key1, String value1, String key2, String value2) {
        return fill(fill(instruction, key1, value1), key2, value2);
    }

    public static String quotePrefix(String text) {
        if (text == null) return "";
        String trimmed = text.trim().replace('"', '\'');
        return trimmed.length() <= 15 ? trimmed : trimmed.substring(0, 15);
    }

    /** Legacy fixture decoder; production prompt data is loaded by PromptStore. */
    public static UIConfigDoc parseDoc(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        UIConfigDoc doc = new UIConfigDoc(root.optInt("schema", 1));
        JSONObject pages = root.optJSONObject("pages");
        if (pages == null || pages.names() == null) return doc;
        JSONArray names = pages.names();
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i);
            JSONObject page = pages.optJSONObject(name);
            JSONObject longpress = page == null ? null : page.optJSONObject("longpress");
            doc.pages.put(name, new PageConfig(longpress == null ? null : new LongpressConfig(
                    parseMenu(longpress.optJSONObject("image")), parseMenu(longpress.optJSONObject("text")))));
        }
        return doc;
    }

    private static MenuConfig parseMenu(JSONObject obj) {
        if (obj == null) return null;
        MenuConfig menu = new MenuConfig();
        JSONArray groups = obj.optJSONArray("groups");
        if (groups == null) return menu;
        for (int i = 0; i < groups.length(); i++) {
            JSONArray source = groups.optJSONArray(i);
            if (source == null) continue;
            List<MenuNode> section = new ArrayList<>();
            for (int j = 0; j < source.length(); j++) {
                MenuNode node = parseNode(source.optJSONObject(j));
                if (node != null) section.add(node);
            }
            if (!section.isEmpty()) menu.groups.add(section);
        }
        return menu;
    }

    private static MenuNode parseNode(JSONObject obj) {
        if (obj == null) return null;
        MenuNode node = new MenuNode(obj.optString("id"), obj.optString("label"), obj.optString("type"),
                obj.optString("instruction"), obj.optString("origin"));
        JSONArray children = obj.optJSONArray("children");
        if (children != null) for (int i = 0; i < children.length(); i++) {
            MenuNode child = parseNode(children.optJSONObject(i));
            if (child != null) node.children.add(child);
        }
        return node;
    }

    public static final class UIConfigDoc {
        public final int schema;
        public final Map<String, PageConfig> pages = new HashMap<>();
        UIConfigDoc(int schema) { this.schema = schema; }
    }

    public static final class PageConfig {
        public final LongpressConfig longpress;
        PageConfig(LongpressConfig longpress) { this.longpress = longpress; }
    }

    public static final class LongpressConfig {
        public final MenuConfig image;
        public final MenuConfig text;
        LongpressConfig(MenuConfig image, MenuConfig text) { this.image = image; this.text = text; }
    }

    public static final class MenuConfig {
        public final List<List<MenuNode>> groups = new ArrayList<>();
    }

    public static final class MenuNode {
        public final String id;
        public final String label;
        public final String type;
        public final String instruction;
        public final String origin;
        public final List<MenuNode> children = new ArrayList<>();
        public MenuNode(String id, String label, String type, String instruction) {
            this(id, label, type, instruction, id != null && id.startsWith("sys_") ? "system" : "user");
        }
        public MenuNode(String id, String label, String type, String instruction, String origin) {
            this.id = id == null ? "" : id;
            this.label = label == null ? "" : label;
            this.type = type == null ? "" : type;
            this.instruction = instruction == null ? "" : instruction;
            this.origin = origin == null || origin.isEmpty()
                    ? (this.id.startsWith("sys_") ? "system" : "user") : origin;
        }
    }
}
