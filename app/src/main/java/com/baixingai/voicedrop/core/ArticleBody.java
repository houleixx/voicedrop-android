package com.baixingai.voicedrop.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArticleBody {
    private static final Pattern MARKER = Pattern.compile("\\[\\[photo:([^\\]]+)\\]\\]");
    private static final Pattern META_COMMENT =
            Pattern.compile("<!--\\s*([A-Za-z][\\w-]*)\\s*:\\s*(.*?)\\s*-->", Pattern.DOTALL);
    private static final Pattern ANY_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private ArticleBody() {}

    public static String resolvePhotoKey(String token, List<String> photos) {
        try {
            int index = Integer.parseInt(token) - 1;
            return index >= 0 && index < photos.size() ? photos.get(index) : null;
        } catch (NumberFormatException ignored) {
            return token;
        }
    }

    public static List<Segment> segments(String body) {
        String stripped = stripOriginComment(body);
        Matcher matcher = MARKER.matcher(stripped);
        List<Segment> out = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                String text = stripped.substring(cursor, matcher.start()).trim();
                if (!text.isEmpty()) {
                    out.add(Segment.text(text));
                }
            }
            out.add(Segment.photo(matcher.group(1)));
            cursor = matcher.end();
        }
        if (cursor < stripped.length()) {
            String text = stripped.substring(cursor).trim();
            if (!text.isEmpty()) {
                out.add(Segment.text(text));
            }
        }
        if (out.isEmpty()) {
            out.add(Segment.text(stripped));
        }
        return out;
    }

    public static String stripMarkers(String body) {
        String stripped = MARKER.matcher(stripOriginComment(body)).replaceAll("");
        while (stripped.contains("\n\n\n")) {
            stripped = stripped.replace("\n\n\n", "\n\n");
        }
        return stripped.trim();
    }

    public static String styleLabel(String body) {
        Matcher matcher = META_COMMENT.matcher(body);
        String label = null;
        while (matcher.find()) {
            if ("style".equals(matcher.group(1))) {
                String value = matcher.group(2).trim();
                if (!value.isEmpty()) {
                    label = value;
                }
            }
        }
        return label;
    }

    public static String styleLabelForVersion(int version) {
        return "风格 v" + version;
    }

    public static Integer styleVersion(String body) {
        String label = styleLabel(body);
        if (label == null) return null;
        Matcher matcher = Pattern.compile("\\d+").matcher(label);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }

    public static String stripOriginComment(String body) {
        return ANY_COMMENT.matcher(body).replaceAll("").trim();
    }

    public static String firstPhotoKey(String body, List<String> photos) {
        for (Segment segment : segments(body)) {
            if (segment.type == Segment.Type.PHOTO) {
                String key = resolvePhotoKey(segment.value, photos);
                if (key != null) return key;
            }
        }
        return null;
    }

    /**
     * Replaces one visible body row without touching any surrounding whitespace,
     * metadata comments, or photo markers. Row numbers match the detail screen:
     * each non-blank text line and each photo marker consumes one row.
     */
    public static String replacingLine(int targetLine, String newText, String body) {
        if (body == null || targetLine < 1 || newText == null) return body;
        Matcher photos = MARKER.matcher(body);
        int cursor = 0;
        int line = 0;
        while (photos.find()) {
            TextLineMatch textMatch = findTextLine(body, cursor, photos.start(), targetLine, line);
            if (textMatch.rangeStart >= 0) {
                return body.substring(0, textMatch.rangeStart) + newText + body.substring(textMatch.rangeEnd);
            }
            line = textMatch.lineCount;
            line++;
            if (line == targetLine) return body; // Images are not editable text rows.
            cursor = photos.end();
        }
        TextLineMatch tail = findTextLine(body, cursor, body.length(), targetLine, line);
        if (tail.rangeStart < 0) return body;
        return body.substring(0, tail.rangeStart) + newText + body.substring(tail.rangeEnd);
    }

    /**
     * Maps a row rendered from a trimmed/duplicate-title-free body back into the
     * original stored body, then performs the exact line replacement there.
     */
    public static String replacingRenderedLine(int targetLine, String newText,
                                               String renderedBody, String originalBody) {
        if (renderedBody == null || originalBody == null) return originalBody;
        String replaced = replacingLine(targetLine, newText, renderedBody);
        if (replaced == null || replaced.equals(renderedBody)) return originalBody;
        int start = originalBody.indexOf(renderedBody);
        if (start < 0) return originalBody;
        return originalBody.substring(0, start) + replaced
                + originalBody.substring(start + renderedBody.length());
    }

    private static TextLineMatch findTextLine(String body, int start, int end, int targetLine, int initialLine) {
        int line = initialLine;
        int lineStart = start;
        while (lineStart <= end) {
            int newline = body.indexOf('\n', lineStart);
            int lineEnd = newline >= 0 && newline < end ? newline : end;
            String raw = body.substring(lineStart, lineEnd);
            String visible = ANY_COMMENT.matcher(raw).replaceAll("");
            if (!visible.trim().isEmpty()) {
                line++;
                if (line == targetLine) {
                    // Metadata comments are expected on their own lines. If one is
                    // embedded beside visible text, do not risk rewriting it.
                    if (!visible.equals(raw)) return new TextLineMatch(-1, -1, line);
                    int leading = 0;
                    while (leading < raw.length() && Character.isWhitespace(raw.charAt(leading))) leading++;
                    int trailing = raw.length();
                    while (trailing > leading && Character.isWhitespace(raw.charAt(trailing - 1))) trailing--;
                    return new TextLineMatch(lineStart + leading, lineStart + trailing, line);
                }
            }
            if (lineEnd >= end) break;
            lineStart = lineEnd + 1;
        }
        return new TextLineMatch(-1, -1, line);
    }

    private static final class TextLineMatch {
        final int rangeStart;
        final int rangeEnd;
        final int lineCount;

        TextLineMatch(int rangeStart, int rangeEnd, int lineCount) {
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
            this.lineCount = lineCount;
        }
    }

    public static String shareText(List<Article> articles) {
        boolean multi = articles.size() > 1;
        List<String> parts = new ArrayList<>();
        for (Article article : articles) {
            String body = stripMarkers(article.body);
            parts.add(multi
                    ? "【" + article.title + "】\n\n" + body
                    : article.title + "\n\n" + body);
        }
        return String.join("\n\n---\n\n", parts);
    }

    public static final class Segment {
        public enum Type { TEXT, PHOTO }

        public final Type type;
        public final String value;

        private Segment(Type type, String value) {
            this.type = type;
            this.value = value;
        }

        public static Segment text(String value) {
            return new Segment(Type.TEXT, value);
        }

        public static Segment photo(String value) {
            return new Segment(Type.PHOTO, value);
        }
    }

    public static final class Article {
        public final String title;
        public final String body;

        public Article(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }
}
