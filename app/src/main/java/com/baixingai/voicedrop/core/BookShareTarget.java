package com.baixingai.voicedrop.core;

import java.net.URI;

/** Validates the WebView location before it is exposed through a book share. */
public final class BookShareTarget {
    private BookShareTarget() {}

    public static Target resolve(String rootUrl, String currentUrl, String pageTitle,
                                 String bookTitle, String author) {
        String fallbackTitle = "《" + safe(bookTitle, "未命名") + "》"
                + (blank(author) ? "" : " — " + author.trim());
        try {
            URI root = new URI(rootUrl).normalize();
            URI current = new URI(currentUrl == null ? "" : currentUrl).normalize();
            String rootPath = root.getPath();
            String currentPath = current.getPath();
            if (root.getScheme() == null || root.getHost() == null || rootPath == null
                    || !root.getScheme().equalsIgnoreCase(current.getScheme())
                    || !root.getHost().equalsIgnoreCase(current.getHost())
                    || currentPath == null || !currentPath.startsWith(rootPath)) {
                return new Target(rootUrl, fallbackTitle, false);
            }
            boolean chapter = !currentPath.equals(rootPath);
            return new Target(current.toString(), chapter && !blank(pageTitle) ? pageTitle.trim() : fallbackTitle,
                    chapter);
        } catch (Exception ignored) {
            return new Target(rootUrl, fallbackTitle, false);
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String safe(String value, String fallback) { return blank(value) ? fallback : value.trim(); }

    public static final class Target {
        public final String url, title;
        public final boolean chapter;
        Target(String url, String title, boolean chapter) {
            this.url = url; this.title = title; this.chapter = chapter;
        }
    }
}
