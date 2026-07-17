package com.baixingai.voicedrop;

import android.net.Uri;

import java.net.URLDecoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

public final class AppRouter {
    private AppRouter() {
    }

    public static DeepLink parse(Uri uri) {
        if (uri == null) return DeepLink.none();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if ("https".equals(scheme) || "http".equals(scheme)) {
            return parseUniversal(uri.getHost(), uri.getPathSegments(), uri.toString());
        }
        String route = uri.getHost() == null ? "" : uri.getHost();
        List<String> segments = uri.getPathSegments();
        String first = segments.isEmpty() ? "" : segments.get(0);
        String tag = uri.getQueryParameter("tag");
        String code = uri.getQueryParameter("code");
        return parseParts(uri.getScheme(), route, first, tag, code);
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
            String code = null;
            if (query != null) {
                for (String part : query.split("&")) {
                    int eq = part.indexOf('=');
                    String name = eq < 0 ? part : part.substring(0, eq);
                    if ("tag".equals(name)) {
                        tag = decode(eq < 0 ? "" : part.substring(eq + 1));
                    } else if ("code".equals(name)) {
                        code = decode(eq < 0 ? "" : part.substring(eq + 1));
                    }
                }
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if ("https".equals(scheme) || "http".equals(scheme)) {
                return parseUniversal(uri.getHost(), pathSegments(path), raw);
            }
            return parseParts(uri.getScheme(), uri.getHost() == null ? "" : uri.getHost(), first, tag, code);
        } catch (Exception e) {
            return DeepLink.none();
        }
    }

    private static List<String> pathSegments(String path) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (path == null || path.isEmpty()) return out;
        for (String part : path.split("/")) {
            if (!part.isEmpty()) out.add(decode(part));
        }
        return out;
    }

    private static DeepLink parseUniversal(String host, List<String> pathSegments, String rawUrl) {
        if (host == null) return DeepLink.none();
        String h = host.toLowerCase();
        java.util.ArrayList<String> segs = new java.util.ArrayList<>(pathSegments == null
                ? java.util.Collections.emptyList() : pathSegments);
        if ("voicedrop.cn".equals(h) || "www.voicedrop.cn".equals(h)) {
            // Entire public VoiceDrop site opens in-app; routable single-token paths
            // become share links, everything else falls back to a web page.
        } else if ("jianshuo.dev".equals(h) || "www.jianshuo.dev".equals(h)) {
            if (segs.isEmpty() || !"voicedrop".equals(segs.get(0))) return DeepLink.none();
            segs.remove(0);
        } else {
            return DeepLink.none();
        }
        if (segs.isEmpty()) return new DeepLink(Kind.RECORDINGS, "", "", "", rawUrl);
        String first = segs.get(0);
        if (segs.size() == 1 && PROMPT_CODE.matcher(first).matches()) {
            return new DeepLink(Kind.PROMPT_IMPORT, "", "", "", rawUrl, first);
        }
        if (segs.size() == 2 && "i".equals(first) && INVITE_CODE.matcher(segs.get(1)).matches()) {
            return new DeepLink(Kind.INVITE, "", "", segs.get(1), rawUrl);
        }
        if (segs.size() == 1 && SHARE_ID.matcher(first).matches() && !isStaticPath(first)) {
            return new DeepLink(Kind.SHARE_LINK, "", "", first, rawUrl);
        }
        return new DeepLink(Kind.WEB, "", "", "", rawUrl);
    }

    private static final Pattern SHARE_ID = Pattern.compile("^[A-Za-z0-9_-]{6,16}$");
    private static final Pattern PROMPT_CODE = Pattern.compile("^[1-9][0-9]{6}$");
    private static final Pattern INVITE_CODE = Pattern.compile("^[A-Za-z0-9]{6,16}$");

    private static boolean isStaticPath(String path) {
        return "privacy".equals(path) || "welcome".equals(path) || "help".equals(path);
    }

    private static DeepLink parseParts(String scheme, String route, String firstSegment, String tag, String code) {
        if (!"voicedrop".equals(scheme)) return DeepLink.none();
        if ("recordings".equals(route) || route.isEmpty()) return new DeepLink(Kind.RECORDINGS, "", "");
        if ("community".equals(route)) return new DeepLink(Kind.COMMUNITY, "", "");
        if ("settings".equals(route)) return new DeepLink(Kind.SETTINGS, "", "");
        if ("record".equals(route)) return new DeepLink(Kind.RECORD, "", tag == null ? "" : tag.trim());
        if ("article".equals(route) && firstSegment != null && !firstSegment.isEmpty()) {
            return new DeepLink(Kind.ARTICLE, decode(firstSegment), "");
        }
        if ("prompt-import".equals(route) && code != null && PROMPT_CODE.matcher(code).matches()) {
            return new DeepLink(Kind.PROMPT_IMPORT, "", "", "", "", code);
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
        ARTICLE,
        PROMPT_IMPORT,
        INVITE,
        SHARE_LINK,
        WEB
    }

    public static final class DeepLink {
        public final Kind kind;
        public final String stem;
        public final String tag;
        public final String id;
        public final String url;
        public final String shareCode;

        DeepLink(Kind kind, String stem, String tag) {
            this(kind, stem, tag, "", "", "");
        }

        DeepLink(Kind kind, String stem, String tag, String id, String url) {
            this(kind, stem, tag, id, url, "");
        }

        DeepLink(Kind kind, String stem, String tag, String id, String url, String shareCode) {
            this.kind = kind;
            this.stem = stem == null ? "" : stem;
            this.tag = tag == null ? "" : tag;
            this.id = id == null ? "" : id;
            this.url = url == null ? "" : url;
            this.shareCode = shareCode == null ? "" : shareCode;
        }

        static DeepLink none() {
            return new DeepLink(Kind.NONE, "", "");
        }
    }
}
