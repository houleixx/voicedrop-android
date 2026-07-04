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
        List<LibraryCommandSession.CommandRequest> out = new ArrayList<>();
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_DEFAULT, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String id = obj.optString("id", "");
                String text = obj.optString("text", "");
                if (!id.isEmpty() && !text.trim().isEmpty()) {
                    out.add(new LibraryCommandSession.CommandRequest(id, text));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context context, List<LibraryCommandSession.CommandRequest> queue) {
        JSONArray arr = new JSONArray();
        for (LibraryCommandSession.CommandRequest request : queue) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", request.id);
                obj.put("text", request.text);
                arr.put(obj);
            } catch (Exception ignored) {
            }
        }
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (arr.length() == 0) editor.remove(KEY_DEFAULT);
        else editor.putString(KEY_DEFAULT, arr.toString());
        editor.apply();
    }
}
