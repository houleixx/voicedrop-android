package com.baixingai.voicedrop.core;

/** Builds the bounded server seed used when an existing article is expanded into a book. */
public final class BookWritingSeed {
    public static final int MAX_CHARS = 20_000;

    private BookWritingSeed() {}

    public static String fromIdea(String idea) {
        return trim(idea);
    }

    public static String fromArticle(String requirement, String title, String body) {
        String ask = trim(requirement);
        String articleTitle = trim(title);
        if (articleTitle.isEmpty()) articleTitle = "无题";
        String articleBody = body == null ? "" : body.trim();
        String prefix = ask.isEmpty() ? "" : "写书要求：" + ask + "\n\n";
        String value = prefix + "以下这篇文章是种子素材，把它扩展成一本完整的书：\n\n《"
                + articleTitle + "》\n\n" + articleBody;
        return limit(value);
    }

    public static boolean canSubmit(String requirement, boolean hasArticle) {
        return hasArticle || !trim(requirement).isEmpty();
    }

    static String limit(String value) {
        return value.length() <= MAX_CHARS ? value : value.substring(0, MAX_CHARS);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
