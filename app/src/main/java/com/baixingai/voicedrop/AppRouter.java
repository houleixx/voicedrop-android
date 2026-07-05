package com.baixingai.voicedrop;

import android.net.Uri;

import java.net.URLDecoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class AppRouter {
    private AppRouter() {
    }

    public static DeepLink parse(Uri uri) {
        if (uri == null) return DeepLink.none();
        String route = uri.getHost() == null ? "" : uri.getHost();
        List<String> segments = uri.getPathSegments();
        String first = segments.isEmpty() ? "" : segments.get(0);
        String tag = uri.getQueryParameter("tag");
        return parseParts(uri.getScheme(), route, first, tag);
    }

    public static DeepLink parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return DeepLink.none();
        try {
            URI uri = new URI(raw);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String first = "";
            if (path.startsWith("/") && path.length() > 1) {
                int next = path.indexOf('/', 1);
                first = next < 0 ? path.substring(1) : path.substring(1, next);
            }
            String query = uri.getRawQuery();
            String tag = null;
            if (query != null) {
                for (String part : query.split("&")) {
                    int eq = part.indexOf('=');
                    String name = eq < 0 ? part : part.substring(0, eq);
                    if ("tag".equals(name)) {
                        tag = decode(eq < 0 ? "" : part.substring(eq + 1));
                        break;
                    }
                }
            }
            return parseParts(uri.getScheme(), uri.getHost() == null ? "" : uri.getHost(), first, tag);
        } catch (Exception e) {
            return DeepLink.none();
        }
    }

    private static DeepLink parseParts(String scheme, String route, String firstSegment, String tag) {
        if (!"voicedrop".equals(scheme)) return DeepLink.none();
        if ("recordings".equals(route) || route.isEmpty()) return new DeepLink(Kind.RECORDINGS, "", "");
        if ("community".equals(route)) return new DeepLink(Kind.COMMUNITY, "", "");
        if ("settings".equals(route)) return new DeepLink(Kind.SETTINGS, "", "");
        if ("record".equals(route)) return new DeepLink(Kind.RECORD, "", tag == null ? "" : tag.trim());
        if ("article".equals(route) && firstSegment != null && !firstSegment.isEmpty()) {
            return new DeepLink(Kind.ARTICLE, decode(firstSegment), "");
        }
        return DeepLink.none();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    public enum Kind {
        NONE,
        RECORDINGS,
        COMMUNITY,
        SETTINGS,
        RECORD,
        ARTICLE
    }

    public static final class DeepLink {
        public final Kind kind;
        public final String stem;
        public final String tag;

        DeepLink(Kind kind, String stem, String tag) {
            this.kind = kind;
            this.stem = stem == null ? "" : stem;
            this.tag = tag == null ? "" : tag;
        }

        static DeepLink none() {
            return new DeepLink(Kind.NONE, "", "");
        }
    }
}
