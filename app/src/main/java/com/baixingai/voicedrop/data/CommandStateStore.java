package com.baixingai.voicedrop.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CommandStateStore {
    private static final String PREFS = "voicedrop.commandstate";
    private static final String KEY_CONTROLS = "controls";
    private static final String KEY_CONFIRMATIONS = "confirmations";

    private CommandStateStore() {
    }

    public static List<Control> loadControls(Context context) {
        return decodeControls(preferences(context).getString(KEY_CONTROLS, ""));
    }

    public static void saveControls(Context context, List<Control> controls) {
        save(preferences(context), KEY_CONTROLS, encodeControls(controls));
    }

    public static List<Confirmation> loadConfirmations(Context context) {
        return decodeConfirmations(preferences(context).getString(KEY_CONFIRMATIONS, ""));
    }

    public static void saveConfirmations(Context context, List<Confirmation> confirmations) {
        save(preferences(context), KEY_CONFIRMATIONS, encodeConfirmations(confirmations));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void save(SharedPreferences preferences, String key, String value) {
        SharedPreferences.Editor editor = preferences.edit();
        if (value.isEmpty() || "[]".equals(value)) editor.remove(key);
        else editor.putString(key, value);
        editor.apply();
    }

    public static String encodeControls(List<Control> controls) {
        JSONArray array = new JSONArray();
        if (controls != null) {
            for (Control control : controls) {
                if (control == null || !validControlType(control.type) || empty(control.id)) continue;
                try {
                    array.put(new JSONObject().put("type", control.type).put("id", control.id));
                } catch (Exception ignored) {
                }
            }
        }
        return array.toString();
    }

    public static List<Control> decodeControls(String raw) {
        List<Control> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw == null || raw.isEmpty() ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String type = object.optString("type", "");
                String id = object.optString("id", "");
                if (validControlType(type) && !empty(id)) out.add(new Control(type, id));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String encodeConfirmations(List<Confirmation> confirmations) {
        JSONArray array = new JSONArray();
        if (confirmations != null) {
            for (Confirmation confirmation : confirmations) {
                if (confirmation == null || empty(confirmation.id)) continue;
                try {
                    array.put(new JSONObject().put("id", confirmation.id).put("text", confirmation.text));
                } catch (Exception ignored) {
                }
            }
        }
        return array.toString();
    }

    public static List<Confirmation> decodeConfirmations(String raw) {
        List<Confirmation> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw == null || raw.isEmpty() ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String id = object.optString("id", "");
                if (empty(id)) continue;
                out.add(new Confirmation(id, object.optString("text", "确认执行这条指令？")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static boolean validControlType(String type) {
        return "confirm".equals(type) || "cancel".equals(type);
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }

    public static final class Control {
        public final String type;
        public final String id;

        public Control(String type, String id) {
            this.type = type;
            this.id = id;
        }
    }

    public static final class Confirmation {
        public final String id;
        public final String text;

        public Confirmation(String id, String text) {
            this.id = id;
            this.text = text == null || text.isEmpty() ? "确认执行这条指令？" : text;
        }
    }
}
