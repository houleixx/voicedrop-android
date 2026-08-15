package com.baixingai.voicedrop.ui;

/** Pure presentation rules for the versioned writing-style screen. */
public final class WritingStylePresentation {
    public enum Action {
        CURRENT_DEFAULT,
        SET_DEFAULT,
        SAVE_AS_NEW_DEFAULT
    }

    private WritingStylePresentation() {}

    public static Action action(boolean existingVersion, boolean currentDefault,
                                String original, String edited) {
        String before = clean(original);
        String after = clean(edited);
        if (!existingVersion || !after.equals(before)) return Action.SAVE_AS_NEW_DEFAULT;
        return currentDefault ? Action.CURRENT_DEFAULT : Action.SET_DEFAULT;
    }

    public static String actionLabel(Action action) {
        switch (action) {
            case CURRENT_DEFAULT:
                return "当前默认";
            case SET_DEFAULT:
                return "设为默认";
            case SAVE_AS_NEW_DEFAULT:
            default:
                return "保存为新版本并设为默认";
        }
    }

    public static String displayName(String style) {
        String clean = clean(style);
        if (clean.isEmpty()) return "未命名风格";
        int newline = clean.indexOf('\n');
        String first = newline < 0 ? clean : clean.substring(0, newline).trim();
        return first.length() > 18 ? first.substring(0, 18) + "…" : first;
    }

    public static String preview(String style) {
        String clean = clean(style).replace('\n', ' ');
        return clean.length() > 42 ? clean.substring(0, 42) + "…" : clean;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
