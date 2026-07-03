package com.baixingai.voicedrop.update;

import java.util.ArrayList;
import java.util.List;

public final class AppVersion {
    private AppVersion() {}

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    public static int compare(String left, String right) {
        ParsedVersion a = parse(left);
        ParsedVersion b = parse(right);
        int max = Math.max(a.numbers.size(), b.numbers.size());
        for (int i = 0; i < max; i++) {
            int av = i < a.numbers.size() ? a.numbers.get(i) : 0;
            int bv = i < b.numbers.size() ? b.numbers.get(i) : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        if (a.preview != b.preview) return a.preview ? -1 : 1;
        return 0;
    }

    private static ParsedVersion parse(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);

        int suffix = value.length();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isDigit(c) || c == '.')) {
                suffix = i;
                break;
            }
        }
        String core = value.substring(0, suffix);
        String tail = suffix < value.length() ? value.substring(suffix).toLowerCase() : "";
        boolean preview = tail.contains("alpha") || tail.contains("beta")
                || tail.contains("rc") || tail.contains("ci") || tail.contains("test");

        List<Integer> numbers = new ArrayList<>();
        for (String part : core.split("\\.")) {
            if (part.isEmpty()) {
                numbers.add(0);
                continue;
            }
            try {
                numbers.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                numbers.add(0);
            }
        }
        return new ParsedVersion(numbers, preview);
    }

    private static final class ParsedVersion {
        final List<Integer> numbers;
        final boolean preview;

        ParsedVersion(List<Integer> numbers, boolean preview) {
            this.numbers = numbers;
            this.preview = preview;
        }
    }
}
