package com.baixingai.voicedrop.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class PromptNode {
    public String id;
    public String type;
    public String label;
    public String origin;
    public String prompt;
    public String kind;
    public String forkedFrom;
    public String importedFrom;
    public final List<String> appliesTo = new ArrayList<>();
    public final List<PromptNode> children = new ArrayList<>();

    public boolean isGroup() {
        return "group".equals(type);
    }

    public boolean isSystem() {
        return "system".equals(origin);
    }

    public PromptNode copy() {
        PromptNode out = new PromptNode();
        out.id = id;
        out.type = type;
        out.label = label;
        out.origin = origin;
        out.prompt = prompt;
        out.kind = kind;
        out.forkedFrom = forkedFrom;
        out.importedFrom = importedFrom;
        out.appliesTo.addAll(appliesTo);
        for (PromptNode child : children) {
            out.children.add(child.copy());
        }
        return out;
    }

    public static PromptNode fromResolved(JSONObject json) throws JSONException {
        PromptNode out = new PromptNode();
        out.id = json.getString("id");
        out.type = json.getString("type");
        out.label = json.getString("label");
        out.origin = json.getString("origin");
        out.prompt = nullableString(json, "prompt");
        out.kind = nullableString(json, "kind");
        out.forkedFrom = nullableString(json, "forkedFrom");
        out.importedFrom = nullableString(json, "importedFrom");

        JSONArray applies = json.optJSONArray("appliesTo");
        if (applies != null) {
            for (int i = 0; i < applies.length(); i++) {
                out.appliesTo.add(applies.getString(i));
            }
        }
        JSONArray children = json.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                out.children.add(fromResolved(children.getJSONObject(i)));
            }
        }
        return out;
    }

    private static String nullableString(JSONObject json, String key) {
        return !json.has(key) || json.isNull(key) ? null : json.optString(key, null);
    }
}
