package com.baixingai.voicedrop.core;

/** Display-only block Markdown classification. Stored article text remains unchanged. */
public final class MarkdownBlock {
    public enum Kind { H1, H2, H3, BULLET, ORDERED, QUOTE, DIVIDER, PLAIN }

    public final Kind kind;
    public final String content;
    public final String marker;

    private MarkdownBlock(Kind kind, String content, String marker) {
        this.kind = kind;
        this.content = content;
        this.marker = marker;
    }

    public static MarkdownBlock classify(String value) {
        String line = value == null ? "" : value.trim();
        if (line.startsWith("#")) {
            int hashes = 0;
            while (hashes < line.length() && line.charAt(hashes) == '#') hashes++;
            if (hashes <= 6 && hashes < line.length() && isHorizontalSpace(line.charAt(hashes))) {
                Kind kind = hashes == 1 ? Kind.H1 : hashes == 2 ? Kind.H2 : Kind.H3;
                return block(kind, line.substring(hashes).trim());
            }
            return block(Kind.PLAIN, line);
        }

        String solid = line.replace(" ", "");
        if (solid.length() >= 3 && isDividerSymbol(solid.charAt(0))) {
            char symbol = solid.charAt(0);
            boolean same = true;
            for (int i = 1; i < solid.length(); i++) {
                if (solid.charAt(i) != symbol) { same = false; break; }
            }
            if (same) return block(Kind.DIVIDER, "");
        }

        if (line.length() >= 2 && isBullet(line.charAt(0)) && isHorizontalSpace(line.charAt(1))) {
            return block(Kind.BULLET, line.substring(1).trim());
        }

        int digits = 0;
        while (digits < line.length() && Character.isDigit(line.charAt(digits))) digits++;
        if (digits > 0 && digits <= 3 && digits < line.length() && isOrderedSeparator(line.charAt(digits))) {
            char separator = line.charAt(digits);
            int contentStart = digits + 1;
            if (contentStart < line.length()
                    && (isHorizontalSpace(line.charAt(contentStart)) || separator == '、')) {
                return new MarkdownBlock(Kind.ORDERED, line.substring(contentStart).trim(),
                        line.substring(0, digits));
            }
        }

        if (line.startsWith(">")) {
            int cursor = 0;
            while (cursor < line.length() && line.charAt(cursor) == '>') {
                cursor++;
                while (cursor < line.length() && line.charAt(cursor) == ' ') cursor++;
            }
            return block(Kind.QUOTE, line.substring(cursor).trim());
        }
        return block(Kind.PLAIN, line);
    }

    private static MarkdownBlock block(Kind kind, String content) {
        return new MarkdownBlock(kind, content, "");
    }

    private static boolean isHorizontalSpace(char value) { return value == ' ' || value == '\t'; }
    private static boolean isDividerSymbol(char value) { return value == '-' || value == '*' || value == '_'; }
    private static boolean isBullet(char value) { return value == '-' || value == '*' || value == '+'; }
    private static boolean isOrderedSeparator(char value) { return value == '.' || value == ')' || value == '、'; }
}
