package com.baixingai.voicedrop.net;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentMessage {
    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern STEM = Pattern.compile("\"stem\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAIRING_ID = Pattern.compile("\"pairingId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PUBKEY = Pattern.compile("\"pubkey\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DOC = Pattern.compile("\"doc\"\\s*:\\s*(\\{.*\\})\\s*\\}?\\s*$", Pattern.DOTALL);
    private static final Pattern ARTICLE = Pattern.compile("\"article\"\\s*:\\s*(\\{.*\\})\\s*\\}?\\s*$", Pattern.DOTALL);

    private AgentMessage() {}

    public static Status status(String text) {
        if (!"status_update".equals(field(TYPE, text))) return null;
        String stem = field(STEM, text);
        String status = field(STATUS, text);
        if (stem.isEmpty() || status.isEmpty()) return null;
        return new Status(stem, status);
    }

    public static Update update(String text) {
        if (!"updated".equals(field(TYPE, text))) return null;
        String doc = object(DOC, text);
        if (doc.isEmpty()) doc = object(ARTICLE, text);
        if (doc.isEmpty()) return null;
        return new Update(doc);
    }

    public static LinkRequest linkRequest(String text) {
        if (!"link_request".equals(field(TYPE, text))) return null;
        String pairingId = field(PAIRING_ID, text);
        String code = field(CODE, text);
        String pubkey = field(PUBKEY, text);
        if (pairingId.isEmpty() || code.isEmpty() || pubkey.isEmpty()) return null;
        return new LinkRequest(pairingId, code, pubkey);
    }

    public static LinkRelease linkRelease(String text) {
        if (!"link_release".equals(field(TYPE, text))) return null;
        String pairingId = field(PAIRING_ID, text);
        if (pairingId.isEmpty()) return null;
        return new LinkRelease(pairingId);
    }

    private static String field(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String object(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static final class Status {
        public final String stem;
        public final String status;

        Status(String stem, String status) {
            this.stem = stem;
            this.status = status;
        }
    }

    public static final class Update {
        public final String docJson;

        Update(String docJson) {
            this.docJson = docJson;
        }
    }

    public static final class LinkRequest {
        public final String pairingId;
        public final String code;
        public final String pubkey;

        LinkRequest(String pairingId, String code, String pubkey) {
            this.pairingId = pairingId;
            this.code = code;
            this.pubkey = pubkey;
        }
    }

    public static final class LinkRelease {
        public final String pairingId;

        LinkRelease(String pairingId) {
            this.pairingId = pairingId;
        }
    }
}
