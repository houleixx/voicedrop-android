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
