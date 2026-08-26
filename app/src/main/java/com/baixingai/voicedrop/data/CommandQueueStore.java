package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.baixingai.voicedrop.net.LibraryCommandSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CommandQueueStore {
    private static final String PREFS = "voicedrop.commandqueue";
    private static final String KEY_DEFAULT = "default";

    private CommandQueueStore() {
    }

    public static List<LibraryCommandSession.CommandRequest> load(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_DEFAULT, "");
        return parse(raw);
    }

    public static List<LibraryCommandSession.CommandRequest> parse(String raw) {
        List<LibraryCommandSession.CommandRequest> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String id = obj.optString("id", "");
                String text = obj.optString("text", "");
                if (!id.isEmpty() && !text.trim().isEmpty()) {
                    List<LibraryCommandSession.CommandRef> refs = new ArrayList<>();
                    JSONArray refsJson = obj.optJSONArray("refs");
                    if (refsJson != null) for (int j = 0; j < refsJson.length(); j++) {
                        JSONObject ref = refsJson.optJSONObject(j);
                        if (ref == null) continue;
                        int n = ref.optInt("n", 0);
                        String stem = ref.optString("stem", "");
                        if (n > 0 && !stem.isEmpty()) {
                            refs.add(new LibraryCommandSession.CommandRef(n, stem, ref.optString("title", "")));
                        }
                    }
                    out.add(new LibraryCommandSession.CommandRequest(id, text, refs));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context context, List<LibraryCommandSession.CommandRequest> queue) {
        String raw = serialize(queue);
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (raw.isEmpty()) editor.remove(KEY_DEFAULT);
        else editor.putString(KEY_DEFAULT, raw);
        editor.apply();
    }

    public static String serialize(List<LibraryCommandSession.CommandRequest> queue) {
        JSONArray arr = new JSONArray();
        if (queue == null) return "";
        for (LibraryCommandSession.CommandRequest request : queue) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", request.id);
                obj.put("text", request.text);
                JSONArray refs = new JSONArray();
                for (LibraryCommandSession.CommandRef ref : request.refs) {
                    refs.put(new JSONObject()
                            .put("n", ref.n)
                            .put("stem", ref.stem)
                            .put("title", ref.title));
                }
                obj.put("refs", refs);
                arr.put(obj);
            } catch (Exception ignored) {
            }
        }
        return arr.length() == 0 ? "" : arr.toString();
    }
}
